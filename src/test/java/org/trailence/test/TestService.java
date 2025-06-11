package org.trailence.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.trailence.auth.dto.AuthResponse;
import org.trailence.auth.dto.InitRenewRequest;
import org.trailence.auth.dto.InitRenewResponse;
import org.trailence.auth.dto.LoginRequest;
import org.trailence.auth.dto.RenewTokenRequest;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.dto.PageResult;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.UuidAndOwner;
import org.trailence.quotas.dto.Plan;
import org.trailence.quotas.dto.UserSubscription;
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
import org.trailence.user.dto.User;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.annotation.PostConstruct;
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
	private static TestAdminLoggedIn admin = null;
	
	@PostConstruct @SuppressWarnings("java:S125")
	public void init() {
		// RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
	}
	
	public TestUser createUser() {
		return createUser(false);
	}
	
	public TestUser createUser(boolean admin) {
		String email = email();
		String password = RandomStringUtils.insecure().nextAlphanumeric(8, 20);
		StepVerifier.create(
			userService.createUser(email, password, admin, List.of(Tuples.of(TrailenceUtils.FREE_PLAN, Optional.empty())))
		).verifyComplete();
		return new TestUser(email, password);
	}
	
	public String email() {
		do {
			String email = RandomStringUtils.insecure().nextAlphanumeric(3, 20) + '@' + RandomStringUtils.insecure().nextAlphanumeric(3, 10) + '.' + RandomStringUtils.insecure().nextAlphanumeric(2, 4);
			if (usedEmails.add(email.toLowerCase())) return email;
		} while (true);
	}
	
	public TestAdminLoggedIn asAdmin() {
		if (admin == null) {
			admin = new TestAdminLoggedIn(createUserAndLogin(true, null));
		}
		return admin;
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
		return createUserAndLogin(false, null);
	}
	
	public TestUserLoggedIn createUserAndLogin(boolean admin, Long keyExpiresAfter) {
		var user = createUser(admin);
		return login(user, keyExpiresAfter, new HashMap<String, Object>());
	}
	
	public TestUserLoggedIn login(TestUser user, Long keyExpiresAfter, Map<String, Object> deviceInfo) {
		var keyPair = generateKeyPair();
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), user.getPassword(), keyPair.getPublic().getEncoded(), keyExpiresAfter, deviceInfo, null))
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
		
		public List<TrailCollection> createCollections(int nbCollections, int expectedError, String expectedErrorCode) {
			var dtos = new TrailCollection[nbCollections];
			for (int i = 0; i < dtos.length; ++i) dtos[i] = generateRandomCollection();
			var response = post("/api/trail-collection/v1/_bulkCreate", dtos);
			if (expectedError > 0) {
				TestUtils.expectError(response, expectedError, expectedErrorCode);
				return null;
			}
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(TrailCollection[].class);
			assertThat(list).hasSizeLessThanOrEqualTo(nbCollections);
			for (var i = 0; i < list.length; ++i) {
				var col = list[i];
				for (var j = 0; j < list.length; ++j)
					if (j != i) assertThat(col.getUuid()).isNotEqualTo(list[j].getUuid());
				var dtoOpt = Arrays.stream(dtos).filter(d -> d.getUuid().equals(col.getUuid())).findAny();
				assertThat(dtoOpt).isPresent();
				var dto = dtoOpt.get();
				assertThat(col.getName()).isEqualTo(dto.getName());
				assertThat(col.getType()).isEqualTo(TrailCollectionType.CUSTOM);
				assertThat(col.getOwner()).isEqualTo(email.toLowerCase());
				assertThat(col.getVersion()).isEqualTo(1L);
			}
			return Arrays.asList(list);
		}
		
		public TrailCollection generateRandomCollection() {
			return new TrailCollection(UUID.randomUUID().toString(), email, 0, 0, 0, RandomStringUtils.insecure().nextAlphanumeric(3, 20), TrailCollectionType.CUSTOM);
		}
		
		public List<TrailCollection> createCollections(int nbCollections) {
			return createCollections(nbCollections, -1, null);
		}
		
		public TrailCollection createCollection() {
			var list = createCollections(1);
			assertThat(list).hasSize(1);
			return list.getFirst();
		}
		
		public List<TrailCollection> updateCollections(TrailCollection... collections) {
			return updateCollections(Arrays.asList(collections));
		}
		
		public List<TrailCollection> updateCollections(List<TrailCollection> collections) {
			var response = put("/api/trail-collection/v1/_bulkUpdate", collections);
			assertThat(response.statusCode()).isEqualTo(200);
			var updated = response.getBody().as(TrailCollection[].class);
			return Arrays.asList(updated);
		}
		
		public void deleteCollections(TrailCollection... collections) {
			deleteCollections(Arrays.asList(collections));
		}
		
		public void deleteCollections(List<TrailCollection> collections) {
			var response = post("/api/trail-collection/v1/_bulkDelete", collections.stream().map(TrailCollection::getUuid).toList());
			assertThat(response.statusCode()).isEqualTo(200);
		}
		
		public Track generateRandomTrack() {
			var random = new Random();
			return generateRandomTrack(random, 0, 10, 0, 100, 0, 10);
		}
		
		public Track generateRandomTrack(Random random, int minNbSegments, int maxNbSegments, int minPointsPerSegment, int maxPointsPerSegment, int minWayPoints, int maxWayPoints) {
			var segments = new Segment[random.nextInt(minNbSegments, maxNbSegments + 1)];
			for (var i = 0; i < segments.length; ++i) {
				segments[i] = new Segment(new Point[random.nextInt(minPointsPerSegment, maxPointsPerSegment + 1)]);
				for (var j = 0; j < segments[i].getP().length; ++j) {
					segments[i].getP()[j] = new Point(
						random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(),
						random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()
					);
				}
			}
			var wayPoints = new WayPoint[random.nextInt(minWayPoints, maxWayPoints + 1)];
			for (var i = 0; i < wayPoints.length; ++i) {
				wayPoints[i] = new WayPoint(
					random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong(),
					RandomStringUtils.insecure().nextAlphanumeric(0, 100), RandomStringUtils.insecure().nextAlphanumeric(0, 100)
				);
			}
			return new Track(UUID.randomUUID().toString(), email, 0, 0, 0, segments, wayPoints, 0);
		}
		
		public Track createTrack() {
			return createTrack(generateRandomTrack(), -1, null);
		}
		
		public Track createTrack(Track dto, int expectedStatus, String expectedErrorCode) {
			var response = post("/api/track/v1", dto);
			if (expectedStatus > 0) {
				TestUtils.expectError(response, expectedStatus, expectedErrorCode);
				return null;
			}
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
		
		public void deleteTracks(Track... tracks) {
			deleteTracks(Arrays.asList(tracks));
		}
		
		public void deleteTracks(List<Track> tracks) {
			var response = post("/api/track/v1/_bulkDelete", tracks.stream().map(Track::getUuid).toList());
			assertThat(response.statusCode()).isEqualTo(200);
		}
		
		public Trail createTrail(TrailCollection collection, boolean sameCurrentAndOriginalTracks) {
			return createTrails(collection, 1, sameCurrentAndOriginalTracks).getFirst();
		}
		
		public List<Trail> createTrails(TrailCollection collection, int nbTrails, boolean sameCurrentAndOriginalTracks) {
			var trails = new LinkedList<Trail>();
			for (int i = 0; i < nbTrails; ++i) {
				var track1 = createTrack();
				var track2 = sameCurrentAndOriginalTracks ? track1 : createTrack();
				var trail = new Trail(
					UUID.randomUUID().toString(), email, 0, 0, 0,
					RandomStringUtils.insecure().nextAlphanumeric(0, 201),
					RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
					RandomStringUtils.insecure().nextAlphanumeric(0, 101),
					RandomStringUtils.insecure().nextAlphanumeric(0, 3),
					RandomStringUtils.insecure().nextAlphanumeric(0, 21),
					null, null, null,
					track1.getUuid(),
					track2.getUuid(),
					collection.getUuid()
				);
				trails.add(trail);
			}
			var response = post("/api/trail/v1/_bulkCreate", trails);
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(Trail[].class);
			assertThat(list).hasSize(nbTrails);
			for (int i = 0; i < nbTrails; ++i) {
				var created = list[i];
				for (int j = 0; j < nbTrails; ++j) if (j != i) assertThat(list[j].getUuid()).isNotEqualTo(created.getUuid());
				var trailOpt = trails.stream().filter(t -> t.getUuid().equals(created.getUuid())).findAny();
				assertThat(trailOpt).isPresent();
				var trail = trailOpt.get();
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
			}
			return Arrays.asList(list);
		}
		
		public void deleteTrails(Trail... trails) {
			var response = post("/api/trail/v1/_bulkDelete", Stream.of(trails).map(Trail::getUuid).toList());
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
		
		public List<Trail> updateTrails(List<Trail> trails) {
			var response = put("/api/trail/v1/_bulkUpdate", trails);
			assertThat(response.statusCode()).isEqualTo(200);
			var updated = response.getBody().as(Trail[].class);
			return Arrays.asList(updated);
		}
		
		public List<Trail> updateTrails(Trail... trails) {
			return updateTrails(Arrays.asList(trails));
		}
		
		public Tag createTag(TrailCollection collection, Tag parent) {
			return createTags(collection, parent).getFirst();
		}
		
		public List<Tag> createTags(TrailCollection collection, Object... parents) {
			var dtos = new LinkedList<Tag>();
			for (Object parent : parents) {
				Tag parentTag = null;
				if (parent instanceof Tag t) parentTag = t;
				else if (parent instanceof Integer i) parentTag = dtos.get(i);
				var dto = new Tag(
					UUID.randomUUID().toString(),
					email,
					0, 0, 0,
					RandomStringUtils.insecure().nextAlphanumeric(0, 51),
					parentTag == null ? null : parentTag.getUuid(),
					collection.getUuid()
				);
				dtos.add(dto);
			}
			var response = post("/api/tag/v1/_bulkCreate", dtos);
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(Tag[].class);
			assertThat(list).hasSize(parents.length);
			Tag[] result = new Tag[parents.length];
			for (int i = 0; i < parents.length; ++i) {
				var created = list[i];
				for (int j = 0; j < parents.length; ++j) if (j != i) assertThat(list[j].getUuid()).isNotEqualTo(created.getUuid());
				var dtoOpt = dtos.stream().filter(d -> d.getUuid().equals(created.getUuid())).findAny();
				assertThat(dtoOpt).isPresent();
				var dto = dtoOpt.get();
				result[dtos.indexOf(dto)] = created;
				assertThat(created.getUuid()).isEqualTo(dto.getUuid());
				assertThat(created.getOwner()).isEqualTo(email.toLowerCase());
				assertThat(created.getVersion()).isEqualTo(1L);
				assertThat(created.getName()).isEqualTo(dto.getName());
				assertThat(created.getParentUuid()).isEqualTo(dto.getParentUuid());
				assertThat(created.getCollectionUuid()).isEqualTo(dto.getCollectionUuid());
			}
			return Arrays.asList(result);
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
		
		public List<Tag> updateTags(List<Tag> tags) {
			var response = put("/api/tag/v1/_bulkUpdate", tags);
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(Tag[].class);
			return Arrays.asList(list);
		}
		
		public List<Tag> updateTags(Tag... tags) {
			return updateTags(Arrays.asList(tags));
		}
		
		public void deleteTags(List<Tag> tags) {
			var response = post("/api/tag/v1/_bulkDelete", tags.stream().map(Tag::getUuid).toList());
			assertThat(response.statusCode()).isEqualTo(200);
		}
		
		public void deleteTags(Tag... tags) {
			deleteTags(Arrays.asList(tags));
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
			return createPhoto(trail, 123456, 123456, -1, null);
		}

		public Tuple2<Photo, byte[]> createPhoto(Trail trail, int minFileSize, int maxFileSize, int expectedStatus, String expectedErrorCode) {
			var uuid = UUID.randomUUID().toString();
			var random = new Random();
			var content = new byte[random.nextInt(minFileSize, maxFileSize + 1)];
			random.nextBytes(content);
			var response = request()
				.contentType(ContentType.BINARY)
				.header("X-Description", "test")
				.header("X-DateTaken", "123456789")
				.header("X-Latitude", "147")
				.header("X-Longitude", "369852")
				.header("X-Index", "12")
				.body(content)
				.post("/api/photo/v1/" + trail.getUuid() + "/" + uuid);
			if (expectedStatus > 0) {
				TestUtils.expectError(response, expectedStatus, expectedErrorCode);
				return null;
			}
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
		
		public long deletePhotos(Photo... photos) {
			return deletePhotos(Arrays.asList(photos));
		}
		
		public long deletePhotos(List<Photo> photos) {
			var response = post("/api/photo/v1/_bulkDelete", photos.stream().map(Photo::getUuid).toList());
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(Long.class);
		}

		public List<Share> getShares() {
			var response = get("/api/share/v2");
			assertThat(response.statusCode()).isEqualTo(200);
			return Arrays.asList(response.getBody().as(Share[].class));
		}
		
		public AuthResponse renewToken() {
			var response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new InitRenewRequest(auth.getEmail(), auth.getKeyId()))
				.post("/api/auth/v1/init_renew");
			assertThat(response.statusCode()).isEqualTo(200);
			var initRenew = response.getBody().as(InitRenewResponse.class);
			assertThat(initRenew.getRandom()).isNotNull();
			
			byte[] signature;
			try {
				Signature signer = Signature.getInstance("SHA256withRSA");
				signer.initSign(keyPair.getPrivate());
				signer.update((auth.getEmail() + initRenew.getRandom()).getBytes(StandardCharsets.UTF_8));
				signature = signer.sign();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			
			response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
				.post("/api/auth/v1/renew");
			assertThat(response.statusCode()).isEqualTo(200);
			var authRenew = response.getBody().as(AuthResponse.class);
			assertThat(authRenew.getAccessToken()).isNotNull();
			assertThat(authRenew.getEmail()).isEqualTo(getEmail().toLowerCase());
			assertThat(authRenew.getPreferences()).isNotNull();
			assertThat(authRenew.getQuotas()).isNotNull();
			assertThat(authRenew.getKeyId()).isEqualTo(auth.getKeyId());
			this.auth = authRenew;
			return authRenew;
		}
		
	}
	
	public static class TestAdminLoggedIn extends TestUserLoggedIn {
		private TestAdminLoggedIn(TestUserLoggedIn admin) {
			super(admin.getEmail(), admin.getPassword(), admin.getKeyPair(), admin.getAuth());
		}
		
		public Plan createPlan(Plan plan) {
			var response = post("/api/admin/plans/v1", plan);
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(Plan.class);
		}
		
		public UserSubscription addPlanToUser(String email, String plan) {
			var response = post("/api/admin/users/v1/" + email + "/subscriptions", plan);
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(UserSubscription.class);
		}
		
		public UserSubscription stopUserSubscription(String email, UUID subscription) {
			var response = delete("/api/admin/users/v1/" + email + "/subscriptions/" + subscription);
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(UserSubscription.class);
		}
		
		public void setUserRoles(String email, List<String> roles) {
			var response = put("/api/admin/users/v1/" + email + "/roles", roles);
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.getBody().as(String[].class)).isEqualTo(roles.toArray(new String[roles.size()]));
		}
		
		public PageResult<User> listUsers() {
			var response = get("/api/admin/users/v1?page=0&size=1000");
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(new TypeRef<PageResult<User>>() {});
		}
	}
	
}
