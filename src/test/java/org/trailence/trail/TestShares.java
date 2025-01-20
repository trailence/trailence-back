package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.trailence.auth.dto.AuthResponse;
import org.trailence.auth.dto.InitRenewRequest;
import org.trailence.auth.dto.InitRenewResponse;
import org.trailence.auth.dto.LoginShareRequest;
import org.trailence.auth.dto.RenewTokenRequest;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestService.TestUserLoggedIn;
import org.trailence.trail.dto.CreateShareRequest;
import org.trailence.trail.dto.Share;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.TrailCollectionType;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class TestShares extends AbstractTest {

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
		
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.randomAlphanumeric(1, 51),
			"myFriend@trailEncE.oRg",
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v1", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getId()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getFrom()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getTo()).isEqualTo(request.getTo().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		assertThat(user.getShares()).singleElement().extracting("id").isEqualTo(share.getId());
		
		var mail = assertMailSent("trailence@trailence.org", "myfriend@trailence.org");
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
		var shares = friend.getShares();
		assertThat(shares).hasSize(1);
		assertThat(shares.getFirst()).extracting("id").isEqualTo(share.getId());
		
		response = friend.delete("/api/share/v1/" + user.getEmail() + "/" + share.getId());
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(user.getShares()).isEmpty();
		assertThat(friend.getShares()).isEmpty();
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
		
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.randomAlphanumeric(1, 51),
			user2.getEmail(),
			ShareElementType.TAG,
			List.of(tag1.getUuid(), tag2.getUuid()),
			"fr",
			false
		);
		var response = user1.post("/api/share/v1", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getId()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getFrom()).isEqualTo(user1.getEmail().toLowerCase());
		assertThat(share.getTo()).isEqualTo(user2.getEmail().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.TAG);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		assertThat(user1.getShares()).singleElement().extracting("id").isEqualTo(share.getId());
		
		var mail = assertMailSent("trailence@trailence.org", user2.getEmail().toLowerCase());
		assertThat(mail.getT1()).isEqualTo(user1.getEmail().toLowerCase() + " vous a partagé des parcours sur trailence.org");
		assertThat(mail.getT2()).contains("il suffit de vous connecter et d'aller dans le menu");
		
		var tracks = user1.getTracks();
		var sharedTracks = tracks.stream().filter(t -> !t.getUuid().equals(trail4.getOriginalTrackUuid())).toList();
		user2.expectTracksIds(sharedTracks);
		user2.expectTrails(trail1, trail2, trail3);
		var shares = user2.getShares();
		assertThat(shares).hasSize(1);
		assertThat(shares.getFirst()).extracting("id").isEqualTo(share.getId());
		
		response = user1.delete("/api/share/v1/" + user1.getEmail() + "/" + share.getId());
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(user1.getShares()).isEmpty();
		assertThat(user2.getShares()).isEmpty();
		assertThat(user2.getTracks()).isEmpty();
		assertThat(user2.getTrails()).isEmpty();
	}
	
	@Test
	void createTwiceTheSameCreateOnlyFirst() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		
		var request1 = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.randomAlphanumeric(1, 51),
			test.email(),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"xx",
			false
		);
		var response = user.post("/api/share/v1", request1);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getId()).isEqualTo(request1.getId());
		
		assertThat(user.getShares()).singleElement().extracting("id").isEqualTo(share.getId());
		
		var request2 = new CreateShareRequest(
			request1.getId(),
			RandomStringUtils.randomAlphanumeric(1, 51),
			test.email(),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"xx",
			false
		);
		response = user.post("/api/share/v1", request2);
		assertThat(response.statusCode()).isEqualTo(200);
		share = response.getBody().as(Share.class);
		assertThat(share.getId()).isEqualTo(request1.getId());
		
		assertThat(user.getShares()).singleElement().extracting("id").isEqualTo(share.getId());
	}
}
