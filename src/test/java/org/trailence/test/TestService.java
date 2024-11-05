package org.trailence.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.trailence.auth.dto.AuthResponse;
import org.trailence.auth.dto.LoginRequest;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.UuidAndOwner;
import org.trailence.trail.dto.Photo;
import org.trailence.trail.dto.Share;
import org.trailence.trail.dto.Tag;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Track.Point;
import org.trailence.trail.dto.Track.Segment;
import org.trailence.trail.dto.Track.WayPoint;
import org.trailence.trail.dto.Trail;
import org.trailence.trail.dto.TrailCollection;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.trail.dto.TrailTag;
import org.trailence.user.UserService;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class TestService {

	private final UserService userService;
	
	private static Set<String> usedEmails = new HashSet<>();
	
	public TestUser createUser() {
		String email = email();
		String password = RandomStringUtils.randomAlphanumeric(8, 20);
		StepVerifier.create(
			userService.createUser(email, password)
		).verifyComplete();
		return new TestUser(email, password);
	}
	
	public String email() {
		do {
			String email = RandomStringUtils.randomAlphanumeric(3, 20) + '@' + RandomStringUtils.randomAlphanumeric(3, 10) + '.' + RandomStringUtils.randomAlphanumeric(2, 4);
			if (usedEmails.add(email.toLowerCase())) return email;
		} while (true);
	}
	
	@AllArgsConstructor
	@Data
	public static class TestUser {
		private String email;
		private String password;
	}
	
	public KeyPair generateKeyPair() {
		try {
			return KeyPairGenerator.getInstance("RSA").generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
	
	public TestUserLoggedIn createUserAndLogin() {
		var user = createUser();
		var keyPair = generateKeyPair();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
		var auth = response.getBody().as(AuthResponse.class);
		return new TestUserLoggedIn(user.getEmail(), user.getPassword(), keyPair, auth);
	}
	
	@AllArgsConstructor
	@Data
	public static class TestUserLoggedIn {
		private String email;
		private String password;
		private KeyPair keyPair;
		private AuthResponse auth;
		
		public RequestSpecification request() {
			return RestAssured.given().header("Authorization", "Bearer " + auth.getAccessToken());
		}
		
		public Response get(String path, Object... pathParams) {
			return request().get(path, pathParams);
		}
		
		public Response post(String path, Object body) {
			return request().contentType(ContentType.JSON).body(body).post(path);
		}
		
		public Response put(String path, Object body) {
			return request().contentType(ContentType.JSON).body(body).put(path);
		}
		
		public Response delete(String path, Object... pathParams) {
			return request().delete(path, pathParams);
		}
		
		public TrailCollection getMyTrails() {
			return getCollections().stream().filter(c -> c.getType().equals(TrailCollectionType.MY_TRAILS)).findAny().get();
		}
		
		public List<TrailCollection> getCollections() {
			var response = post("/api/trail-collection/v1/_bulkGetUpdates", List.of());
			var updates = response.getBody().as(new TypeRef<UpdateResponse<TrailCollection>>() {});
			return updates.getCreated();
		}
		
		public TrailCollection createCollection() {
			var dto = new TrailCollection(UUID.randomUUID().toString(), email, 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.CUSTOM);
			var response = post("/api/trail-collection/v1/_bulkCreate", List.of(dto));
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(TrailCollection[].class);
			assertThat(list).hasSize(1);
			var col = list[0];
			assertThat(col.getUuid()).isEqualTo(dto.getUuid());
			assertThat(col.getName()).isEqualTo(dto.getName());
			assertThat(col.getType()).isEqualTo(TrailCollectionType.CUSTOM);
			assertThat(col.getOwner()).isEqualTo(email.toLowerCase());
			assertThat(col.getVersion()).isEqualTo(1L);
			return col;
		}
		
		public void deleteCollections(TrailCollection... collections) {
			var response = post("/api/trail-collection/v1/_bulkDelete", Stream.of(collections).map(col -> col.getUuid()).toList());
			assertThat(response.statusCode()).isEqualTo(200);
		}
		
		public Track generateRandomTrack() {
			var random = new Random();
			var segments = new Segment[random.nextInt(10)];
			for (var i = 0; i < segments.length; ++i) {
				segments[i] = new Segment(new Point[random.nextInt(100)]);
				for (var j = 0; j < segments[i].getP().length; ++j) {
					segments[i].getP()[j] = new Point(
						random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(),
						random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()
					);
				}
			}
			var wayPoints = new WayPoint[random.nextInt(10)];
			for (var i = 0; i < wayPoints.length; ++i) {
				wayPoints[i] = new WayPoint(
					random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(),
					RandomStringUtils.randomAlphanumeric(0, 100), RandomStringUtils.randomAlphanumeric(0, 100)
				);
			}
			return new Track(UUID.randomUUID().toString(), email, 0, 0, 0, segments, wayPoints);
		}
		
		public Track createTrack() {
			var dto = generateRandomTrack();
			var response = post("/api/track/v1", dto);
			assertThat(response.statusCode()).isEqualTo(200);
			var track = response.getBody().as(Track.class);
			assertThat(track.getUuid()).isEqualTo(dto.getUuid());
			assertThat(track.getOwner()).isEqualTo(email.toLowerCase());
			assertThat(track.getVersion()).isEqualTo(1L);
			assertThat(track.getS()).isEqualTo(dto.getS());
			assertThat(track.getWp()).isEqualTo(dto.getWp());
			return track;
		}
		
		public List<UuidAndOwner> getTracks() {
			var response = post("/api/track/v1/_bulkGetUpdates", List.of());
			var updates = response.getBody().as(new TypeRef<UpdateResponse<UuidAndOwner>>() {});
			return updates.getCreated();
		}
		
		public void expectTracks(Track... tracks) {
			expectTracks(Arrays.asList(tracks));
		}
		
		public void expectTracks(List<Track> tracks) {
			var myTracks = getTracks();
			assertThat(myTracks).hasSameSizeAs(tracks);
			for (var expected : tracks) {
				assertThat(myTracks).as(expected.toString()).satisfiesOnlyOnce(t -> {
					assertThat(t.getUuid()).isEqualTo(expected.getUuid());
					assertThat(t.getOwner()).isEqualTo(email.toLowerCase());
				});
				var trackResponse = get("/api/track/v1/" + email + "/" + expected.getUuid());
				assertThat(trackResponse.statusCode()).isEqualTo(200);
				var track = trackResponse.getBody().as(Track.class);
				assertThat(track).isEqualTo(expected);
			}
		}
		
		public void expectTracksIds(List<UuidAndOwner> tracks) {
			assertThat(getTracks()).containsExactlyInAnyOrderElementsOf(tracks);
		}
		
		public Trail createTrail(TrailCollection collection, boolean sameCurrentAndOriginalTracks) {
			var track1 = createTrack();
			var track2 = sameCurrentAndOriginalTracks ? track1 : createTrack();
			var trail = new Trail(
				UUID.randomUUID().toString(), email, 0, 0, 0,
				RandomStringUtils.randomAlphanumeric(0, 201),
				RandomStringUtils.randomAlphanumeric(0, 50001),
				RandomStringUtils.randomAlphanumeric(0, 101),
				RandomStringUtils.randomAlphanumeric(0, 3),
				track1.getUuid(),
				track2.getUuid(),
				collection.getUuid()
			);
			var response = post("/api/trail/v1/_bulkCreate", List.of(trail));
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(Trail[].class);
			assertThat(list).hasSize(1);
			var created = list[0];
			assertThat(created.getUuid()).isEqualTo(trail.getUuid());
			assertThat(created.getOwner()).isEqualTo(email.toLowerCase());
			assertThat(created.getVersion()).isEqualTo(1L);
			assertThat(created.getName()).isEqualTo(trail.getName());
			assertThat(created.getDescription()).isEqualTo(trail.getDescription());
			assertThat(created.getLocation()).isEqualTo(trail.getLocation());
			assertThat(created.getLoopType()).isEqualTo(trail.getLoopType());
			assertThat(created.getOriginalTrackUuid()).isEqualTo(trail.getOriginalTrackUuid());
			assertThat(created.getCurrentTrackUuid()).isEqualTo(trail.getCurrentTrackUuid());
			assertThat(created.getCollectionUuid()).isEqualTo(trail.getCollectionUuid());
			return created;
		}
		
		public void deleteTrails(Trail... trails) {
			var response = post("/api/trail/v1/_bulkDelete", Stream.of(trails).map(trail -> trail.getUuid()).toList());
			assertThat(response.statusCode()).isEqualTo(200);
		}
		
		public List<Trail> getTrails() {
			var response = post("/api/trail/v1/_bulkGetUpdates", List.of());
			assertThat(response.statusCode()).isEqualTo(200);
			var updates = response.getBody().as(new TypeRef<UpdateResponse<Trail>>() {});
			return updates.getCreated();
		}
		
		public void expectTrails(Trail... trails) {
			var myTrails = getTrails();
			assertThat(myTrails).hasSize(trails.length);
			for (var expected : trails) {
				assertThat(myTrails).as(expected.toString()).satisfiesOnlyOnce(t -> {
					assertThat(t.getUuid()).isEqualTo(expected.getUuid());
					assertThat(t.getOwner()).isEqualTo(expected.getOwner());
				});
			}
		}
		
		public Tag createTag(TrailCollection collection, Tag parent) {
			var dto = new Tag(
				UUID.randomUUID().toString(),
				email,
				0, 0, 0,
				RandomStringUtils.randomAlphanumeric(0, 51),
				parent == null ? null : parent.getUuid(),
				collection.getUuid()
			);
			var response = post("/api/tag/v1/_bulkCreate", List.of(dto));
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(Tag[].class);
			assertThat(list).hasSize(1);
			var created = list[0];
			assertThat(created.getUuid()).isEqualTo(dto.getUuid());
			assertThat(created.getOwner()).isEqualTo(email.toLowerCase());
			assertThat(created.getVersion()).isEqualTo(1L);
			assertThat(created.getName()).isEqualTo(dto.getName());
			assertThat(created.getParentUuid()).isEqualTo(dto.getParentUuid());
			assertThat(created.getCollectionUuid()).isEqualTo(dto.getCollectionUuid());
			return created;
		}
		
		public List<Tag> getTags() {
			var response = post("/api/tag/v1/_bulkGetUpdates", List.of());
			assertThat(response.statusCode()).isEqualTo(200);
			var updates = response.getBody().as(new TypeRef<UpdateResponse<Tag>>() {});
			return updates.getCreated();
		}
		
		public void expectTags(Tag... expectedTags) {
			var tags = getTags();
			assertThat(tags).hasSize(expectedTags.length);
			for (var expected : expectedTags) {
				assertThat(tags).as(expected.toString()).satisfiesOnlyOnce(t -> {
					assertThat(t.getUuid()).isEqualTo(expected.getUuid());
					assertThat(t.getOwner()).isEqualTo(email.toLowerCase());
					assertThat(t.getParentUuid()).isEqualTo(expected.getParentUuid());
					assertThat(t.getCollectionUuid()).isEqualTo(expected.getCollectionUuid());
				});
			}
		}
		
		public TrailTag createTrailTag(Trail trail, Tag tag) {
			var dto = new TrailTag(tag.getUuid(), trail.getUuid(), 0);
			var response = post("/api/tag/v1/trails/_bulkCreate", List.of(dto));
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(TrailTag[].class);
			assertThat(list).hasSize(1);
			var result = list[0];
			assertThat(result.getTagUuid()).isEqualTo(tag.getUuid());
			assertThat(result.getTrailUuid()).isEqualTo(trail.getUuid());
			return result;
		}
		
		public List<TrailTag> getTrailTags() {
			var response = get("/api/tag/v1/trails");
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(new TypeRef<List<TrailTag>>() {});
		}

		public Tuple2<Photo, byte[]> createPhoto(Trail trail) {
			var uuid = UUID.randomUUID().toString();
			var content = new byte[123456];
			new Random().nextBytes(content);
			var response = request()
				.contentType(ContentType.BINARY)
				.header("X-Description", "test")
				.header("X-DateTaken", "123456789")
				.header("X-Latitude", "147")
				.header("X-Longitude", "369852")
				.header("X-Index", "12")
				.body(content)
				.post("/api/photo/v1/" + trail.getUuid() + "/" + uuid);
			assertThat(response.statusCode()).isEqualTo(200);
			var photo = response.getBody().as(Photo.class);
			assertThat(photo.getUuid()).isEqualTo(uuid);
			assertThat(photo.getOwner()).isEqualTo(email.toLowerCase());
			assertThat(photo.getVersion()).isEqualTo(1);
			assertThat(photo.getDescription()).isEqualTo("test");
			assertThat(photo.getTrailUuid()).isEqualTo(trail.getUuid());
			assertThat(photo.getLatitude()).isEqualTo(147);
			assertThat(photo.getLongitude()).isEqualTo(369852);
			assertThat(photo.getIndex()).isEqualTo(12);
			assertThat(photo.getDateTaken()).isEqualTo(123456789);
			return Tuples.of(photo, content);
		}
		
		public List<Photo> getPhotos() {
			var response = post("/api/photo/v1/_bulkGetUpdates", List.of());
			assertThat(response.statusCode()).isEqualTo(200);
			var updates = response.getBody().as(new TypeRef<UpdateResponse<Photo>>() {});
			return updates.getCreated();
		}

		public List<Share> getShares() {
			var response = get("/api/share/v1");
			assertThat(response.statusCode()).isEqualTo(200);
			return Arrays.asList(response.getBody().as(Share[].class));
		}
		
	}
	
}
