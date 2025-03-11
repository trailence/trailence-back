package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.trailence.auth.dto.AuthResponse;
import org.trailence.auth.dto.InitRenewRequest;
import org.trailence.auth.dto.InitRenewResponse;
import org.trailence.auth.dto.LoginShareRequest;
import org.trailence.auth.dto.RenewTokenRequest;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestService.TestUserLoggedIn;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.trail.rest.ShareV1Controller.ShareV1;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.trailence.trail.rest.ShareV1Controller.CreateShareRequestV1;

@SuppressWarnings("deprecation")
class TestSharesV1 extends AbstractTest {

	@Test
	void shareCollectionToNewUser() throws Exception {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var col2 = user.createCollection();
		var trail1 = user.createTrail(mytrails, true);
		var trail2 = user.createTrail(mytrails, false);
		var trail3 = user.createTrail(col2, true);
		var tracks = user.getTracks();
		var sharedTracks = tracks.stream().filter(t -> !t.getUuid().equals(trail3.getOriginalTrackUuid()) && !t.getUuid().equals(trail3.getCurrentTrackUuid())).toList();
		
		String to = "myFriend." + RandomStringUtils.insecure().nextAlphanumeric(1, 9) + "@trailEncE.oRg";
		var request = new CreateShareRequestV1(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			to,
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v1", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(ShareV1.class);
		assertThat(share.getId()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getFrom()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getTo()).isEqualTo(request.getTo().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		assertThat(getShares(user)).singleElement().extracting("id").isEqualTo(share.getId());
		
		var mail = assertMailSent("trailence@trailence.org", to.toLowerCase());
		assertThat(mail.getT1()).isEqualTo(user.getEmail().toLowerCase() + " shared trails with you on trailence.org");
		var i = mail.getT2().indexOf("\r\nYou can access it without having an account by following this link: ");
		assertThat(i).isPositive();
		var j = mail.getT2().indexOf("\r\n", i + 70);
		assertThat(j).isPositive();
		var link = mail.getT2().substring(i + 70, j);
		assertThat(link).startsWith("https://trailence.org/link/").endsWith("?lang=en");
		i = link.lastIndexOf('?');
		var token = link.substring(27, i);
		
		var keyPair = test.generateKeyPair();
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginShareRequest(token, keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>()))
			.post("/api/auth/v1/share");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		assertThat(auth.isComplete()).isFalse();
		var friend = new TestUserLoggedIn(request.getTo(), null, keyPair, auth);
		
		assertThat(friend.getCollections()).singleElement().extracting(c -> c.getType()).isEqualTo(TrailCollectionType.MY_TRAILS);
		friend.expectTracksIds(sharedTracks);
		friend.expectTrails(trail1, trail2);
		var shares = getShares(friend);
		assertThat(shares).hasSize(1);
		assertThat(shares.getFirst()).extracting("id").isEqualTo(share.getId());
		
		response = friend.delete("/api/share/v1/" + user.getEmail() + "/" + share.getId());
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(getShares(user)).isEmpty();
		assertThat(getShares(friend)).isEmpty();
		assertThat(friend.getTracks()).isEmpty();
		assertThat(friend.getTrails()).isEmpty();
		
		// renew token
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
		assertThat(authRenew.getEmail()).isEqualTo(friend.getEmail().toLowerCase());
		assertThat(authRenew.getPreferences()).isNotNull();
		assertThat(authRenew.getKeyId()).isEqualTo(auth.getKeyId());
		assertThat(authRenew.isComplete()).isFalse();
	}
	
	@Test
	void shareTagsToExistingUser() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		
		var mytrails = user1.getMyTrails();
		var trail1 = user1.createTrail(mytrails, true);
		var trail2 = user1.createTrail(mytrails, false);
		var trail3 = user1.createTrail(mytrails, false);
		var trail4 = user1.createTrail(mytrails, true);
		
		var tag1 = user1.createTag(mytrails, null);
		var tag2 = user1.createTag(mytrails, null);
		var tag3 = user1.createTag(mytrails, null);
		user1.createTrailTag(trail1, tag1);
		user1.createTrailTag(trail2, tag2);
		user1.createTrailTag(trail3, tag1);
		user1.createTrailTag(trail3, tag2);
		user1.createTrailTag(trail3, tag3);
		user1.createTrailTag(trail4, tag3);
		
		var request = new CreateShareRequestV1(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			user2.getEmail(),
			ShareElementType.TAG,
			List.of(tag1.getUuid(), tag2.getUuid()),
			"fr",
			false
		);
		var response = user1.post("/api/share/v1", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(ShareV1.class);
		assertThat(share.getId()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getFrom()).isEqualTo(user1.getEmail().toLowerCase());
		assertThat(share.getTo()).isEqualTo(user2.getEmail().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.TAG);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		assertThat(getShares(user1)).singleElement().extracting("id").isEqualTo(share.getId());
		
		var mail = assertMailSent("trailence@trailence.org", user2.getEmail().toLowerCase());
		assertThat(mail.getT1()).isEqualTo(user1.getEmail().toLowerCase() + " vous a partagé des parcours sur trailence.org");
		assertThat(mail.getT2()).contains("il suffit de vous connecter et d'aller dans le menu");
		
		var tracks = user1.getTracks();
		var sharedTracks = tracks.stream().filter(t -> !t.getUuid().equals(trail4.getOriginalTrackUuid())).toList();
		user2.expectTracksIds(sharedTracks);
		user2.expectTrails(trail1, trail2, trail3);
		var shares = getShares(user2);
		assertThat(shares).hasSize(1);
		assertThat(shares.getFirst()).extracting("id").isEqualTo(share.getId());
		
		response = user1.delete("/api/share/v1/" + user1.getEmail() + "/" + share.getId());
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(getShares(user1)).isEmpty();
		assertThat(getShares(user2)).isEmpty();
		assertThat(user2.getTracks()).isEmpty();
		assertThat(user2.getTrails()).isEmpty();
	}
	
	@Test
	void createTwiceTheSameCreateOnlyFirst() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		
		var request1 = new CreateShareRequestV1(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			test.email(),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"xx",
			false
		);
		var response = user.post("/api/share/v1", request1);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(ShareV1.class);
		assertThat(share.getId()).isEqualTo(request1.getId());
		
		assertThat(getShares(user)).singleElement().extracting("id").isEqualTo(share.getId());
		
		var request2 = new CreateShareRequestV1(
			request1.getId(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			test.email(),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"xx",
			false
		);
		response = user.post("/api/share/v1", request2);
		assertThat(response.statusCode()).isEqualTo(200);
		share = response.getBody().as(ShareV1.class);
		assertThat(share.getId()).isEqualTo(request1.getId());
		
		assertThat(getShares(user)).singleElement().extracting("id").isEqualTo(share.getId());
	}
	
	@Test
	void shareThenDeleteTrailsShouldRemoveTheEmptyShare() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail1 = user.createTrail(mytrails, true);
		var trail2 = user.createTrail(mytrails, true);
		
		var request = new CreateShareRequestV1(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			"notReallyAFriend." + RandomStringUtils.insecure().nextAlphanumeric(1, 9) + "@trailence.org",
			ShareElementType.TRAIL,
			List.of(trail1.getUuid(), trail2.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v1", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(ShareV1.class);
		assertThat(share.getId()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getFrom()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getTo()).isEqualTo(request.getTo().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.TRAIL);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		assertThat(getShares(user)).singleElement().extracting("id").isEqualTo(share.getId());
		
		user.deleteTrails(trail1, trail2);
		assertThat(user.getShares()).isEmpty();
	}
	
	@Test
	void emptyShareIsNotCreated() {
		var user = test.createUserAndLogin();
		var request = new CreateShareRequestV1(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			"someone@trailence.org",
			ShareElementType.COLLECTION,
			List.of(),
			"en",
			false
		);
		var response = user.post("/api/share/v1", request);
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(getShares(user)).isEmpty();
		assertMailNotSent("trailence@trailence.org", "someone@trailence.org");
	}
	
	private List<ShareV1> getShares(TestUserLoggedIn user) {
		var response = user.get("/api/share/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		return Arrays.asList(response.getBody().as(ShareV1[].class));
	}


}
