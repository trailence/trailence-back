package org.trailence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.HashMap;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
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

class TestAuth extends AbstractTest {

	@Test
	void testLogin() {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		assertThat(auth.getAccessToken()).isNotNull();
		assertThat(auth.getEmail()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(auth.getPreferences()).isNotNull();
	}
	
	@Test
	void testLoginRenewDelete() throws Exception {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
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
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>()))
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
		
		response = RestAssured.given()
			.header("Authorization", "Bearer " + authRenew.getAccessToken())
			.delete("/api/auth/v1/mykeys/{keyId}", auth.getKeyId());
		assertThat(response.statusCode()).isEqualTo(200);
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
			.post("/api/auth/v1/init_renew");
		assertThat(response.statusCode()).isEqualTo(403);
	}
	
	@Test
	void invalidLogin() {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(test.email(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "invalid-credentials");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "12345678", keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "invalid-credentials");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "1", keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-password");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(null, user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-email");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), null, keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-password");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-publicKey");
		
		byte[] invalidKey = new byte[10];
		new Random().nextBytes(invalidKey);
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), invalidKey, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 400, "invalid-publicKey");
	}
	
	@Test
	void invalidPasswordNeedsCaptcha() {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "wrong-password", keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "invalid-credentials");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "wrong-password", keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "invalid-credentials");
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "wrong-password", keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "captcha-needed");
			
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "captcha-needed");
		
		var token = RandomStringUtils.randomAlphabetic(20);
		var stubInvalid = CaptchaStub.stubCaptcha(wireMockServer, "not the good one", false);
		var stubValid = CaptchaStub.stubCaptcha(wireMockServer, token, true);
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), "not the good one"))
			.post("/api/auth/v1/login");
		TestUtils.expectError(response, 403, "captcha-needed");
		
		assertThat(wireMockServer.countRequestsMatching(stubInvalid.getRequest()).getCount()).isEqualTo(1);
		assertThat(wireMockServer.countRequestsMatching(stubValid.getRequest()).getCount()).isZero();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), token))
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
				.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
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
		signer.update((auth.getEmail() + "wrong_random").getBytes(StandardCharsets.UTF_8));
		var signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>()))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);

		signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update(("invalid@email.com" + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>()))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);

		signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keyPair.getPrivate());
		signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
		signature = signer.sign();
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>()))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(200);
	}
	
	@Test
	void testRenewInvalidKey() throws Exception {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
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
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>()))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);
	}
	
	
	@Test
	void testRenewInvalidSingature() throws Exception {
		var user = test.createUser();
		var keyPair = test.generateKeyPair();
		var response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
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
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>()))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(403);

		signature[2]--;
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>()))
			.post("/api/auth/v1/renew");
		assertThat(response.statusCode()).isEqualTo(200);
	}

}
