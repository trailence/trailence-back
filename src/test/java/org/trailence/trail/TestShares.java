package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.trailence.auth.dto.AuthResponse;
import org.trailence.auth.dto.InitRenewRequest;
import org.trailence.auth.dto.InitRenewResponse;
import org.trailence.auth.dto.LoginShareRequest;
import org.trailence.auth.dto.RenewTokenRequest;
import org.trailence.global.rest.ApiError;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestService.TestUserLoggedIn;
import org.trailence.trail.dto.CreateShareRequest;
import org.trailence.trail.dto.Share;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.trail.dto.UpdateShareRequest;

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
		
		String to = "myFriend." + RandomStringUtils.insecure().nextAlphanumeric(1, 9) + "@trailEncE.oRg";
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(to),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getRecipients()).containsExactlyInAnyOrderElementsOf(request.getRecipients().stream().map(String::toLowerCase).toList());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		assertThat(user.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		
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
		var friend = new TestUserLoggedIn(to, null, keyPair, auth);
		
		assertThat(friend.getCollections()).singleElement().extracting(c -> c.getType()).isEqualTo(TrailCollectionType.MY_TRAILS);
		friend.expectTracksIds(sharedTracks);
		friend.expectTrails(trail1, trail2);
		var shares = friend.getShares();
		assertThat(shares).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		
		response = friend.delete("/api/share/v2/" + user.getEmail() + "/" + share.getUuid());
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
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(user2.getEmail()),
			ShareElementType.TAG,
			List.of(tag1.getUuid(), tag2.getUuid()),
			"fr",
			false
		);
		var response = user1.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getOwner()).isEqualTo(user1.getEmail().toLowerCase());
		assertThat(share.getRecipients()).containsExactlyInAnyOrderElementsOf(request.getRecipients().stream().map(String::toLowerCase).toList());
		assertThat(share.getType()).isEqualTo(ShareElementType.TAG);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		assertThat(user1.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		
		var mail = assertMailSent("trailence@trailence.org", user2.getEmail().toLowerCase());
		assertThat(mail.getT1()).isEqualTo(user1.getEmail().toLowerCase() + " vous a partagé des parcours sur trailence.org");
		assertThat(mail.getT2()).contains("il suffit de vous connecter et d'aller dans le menu");
		
		var tracks = user1.getTracks();
		var sharedTracks = tracks.stream().filter(t -> !t.getUuid().equals(trail4.getOriginalTrackUuid())).toList();
		user2.expectTracksIds(sharedTracks);
		user2.expectTrails(trail1, trail2, trail3);
		var shares = user2.getShares();
		assertThat(shares).hasSize(1);
		assertThat(shares.getFirst()).extracting("uuid").isEqualTo(share.getUuid());
		
		response = user1.delete("/api/share/v2/" + user1.getEmail() + "/" + share.getUuid());
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
		
		var to1 = test.email();
		var request1 = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(to1),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"xx",
			false
		);
		var response = user.post("/api/share/v2", request1);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request1.getId());
		
		assertThat(user.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		assertMailSent("trailence@trailence.org", to1.toLowerCase());
		
		var to2 = test.email();
		var request2 = new CreateShareRequest(
			request1.getId(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(to2),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"xx",
			false
		);
		response = user.post("/api/share/v2", request2);
		assertThat(response.statusCode()).isEqualTo(200);
		share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request1.getId());
		
		assertThat(user.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		assertMailNotSent("trailence@trailence.org", to2.toLowerCase());
	}
	
	@Test
	void shareThenDeleteTrailsShouldRemoveTheEmptyShare() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail1 = user.createTrail(mytrails, true);
		var trail2 = user.createTrail(mytrails, true);
		
		var to = "notReallyAFriend." + RandomStringUtils.insecure().nextAlphanumeric(1, 9) + "@trailence.org";
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(to),
			ShareElementType.TRAIL,
			List.of(trail1.getUuid(), trail2.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getRecipients()).containsExactlyInAnyOrderElementsOf(request.getRecipients().stream().map(String::toLowerCase).toList());
		assertThat(share.getType()).isEqualTo(ShareElementType.TRAIL);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		assertThat(user.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		assertMailSent("trailence@trailence.org", to.toLowerCase());
		
		user.deleteTrails(trail1, trail2);
		assertThat(user.getShares()).isEmpty();
	}
	
	@Test
	void emptyShareIsNotCreated() {
		var user = test.createUserAndLogin();
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of("someone@trailence.org"),
			ShareElementType.COLLECTION,
			List.of(),
			"en",
			false
		);
		var response = user.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(user.getShares()).isEmpty();
		assertMailNotSent("trailence@trailence.org", "someone@trailence.org");
	}
	
	@Test
	void shareWithNobodyFails() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(400);
		var error = response.getBody().as(ApiError.class);
		assertThat(error.getErrorCode()).isEqualTo("invalid-recipients");
		assertThat(error.getErrorMessage()).isEqualTo("size must be between 1 and 20");
	}
	
	@Test
	void shareWithTooManyPeopleFails() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			IntStream.range(1, 22).mapToObj(i -> i + "@test.com").toList(),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(400);
		var error = response.getBody().as(ApiError.class);
		assertThat(error.getErrorCode()).isEqualTo("invalid-recipients");
		assertThat(error.getErrorMessage()).isEqualTo("size must be between 1 and 20");
		IntStream.range(1, 22).mapToObj(i -> i + "@test.com").forEach(email -> assertMailNotSent("trailence@trailence.org", email));
	}
	
	@Test
	void shareWith3PeoplesThenPeopleLeft() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail1 = user.createTrail(mytrails, true);
		var trail2 = user.createTrail(mytrails, true);
		
		var friend1 = test.createUserAndLogin();
		var friend2 = test.createUserAndLogin();
		var friend3 = test.createUserAndLogin();
		
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(friend1.getEmail(), friend2.getEmail(), friend3.getEmail()),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getRecipients()).containsExactlyInAnyOrderElementsOf(request.getRecipients().stream().map(String::toLowerCase).toList());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		
		var shares = user.getShares();
		assertThat(user.getShares()).hasSize(1);
		var s = shares.getFirst();
		assertThat(s.getUuid()).isEqualTo(share.getUuid());
		assertThat(s.getRecipients()).containsExactlyInAnyOrder(friend1.getEmail().toLowerCase(), friend2.getEmail().toLowerCase(), friend3.getEmail().toLowerCase());
		friend1.expectTrails(trail1, trail2);
		shares = friend1.getShares();
		s = shares.getFirst();
		assertThat(s.getUuid()).isEqualTo(share.getUuid());
		assertThat(s.getRecipients()).singleElement().isEqualTo(friend1.getEmail().toLowerCase());
		friend2.expectTrails(trail1, trail2);
		assertThat(friend2.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());		
		friend3.expectTrails(trail1, trail2);
		assertThat(friend3.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		assertMailSent("trailence@trailence.org", friend1.getEmail().toLowerCase());
		assertMailSent("trailence@trailence.org", friend2.getEmail().toLowerCase());
		assertMailSent("trailence@trailence.org", friend3.getEmail().toLowerCase());
		
		response = friend1.delete("/api/share/v2/" + user.getEmail() + "/" + share.getUuid());
		assertThat(response.statusCode()).isEqualTo(200);
		shares = user.getShares();
		assertThat(user.getShares()).hasSize(1);
		s = shares.getFirst();
		assertThat(s.getUuid()).isEqualTo(share.getUuid());
		assertThat(s.getRecipients()).containsExactlyInAnyOrder(friend2.getEmail().toLowerCase(), friend3.getEmail().toLowerCase());
		assertThat(friend1.getTrails()).isEmpty();
		assertThat(friend1.getShares()).isEmpty();
		friend2.expectTrails(trail1, trail2);
		assertThat(friend2.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());		
		friend3.expectTrails(trail1, trail2);
		assertThat(friend3.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		
		response = friend2.delete("/api/share/v2/" + user.getEmail() + "/" + share.getUuid());
		assertThat(response.statusCode()).isEqualTo(200);
		shares = user.getShares();
		assertThat(user.getShares()).hasSize(1);
		s = shares.getFirst();
		assertThat(s.getUuid()).isEqualTo(share.getUuid());
		assertThat(s.getRecipients()).containsExactlyInAnyOrder(friend3.getEmail().toLowerCase());
		assertThat(friend1.getTrails()).isEmpty();
		assertThat(friend1.getShares()).isEmpty();
		assertThat(friend2.getTrails()).isEmpty();
		assertThat(friend2.getShares()).isEmpty();
		friend3.expectTrails(trail1, trail2);
		assertThat(friend3.getShares()).singleElement().extracting("uuid").isEqualTo(share.getUuid());
		
		response = friend3.delete("/api/share/v2/" + user.getEmail() + "/" + share.getUuid());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user.getShares()).isEmpty();
		assertThat(friend1.getTrails()).isEmpty();
		assertThat(friend1.getShares()).isEmpty();
		assertThat(friend2.getTrails()).isEmpty();
		assertThat(friend2.getShares()).isEmpty();
		assertThat(friend3.getTrails()).isEmpty();
		assertThat(friend3.getShares()).isEmpty();
	}
	
	@Test
	void shareAndUpdate() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		
		var friend1 = test.createUserAndLogin();
		var friend2 = test.createUserAndLogin();
		var friend3 = test.createUserAndLogin();
		
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(friend1.getEmail(), friend2.getEmail()),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.isIncludePhotos()).isFalse();
		assertThat(share.getRecipients()).containsExactlyInAnyOrder(friend1.getEmail().toLowerCase(), friend2.getEmail().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		assertMailSent("trailence@trailence.org", friend1.getEmail().toLowerCase());
		assertMailSent("trailence@trailence.org", friend2.getEmail().toLowerCase());
		
		var update = new UpdateShareRequest(
			"new name",
			true,
			List.of(friend1.getEmail(), friend3.getEmail()),
			"en"
		);
		response = user.put("/api/share/v2/" + share.getUuid(), update);
		assertThat(response.statusCode()).isEqualTo(200);
		share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getName()).isEqualTo(update.getName());
		assertThat(share.isIncludePhotos()).isTrue();
		assertThat(share.getRecipients()).containsExactlyInAnyOrder(friend1.getEmail().toLowerCase(), friend3.getEmail().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertMailNotSent("trailence@trailence.org", friend1.getEmail().toLowerCase());
		assertMailNotSent("trailence@trailence.org", friend2.getEmail().toLowerCase());
		assertMailSent("trailence@trailence.org", friend3.getEmail().toLowerCase());

		var shares = user.getShares();
		assertThat(shares).hasSize(1);
		share = shares.getFirst();
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getName()).isEqualTo(update.getName());
		assertThat(share.isIncludePhotos()).isTrue();
		assertThat(share.getRecipients()).containsExactlyInAnyOrder(friend1.getEmail().toLowerCase(), friend3.getEmail().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
	}
	
	@Test
	void shareThenRemoveRecipientThenAddAgainSendEmailOnlyOnce() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		
		var friend1 = test.createUserAndLogin();
		var friend2 = test.createUserAndLogin();
		var friend3 = test.createUserAndLogin();
		
		var request = new CreateShareRequest(
			UUID.randomUUID().toString(),
			RandomStringUtils.insecure().nextAlphanumeric(1, 51),
			List.of(friend1.getEmail(), friend2.getEmail()),
			ShareElementType.COLLECTION,
			List.of(mytrails.getUuid()),
			"en",
			false
		);
		var response = user.post("/api/share/v2", request);
		assertThat(response.statusCode()).isEqualTo(200);
		var share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getName()).isEqualTo(request.getName());
		assertThat(share.isIncludePhotos()).isFalse();
		assertThat(share.getRecipients()).containsExactlyInAnyOrder(friend1.getEmail().toLowerCase(), friend2.getEmail().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertThat(share.getTrails()).isNull();
		assertMailSent("trailence@trailence.org", friend1.getEmail().toLowerCase());
		assertMailSent("trailence@trailence.org", friend2.getEmail().toLowerCase());
		
		var update = new UpdateShareRequest(
			"new name",
			true,
			List.of(friend1.getEmail(), friend3.getEmail()),
			"en"
		);
		response = user.put("/api/share/v2/" + share.getUuid(), update);
		assertThat(response.statusCode()).isEqualTo(200);
		share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getName()).isEqualTo(update.getName());
		assertThat(share.isIncludePhotos()).isTrue();
		assertThat(share.getRecipients()).containsExactlyInAnyOrder(friend1.getEmail().toLowerCase(), friend3.getEmail().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertMailNotSent("trailence@trailence.org", friend1.getEmail().toLowerCase());
		assertMailNotSent("trailence@trailence.org", friend2.getEmail().toLowerCase());
		assertMailSent("trailence@trailence.org", friend3.getEmail().toLowerCase());
		
		update = new UpdateShareRequest(
			"new name",
			true,
			List.of(friend1.getEmail(), friend2.getEmail()),
			"en"
		);
		response = user.put("/api/share/v2/" + share.getUuid(), update);
		assertThat(response.statusCode()).isEqualTo(200);
		share = response.getBody().as(Share.class);
		assertThat(share.getUuid()).isEqualTo(request.getId());
		assertThat(share.getOwner()).isEqualTo(user.getEmail().toLowerCase());
		assertThat(share.getName()).isEqualTo(update.getName());
		assertThat(share.isIncludePhotos()).isTrue();
		assertThat(share.getRecipients()).containsExactlyInAnyOrder(friend1.getEmail().toLowerCase(), friend2.getEmail().toLowerCase());
		assertThat(share.getType()).isEqualTo(ShareElementType.COLLECTION);
		assertThat(share.getElements()).isEqualTo(request.getElements());
		assertMailNotSent("trailence@trailence.org", friend1.getEmail().toLowerCase());
		assertMailNotSent("trailence@trailence.org", friend2.getEmail().toLowerCase());
		assertMailNotSent("trailence@trailence.org", friend3.getEmail().toLowerCase());
	}
}
