package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.trail.dto.Trail;

import io.restassured.common.mapper.TypeRef;

class TestTrails extends AbstractTest {

	@Test
	void crud() {
		var user = test.createUserAndLogin();
		
		var mytrails = user.getMyTrails();
		
		var trail1 = user.createTrail(mytrails, true);
		var trail2 = user.createTrail(mytrails, false);
		var trail3 = user.createTrail(mytrails, true);
		var trail4 = user.createTrail(mytrails, false);
		
		user.expectTrails(trail1, trail2, trail3, trail4);
		
		// update trail 2
		trail2.setName("abcd");
		trail2.setDescription("efghij");
		trail2.setLocation("klmnopqrst");
		trail2.setLoopType("uv");
		var response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail2));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Trail[].class);
		assertThat(updated).hasSize(1);
		assertThat(updated[0].getName()).isEqualTo(trail2.getName());
		assertThat(updated[0].getDescription()).isEqualTo(trail2.getDescription());
		assertThat(updated[0].getLocation()).isEqualTo(trail2.getLocation());
		assertThat(updated[0].getLoopType()).isEqualTo(trail2.getLoopType());
		assertThat(updated[0].getVersion()).isEqualTo(2L);
		trail2 = updated[0];
		
		user.expectTrails(trail1, trail2, trail3, trail4);
		
		// delete trail 3
		user.deleteTrails(trail3);
		
		user.expectTrails(trail1, trail2, trail4);
		
		// known trail1, original trail2, trail3
		response = user.post("/api/trail/v1/_bulkGetUpdates", List.of(
			new Versioned(trail1.getUuid(), user.getEmail(), 1L),
			new Versioned(trail2.getUuid(), user.getEmail(), 1L),
			new Versioned(trail3.getUuid(), user.getEmail(), 1L)
		));
		assertThat(response.statusCode()).isEqualTo(200);
		var updates = response.getBody().as(new TypeRef<UpdateResponse<Trail>>() {});
		assertThat(updates.getCreated()).singleElement().extracting("uuid").isEqualTo(trail4.getUuid());
		assertThat(updates.getUpdated()).singleElement().extracting("uuid").isEqualTo(trail2.getUuid());
		assertThat(updates.getDeleted()).singleElement().extracting("uuid").isEqualTo(trail3.getUuid());
	}
	
	@Test
	void moveToAnotherCollection() {
		var user = test.createUserAndLogin();
		
		var mytrails = user.getMyTrails();
		var collection2 = user.createCollection();
		var trail = user.createTrail(mytrails, true);
		
		trail.setCollectionUuid(collection2.getUuid());
		var response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Trail[].class);
		assertThat(updated).hasSize(1);
		assertThat(updated[0].getUuid()).isEqualTo(trail.getUuid());
		assertThat(updated[0].getCollectionUuid()).isEqualTo(collection2.getUuid());
		assertThat(updated[0].getVersion()).isEqualTo(2L);
	}
	
	@Test
	void updateTracks() {
		var user = test.createUserAndLogin();
		
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);
		
		var newTrack1 = user.createTrack();
		var newTrack2 = user.createTrack();
		
		var originalTrackUuid = trail.getOriginalTrackUuid();
		trail.setOriginalTrackUuid(newTrack1.getUuid());
		trail.setCurrentTrackUuid(newTrack2.getUuid());
		var response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Trail[].class);
		assertThat(updated).hasSize(1);
		assertThat(updated[0].getUuid()).isEqualTo(trail.getUuid());
		assertThat(updated[0].getOriginalTrackUuid()).isEqualTo(originalTrackUuid);
		assertThat(updated[0].getCurrentTrackUuid()).isEqualTo(newTrack2.getUuid());
		assertThat(updated[0].getVersion()).isEqualTo(2L);
	}
	
	@Test
	void createWithNullables() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		var track = user.createTrack();
		
		var response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			null, null, null, null,
			track.getUuid(),
			track.getUuid(),
			col.getUuid()
		)));
		assertThat(response.statusCode()).isEqualTo(200);
		var created = response.getBody().as(Trail[].class);
		assertThat(created).hasSize(1);
		assertThat(created[0].getName()).isNull();
		assertThat(created[0].getDescription()).isNull();
		assertThat(created[0].getLocation()).isNull();
		assertThat(created[0].getLoopType()).isNull();
		assertThat(created[0].getVersion()).isEqualTo(1L);
	}
	
	@Test
	void updateWithNullables() {
		var user = test.createUserAndLogin();
		
		var mytrails = user.getMyTrails();
		var trail = user.createTrail(mytrails, true);

		trail.setName(null);
		trail.setDescription(null);
		trail.setLocation(null);
		trail.setLoopType(null);
		var response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Trail[].class);
		assertThat(updated).hasSize(1);
		assertThat(updated[0].getUuid()).isEqualTo(trail.getUuid());
		assertThat(updated[0].getName()).isNull();
		assertThat(updated[0].getDescription()).isNull();
		assertThat(updated[0].getLocation()).isNull();
		assertThat(updated[0].getLoopType()).isNull();
		assertThat(updated[0].getVersion()).isEqualTo(2L);
	}
	
	@Test
	void createOrUpdateWithLinkedDataThatDoesNotBelongToTheUser() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		
		var col1 = user1.getMyTrails();
		var col2 = user2.getMyTrails();
		
		var track1 = user1.createTrack();
		var track2 = user2.createTrack();
		
		// create with collection of another user
		var response = user1.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user1.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track1.getUuid(),
			track1.getUuid(),
			col2.getUuid()
		)));
		TestUtils.expectError(response, 404, "collection-not-found");
		
		// create with track of another user
		response = user1.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user1.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track1.getUuid(),
			track2.getUuid(),
			col1.getUuid()
		)));
		TestUtils.expectError(response, 404, "track-not-found");
		response = user1.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user1.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track2.getUuid(),
			track1.getUuid(),
			col1.getUuid()
		)));
		TestUtils.expectError(response, 404, "track-not-found");
		
		// create valid
		response = user1.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user1.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track1.getUuid(),
			track1.getUuid(),
			col1.getUuid()
		)));
		assertThat(response.statusCode()).isEqualTo(200);
		var trail = response.getBody().as(Trail[].class)[0];
		
		// update with collection of another user
		trail.setCollectionUuid(col2.getUuid());
		response = user1.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 404, "collection-not-found");
		trail.setCollectionUuid(col1.getUuid());
		
		// update with track of another user
		trail.setCurrentTrackUuid(track2.getUuid());
		response = user1.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 404, "track-not-found");
	}
	
	@Test
	void cannotAccessToTrailsOfSomeoneElse() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		
		var col2 = user2.getMyTrails();
		var trail2 = user2.createTrail(col2, false);
		
		// cannot update
		var originalName = trail2.getName();
		trail2.setName("hacked");
		var response = user1.put("/api/trail/v1/_bulkUpdate", List.of(trail2));
		assertThat(response.statusCode()).isEqualTo(200);
		trail2.setName(originalName);
		assertThat(user2.getTrails()).singleElement().isEqualTo(trail2);
		
		// cannot delete
		response = user1.post("/api/trail/v1/_bulkDelete", List.of(trail2.getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getTrails()).singleElement().isEqualTo(trail2);
	}
	
	@Test
	void createWithInvalidInput() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		var track = user.createTrack();
		
		var response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			null, user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			track.getUuid(),
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "missing-uuid");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			"123", user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			track.getUuid(),
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-uuid");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			track.getUuid(),
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-name-too-long");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			track.getUuid(),
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-description-too-long");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			track.getUuid(),
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-location-too-long");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(3),
			track.getUuid(),
			track.getUuid(),
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-loopType-too-long");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			null,
			track.getUuid(),
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "missing-originalTrackUuid");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			"123",
			track.getUuid(),
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-originalTrackUuid");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			null,
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "missing-currentTrackUuid");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			"123",
			col.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-currentTrackUuid");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			track.getUuid(),
			null
		)));
		TestUtils.expectError(response, 400, "missing-collectionUuid");
		
		response = user.post("/api/trail/v1/_bulkCreate", List.of(new Trail(
			UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			track.getUuid(),
			track.getUuid(),
			"123"
		)));
		TestUtils.expectError(response, 400, "invalid-collectionUuid");
	}
	
	@Test
	void updateWithInvalidInput() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		
		var trail = user.createTrail(col, false);
		trail.setUuid(null);
		var response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "missing-uuid");
		
		trail = user.createTrail(col, false);
		trail.setUuid("1234");
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "invalid-uuid");
		
		trail = user.createTrail(col, false);
		trail.setName(RandomStringUtils.insecure().nextAlphanumeric(201));
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "invalid-name-too-long");
		
		trail = user.createTrail(col, false);
		trail.setDescription(RandomStringUtils.insecure().nextAlphanumeric(50001));
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "invalid-description-too-long");
		
		trail = user.createTrail(col, false);
		trail.setLocation(RandomStringUtils.insecure().nextAlphanumeric(101));
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "invalid-location-too-long");
		
		trail = user.createTrail(col, false);
		trail.setLoopType(RandomStringUtils.insecure().nextAlphanumeric(3));
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "invalid-loopType-too-long");
		
		trail = user.createTrail(col, false);
		trail.setCurrentTrackUuid(null);
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "missing-currentTrackUuid");
		
		trail = user.createTrail(col, false);
		trail.setCurrentTrackUuid("1234");
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "invalid-currentTrackUuid");
		
		trail = user.createTrail(col, false);
		trail.setCurrentTrackUuid(UUID.randomUUID().toString());
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 404, "track-not-found");
		
		trail = user.createTrail(col, false);
		trail.setCollectionUuid(null);
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "missing-collectionUuid");
		
		trail = user.createTrail(col, false);
		trail.setCollectionUuid("1234");
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 400, "invalid-collectionUuid");
		
		trail = user.createTrail(col, false);
		trail.setCollectionUuid(UUID.randomUUID().toString());
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(trail));
		TestUtils.expectError(response, 404, "collection-not-found");
	}
	
	@Test
	void createTwiceTheSameCreateOnlyFirst() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		var trail = user.createTrail(col, true);
		
		var trail2 = new Trail(
			trail.getUuid(), user.getEmail(), 0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			trail.getOriginalTrackUuid(), trail.getCurrentTrackUuid(),
			col.getUuid()
		);
		var response = user.post("/api/trail/v1/_bulkCreate", List.of(trail2));
		assertThat(response.statusCode()).isEqualTo(200);
		var created = response.getBody().as(Trail[].class);
		assertThat(created).singleElement().isEqualTo(trail);
	}
	
	@Test
	void updateWithOlderVersionDoNotUpdate() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		var trail = user.createTrail(col, true);
		
		var update1 = new Trail(
			trail.getUuid(), user.getEmail(), 1, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			trail.getOriginalTrackUuid(), trail.getCurrentTrackUuid(),
			col.getUuid()
		);
		var response = user.put("/api/trail/v1/_bulkUpdate", List.of(update1));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Trail[].class);
		assertThat(updated).singleElement().extracting("name").isEqualTo(update1.getName());
		
		var update2 = new Trail(
			trail.getUuid(), user.getEmail(), 1, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 201),
			RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
			RandomStringUtils.insecure().nextAlphanumeric(0, 101),
			RandomStringUtils.insecure().nextAlphanumeric(0, 3),
			trail.getOriginalTrackUuid(), trail.getCurrentTrackUuid(),
			col.getUuid()
		);
		response = user.put("/api/trail/v1/_bulkUpdate", List.of(update2));
		assertThat(response.statusCode()).isEqualTo(200);
		updated = response.getBody().as(Trail[].class);
		assertThat(updated).singleElement().extracting("name").isEqualTo(update1.getName());
	}
	
	@Test
	void updateWithSameValuesDoNotUpdate() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		var trail = user.createTrail(col, true);
		
		var update1 = new Trail(
			trail.getUuid(), user.getEmail(), 1, 0, 0,
			trail.getName(), trail.getDescription(), trail.getLocation(), trail.getLoopType(),
			trail.getOriginalTrackUuid(), trail.getCurrentTrackUuid(),
			col.getUuid()
		);
		var response = user.put("/api/trail/v1/_bulkUpdate", List.of(update1));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Trail[].class);
		assertThat(updated).singleElement().isEqualTo(trail);
	}
	
}
