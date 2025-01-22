package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.UuidAndOwner;
import org.trailence.global.dto.Versioned;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Track.Point;
import org.trailence.trail.dto.Track.Segment;
import org.trailence.trail.dto.Track.WayPoint;

import io.restassured.common.mapper.TypeRef;

class TestTracks extends AbstractTest {

	@Test
	void crud() {
		var user = test.createUserAndLogin();
		// create 4 tracks
		var track1 = user.createTrack();
		var track2 = user.createTrack();
		var track3 = user.createTrack();
		var track4 = user.createTrack();
		
		// get
		user.expectTracks(track1, track2, track3, track4);
		
		// update track 2
		Random random = new Random();
		track2.setWp(new WayPoint[] {
			new WayPoint(
				random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(),
				RandomStringUtils.insecure().nextAlphanumeric(0, 100), RandomStringUtils.insecure().nextAlphanumeric(0, 100)
			)
		});
		var response = user.put("/api/track/v1", track2);
		assertThat(response.statusCode()).isEqualTo(200);
		var newTrack2 = response.getBody().as(Track.class);
		assertThat(newTrack2.getUpdatedAt()).isGreaterThan(track2.getCreatedAt());
		track2.setUpdatedAt(newTrack2.getUpdatedAt());
		track2.setVersion(2);
		track2.setSizeUsed(newTrack2.getSizeUsed());
		assertThat(newTrack2).isEqualTo(track2);
		
		user.expectTracks(track1, track2, track3, track4);
		
		// delete track 3
		response = user.post("/api/track/v1/_bulkDelete", List.of(track3.getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);
		
		user.expectTracks(track1, track2, track4);
		
		// known track1, original track2, track3
		response = user.post("/api/track/v1/_bulkGetUpdates", List.of(
			new Versioned(track1.getUuid(), user.getEmail(), 1L),
			new Versioned(track2.getUuid(), user.getEmail(), 1L),
			new Versioned(track3.getUuid(), user.getEmail(), 1L)
		));
		assertThat(response.statusCode()).isEqualTo(200);
		var updates = response.getBody().as(new TypeRef<UpdateResponse<UuidAndOwner>>() {});
		assertThat(updates.getCreated()).singleElement().extracting("uuid").isEqualTo(track4.getUuid());
		assertThat(updates.getUpdated()).singleElement().extracting("uuid").isEqualTo(track2.getUuid());
		assertThat(updates.getDeleted()).singleElement().extracting("uuid").isEqualTo(track3.getUuid());
	}
	
	@Test
	void cannotAccessTracksOfSomeoneElse() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		
		var track2 = user2.createTrack();
		
		// cannot read
		var response = user1.get("/api/track/v1/" + user2.getEmail() + "/" + track2.getUuid());
		TestUtils.expectError(response, 404, "track-not-found");
		response = user1.get("/api/track/v1/" + user1.getEmail() + "/" + track2.getUuid());
		TestUtils.expectError(response, 404, "track-not-found");
		
		// cannot update
		response = user1.put("/api/track/v1", track2);
		TestUtils.expectError(response, 404, "track-not-found");
		
		// cannot delete
		response = user1.post("/api/track/v1/_bulkDelete", List.of(track2.getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getTracks()).singleElement().extracting("uuid").isEqualTo(track2.getUuid());
	}
	
	@Test
	void createTrackWithInvalidInput() {
		var user = test.createUserAndLogin();
		
		var track = user.generateRandomTrack();
		track.setUuid("1234");
		var response = user.post("/api/track/v1", track);
		TestUtils.expectError(response, 400, "invalid-uuid");

		track.setUuid(null);
		response = user.post("/api/track/v1", track);
		TestUtils.expectError(response, 400, "missing-uuid");
		
		track = user.generateRandomTrack();
		track.setOwner(test.email());
		response = user.post("/api/track/v1", track);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(Track.class).getOwner()).isEqualTo(user.getEmail().toLowerCase());
	}
	
	@Test
	void createTwiceTheSameCreateOnlyFirst() {
		var user = test.createUserAndLogin();
		var track = user.createTrack();

		var track2 = user.generateRandomTrack();
		track2.setUuid(track.getUuid());
		var response = user.post("/api/track/v1", track);
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(user.getTracks()).singleElement().isEqualTo(new UuidAndOwner(track.getUuid(), user.getEmail().toLowerCase()));
		
		response = user.get("/api/track/v1/" + user.getEmail() + "/" + track.getUuid());
		assertThat(response.statusCode()).isEqualTo(200);
		track.setOwner(user.getEmail().toLowerCase());
		assertThat(response.getBody().as(Track.class)).isEqualTo(track);
	}
	
	@Test
	void updateWithOlderVersionDoNotUpdate() {
		var user = test.createUserAndLogin();
		var track = user.createTrack();
		
		var track2 = user.generateRandomTrack();
		track2.setUuid(track.getUuid());
		track2.setVersion(track.getVersion());
		track2.setCreatedAt(track.getCreatedAt());
		track2.setUpdatedAt(track.getUpdatedAt());
		track2.setOwner(user.getEmail().toLowerCase());
		var response = user.put("/api/track/v1", track2);
		assertThat(response.statusCode()).isEqualTo(200);

		assertThat(user.getTracks()).singleElement().isEqualTo(new UuidAndOwner(track.getUuid(), user.getEmail().toLowerCase()));
		
		response = user.get("/api/track/v1/" + user.getEmail() + "/" + track.getUuid());
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Track.class);
		assertThat(updated.getVersion()).isEqualTo(2);
		track2.setVersion(2);
		track2.setUpdatedAt(updated.getUpdatedAt());
		track2.setSizeUsed(updated.getSizeUsed());
		assertThat(updated).isEqualTo(track2);
		
		var track3 = user.generateRandomTrack();
		track3.setUuid(track.getUuid());
		track3.setVersion(track.getVersion());
		response = user.put("/api/track/v1", track3);
		assertThat(response.statusCode()).isEqualTo(200);

		assertThat(user.getTracks()).singleElement().isEqualTo(new UuidAndOwner(track.getUuid(), user.getEmail().toLowerCase()));
		
		response = user.get("/api/track/v1/" + user.getEmail() + "/" + track.getUuid());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(Track.class)).isEqualTo(track2);
	}
	
	@Test
	void updateWithSameValuesDoNotUpdate() {
		var user = test.createUserAndLogin();
		var track = user.createTrack();
		
		var track2 = user.generateRandomTrack();
		track2.setUuid(track.getUuid());
		track2.setS(track.getS());
		track2.setWp(track.getWp());
		track2.setVersion(track.getVersion());
		var response = user.put("/api/track/v1", track2);
		assertThat(response.statusCode()).isEqualTo(200);

		assertThat(user.getTracks()).singleElement().isEqualTo(new UuidAndOwner(track.getUuid(), user.getEmail().toLowerCase()));
		
		response = user.get("/api/track/v1/" + user.getEmail() + "/" + track.getUuid());
		assertThat(response.statusCode()).isEqualTo(200);
		track.setOwner(user.getEmail().toLowerCase());
		assertThat(response.getBody().as(Track.class)).isEqualTo(track);
	}
	
	@Test
	void createWithTooLargeDataFails() {
		var user = test.createUserAndLogin();
		var track = createTooLargeTrack(user.getEmail());
		var response = user.post("/api/track/v1", track);
		TestUtils.expectError(response, 400, "track-too-large");
	}
	
	@Test
	void updateWithTooLargeDataFails() {
		var user = test.createUserAndLogin();
		var track = user.createTrack();
		
		var track2 = createTooLargeTrack(user.getEmail());
		track2.setVersion(1);
		track2.setUuid(track.getUuid());
		
		var response = user.put("/api/track/v1", track2);
		TestUtils.expectError(response, 400, "track-too-large");
	}
	
	@Test
	void testQuotaTrackNumber() {
		var user = test.createUserAndLogin();
		var quotas = user.getAuth().getQuotas();
		quotas.setTracksMax(5);
		test.asAdmin().updateQuotas(user.getEmail(), quotas);
		
		var tracks = new LinkedList<Track>();
		tracks.add(user.createTrack());
		tracks.add(user.createTrack());
		tracks.add(user.createTrack());
		tracks.add(user.createTrack());
		assertThat(user.renewToken().getQuotas().getTracksUsed()).isEqualTo(4);
		tracks.add(user.createTrack());
		assertThat(user.renewToken().getQuotas().getTracksUsed()).isEqualTo(5);
		user.createTrack(user.generateRandomTrack(), 403, "quota-exceeded-tracks");
		
		user.deleteTracks(tracks.subList(0, 2));
		assertThat(user.renewToken().getQuotas().getTracksUsed()).isEqualTo(3);
		tracks.removeFirst();
		tracks.removeFirst();

		tracks.add(user.createTrack());
		tracks.add(user.createTrack());
		assertThat(user.renewToken().getQuotas().getTracksUsed()).isEqualTo(5);
		user.createTrack(user.generateRandomTrack(), 403, "quota-exceeded-tracks");
		
		user.deleteTracks(tracks);
		assertThat(user.renewToken().getQuotas().getTracksUsed()).isZero();
	}
	
	@Test
	void testQuotaTrackSize() {
		var user = test.createUserAndLogin();
		var quotas = user.getAuth().getQuotas();
		quotas.setTracksSizeMax(10000);
		test.asAdmin().updateQuotas(user.getEmail(), quotas);
		
		var track = user.createTrack(user.generateRandomTrack(new Random(), 1, 1, 1, 1, 1, 1), -1, null);
		var size = user.renewToken().getQuotas().getTracksSizeUsed();
		assertThat(size).isPositive().isEqualTo(track.getSizeUsed());

		user.createTrack(user.generateRandomTrack(new Random(), 20, 20, 100, 100, 1, 1), 403, "quota-exceeded-tracks-size");
		quotas = user.renewToken().getQuotas();
		assertThat(quotas.getTracksSizeUsed()).isEqualTo(size);
		assertThat(quotas.getTracksUsed()).isEqualTo(1);
		
		user.deleteTracks(track);
		quotas = user.renewToken().getQuotas();
		assertThat(quotas.getTracksSizeUsed()).isZero();
		assertThat(quotas.getTracksUsed()).isZero();
	}
	
	private Track createTooLargeTrack(String email) {
		var segments = new Segment[30];
		for (var i = 0; i < segments.length; ++i) {
			segments[i] = new Segment(new Point[1000]);
			for (var j = 0; j < segments[i].getP().length; ++j) {
				segments[i].getP()[j] = new Point(
					1L * i * j, i * j * i * 1L, (i + 10L) * j + 12, i * (j + 3) + 2L,
					i * j + 4L, (i + 10L) * j + 13, i * (j + 3) + 4L, i + j + 8L
				);
			}
		}
		var wayPoints = new WayPoint[10];
		for (var i = 0; i < wayPoints.length; ++i) {
			wayPoints[i] = new WayPoint(
				i * 2L, i + 20L, (i + 10) * i + 12L, i + 2L,
				RandomStringUtils.insecure().nextAlphanumeric(75), RandomStringUtils.insecure().nextAlphanumeric(50)
			);
		}
		return new Track(UUID.randomUUID().toString(), email, 0, 0, 0, segments, wayPoints, 0);
	}
	
}
