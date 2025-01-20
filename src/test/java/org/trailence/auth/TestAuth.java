package org.trailence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.trailence.auth.db.UserKeyRepository;
import org.trailence.auth.dto.AuthResponse;
import org.trailence.auth.dto.InitRenewRequest;
import org.trailence.auth.dto.InitRenewResponse;
import org.trailence.auth.dto.LoginRequest;
import org.trailence.auth.dto.RenewTokenRequest;
import org.trailence.auth.dto.UserKey;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.test.stubs.CaptchaStub;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.test.StepVerifier;

class TestAuth extends AbstractTest {
	
	@Autowired
	private UserKeyRepository keyRepo;
	@Autowired
	private AuthService service;

	@Test
	void testLogin() {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		assertThat(auth.getAccessToken()).isNotNull();
		assertThat(auth.getEmail()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(auth.getPreferences()).isNotNull();
	}
	
	@Test
	void testLoginRenewTokenDeleteKey() throws Exception {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
				.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		assertThat(response.statusCode()).isEqualTo(200);
		var initRenew = response.getBody().as(InitRenewResponse.class);
		assertThat(initRenew.getRandom()).isNotNull();
		
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		var signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(200);
		var authRenew = response.getBody().as(AuthResponse.class);
		assertThat(authRenew.getAccessToken()).isNotNull();
		assertThat(authRenew.getEmail()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(authRenew.getPreferences()).isNotNull();
		assertThat(authRenew.getKeyId()).isEqualTo(auth.getKeyId());
		
		response = RestAssured.given()
			.header("Authorization", "Bearer " + authRenew.getAccessToken())
			.get("/api/auth/v1/mykeys");
		assertThat(response.statusCode()).isEqualTo(200);
		var keys = response.getBody().as(UserKey[].class);
		assertThat(keys).hasSize(1);
		assertThat(keys[0].getId()).isEqualTo(auth.getKeyId());
		
		long deleteTime = System.currentTimeMillis();
		
		response = RestAssured.given()
			.header("Authorization", "Bearer " + authRenew.getAccessToken())
			.delete("/api/auth/v1/mykeys/{keyId}", auth.getKeyId());
		assertThat(response.statusCode()).isEqualTo(200);
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		assertThat(response.statusCode()).isEqualTo(403);
		
		StepVerifier.create(
			keyRepo.findByIdAndEmail(UUID.fromString(auth.getKeyId()), auth.getEmail())
		).expectNextMatches(k -> k.getDeletedAt() != null && k.getDeletedAt() >= deleteTime).expectComplete();
	}
	
	@Test
	void loginRenewTokenWithKey() throws Exception {
		var user = test.createUser();
		var keyPair1 = test.generateKeyPair();
		long time1 = System.currentTimeMillis();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair1.getPublic().getEncoded(), 60000L, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		assertThat(auth.getKeyCreatedAt()).isGreaterThanOrEqualTo(time1);
		assertThat(auth.getKeyExpiresAt()).isEqualTo(auth.getKeyCreatedAt() + 60000);

		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		assertThat(response.statusCode()).isEqualTo(200);
		var initRenew = response.getBody().as(InitRenewResponse.class);
		assertThat(initRenew.getRandom()).isNotNull();
			
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair1.getPrivate());
		signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		var signature = signer.sign();
		
		var keyPair2 = test.generateKeyPair();
		var time2 = System.currentTimeMillis();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), keyPair2.getPublic().getEncoded(), 30000L))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(200);
		var authRenew = response.getBody().as(AuthResponse.class);
		assertThat(authRenew.getAccessToken()).isNotNull();
		assertThat(authRenew.getEmail()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(authRenew.getPreferences()).isNotNull();
		assertThat(authRenew.getKeyId()).isNotEqualTo(auth.getKeyId());
		assertThat(authRenew.getKeyCreatedAt()).isGreaterThanOrEqualTo(time2);
		assertThat(authRenew.getKeyExpiresAt()).isEqualTo(authRenew.getKeyCreatedAt() + 30000);
			
		response = RestAssured.given()
			.header("Authorization", "Bearer " + authRenew.getAccessToken())
			.get("/api/auth/v1/mykeys");
		assertThat(response.statusCode()).isEqualTo(200);
		var keys = response.getBody().as(UserKey[].class);
		assertThat(keys).hasSize(1);
		assertThat(keys[0].getId()).isEqualTo(authRenew.getKeyId());

		// cannot renew with first key
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		TestUtils.expectError(response, 403, "forbidden");
	}
	
	@Test
	void invalidLogin() {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(test.email(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "invalid-credentials");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "12345678", keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "invalid-credentials");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "1", keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-password");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(null, user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-email");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), null, keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-password");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), null, null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-publicKey");
		
		byte[] invalidKey = new byte[10];
		new Random().nextBytes(invalidKey);
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), invalidKey, null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-publicKey");
	}
	
	@Test
	void invalidPasswordNeedsCaptcha() {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "wrong-password", keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "invalid-credentials");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "wrong-password", keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "invalid-credentials");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "wrong-password", keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "captcha-needed");
			
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "captcha-needed");
		
		var token = RandomStringUtils.insecure().nextAlphabetic(20);
		var stubInvalid = CaptchaStub.stubCaptcha(wireMockServer, "not the good one", false);
		var stubValid = CaptchaStub.stubCaptcha(wireMockServer, token, true);
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), "not the good one"))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "captcha-needed");
		
		assertThat(wireMockServer.countRequestsMatching(stubInvalid.getRequest()).getCount()).isEqualTo(1);
		assertThat(wireMockServer.countRequestsMatching(stubValid.getRequest()).getCount()).isZero();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), token))
			.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(wireMockServer.countRequestsMatching(stubInvalid.getRequest()).getCount()).isEqualTo(1);
		assertThat(wireMockServer.countRequestsMatching(stubValid.getRequest()).getCount()).isEqualTo(1);
		
		wireMockServer.removeStubMapping(stubInvalid);
		wireMockServer.removeStubMapping(stubValid);
	}
	
	@Test
	void testCaptchaKey() {
		var response = RestAssured.given().get("/api/auth/v1/captcha");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().asString()).isEqualTo("captchaClient");
	}
	
	@Test
	void testRenewInvalidRandom() throws Exception {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
				.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		
		// init
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		assertThat(response.statusCode()).isEqualTo(200);
		var initRenew = response.getBody().as(InitRenewResponse.class);
		assertThat(initRenew.getRandom()).isNotNull();
		
		// wrong random in signature (trial 1)
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update((auth.getEmail() + "wrong_random").getBytes(StandardCharsets.UTF_8));
		var signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);

		// wrong email in signature (trial 2)
		signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update(("invalid@email.com" + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);

		// valid renew
		signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(200);
		
		// init again
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		assertThat(response.statusCode()).isEqualTo(200);
		initRenew = response.getBody().as(InitRenewResponse.class);
		assertThat(initRenew.getRandom()).isNotNull();

		var incorrectRandom = RandomStringUtils.insecure().nextAlphabetic(44);
		
		// invalid random in request (trial 1)
		signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), incorrectRandom, auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);
		
		// invalid random in request and signature (trial 2)
		signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update((auth.getEmail() + incorrectRandom).getBytes(StandardCharsets.UTF_8));
		signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), incorrectRandom, auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);
		
		// invalid again (trial 3)
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), incorrectRandom, auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);
		
		// invalid again (trial 4)
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), incorrectRandom, auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);
		
		// valid, but after 4 trials key has been deleted
		signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);
	}
	
	@Test
	void testRenewInvalidKey() throws Exception {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
				.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		assertThat(response.statusCode()).isEqualTo(200);
		var initRenew = response.getBody().as(InitRenewResponse.class);
		assertThat(initRenew.getRandom()).isNotNull();
		
		var wrongKeyPair = test.generateKeyPair();
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(wrongKeyPair.getPrivate());
		signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		var signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);
	}
	
	@Test
	void testRenewInvalidSingature() throws Exception {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
				.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		assertThat(response.statusCode()).isEqualTo(200);
		var initRenew = response.getBody().as(InitRenewResponse.class);
		assertThat(initRenew.getRandom()).isNotNull();
		
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		var signature = signer.sign();
		signature[2]++;
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);

		signature[2]--;
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(200);
	}
	
	@Test
	void testDeleteExpiredKeys() {
		var user = test.createUser(false);
		var login1 = test.login(user, 60000L, new HashMap<>());
		var login2 = test.login(user, 60000L, new HashMap<>());
		var keys = keyRepo.findByEmail(login2.getAuth().getEmail()).collectList().block();
		assertThat(keys).hasSize(2).allMatch(k -> k.getDeletedAt() == null);
		assertThat(keyRepo.findByEmail(login2.getAuth().getEmail()).count().block()).isEqualTo(2L);
		// change key2 expiration in the past
		var key2 = keyRepo.findByIdAndEmail(UUID.fromString(login2.getAuth().getKeyId()), login2.getAuth().getEmail()).block();
		key2.setCreatedAt(System.currentTimeMillis() - 120000L);
		keyRepo.save(key2).block();
		// run scheduled task
		service.handleExpiredKeys().block();
		keys = keyRepo.findByEmail(login2.getAuth().getEmail()).collectList().block();
		assertThat(keys).hasSize(2).allMatch(k -> {
			if (k.getId().equals(UUID.fromString(login1.getAuth().getKeyId())))
				return k.getDeletedAt() == null;
			return k.getDeletedAt() != null;
		});
	}
	
	@Test
	void testCleanDeletedKeys() throws Exception {
		var user = test.createUser(false);
		var login1 = test.login(user, 60000L, Map.of("deviceId", "1"));
		var login2 = test.login(user, 60000L, Map.of("deviceId", "2"));
		var login3 = test.login(user, 60000L, Map.of("deviceId", "3"));
		var login4 = test.login(user, 60000L, Map.of("deviceId", "3"));
		
		// mark key 2 as deleted since 16 months
		var key = keyRepo.findByIdAndEmail(UUID.fromString(login2.getAuth().getKeyId()), login2.getAuth().getEmail()).block();
		key.setDeletedAt(System.currentTimeMillis() - 16L * 31 * 24 * 60 * 60 * 1000);
		keyRepo.save(key).block();
		
		// mark key 3 and key 4 as deleted since 1 day, with key 3 created before key 4
		key = keyRepo.findByIdAndEmail(UUID.fromString(login3.getAuth().getKeyId()), login3.getAuth().getEmail()).block();
		key.setCreatedAt(System.currentTimeMillis() - 60000);
		key.setDeletedAt(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
		keyRepo.save(key).block();
		key = keyRepo.findByIdAndEmail(UUID.fromString(login4.getAuth().getKeyId()), login4.getAuth().getEmail()).block();
		key.setDeletedAt(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
		keyRepo.save(key).block();
		
		// run scheduled task
		service.cleanKeys().block();
		
		// expect key 2 and key 3 to be permanently deleted
		var keys = keyRepo.findByEmail(login1.getAuth().getEmail()).collectList().block();
		assertThat(keys)
			.hasSize(2)
			.anyMatch(k -> k.getId().toString().equals(login1.getAuth().getKeyId()))
			.anyMatch(k -> k.getId().toString().equals(login4.getAuth().getKeyId()))
			;
	}
	
}
