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
import org.trailence.global.rest.AuthDetails;
import org.trailence.quotas.db.UserQuotasEntity;
import org.trailence.quotas.db.UserQuotasRepository;
import org.trailence.quotas.dto.Plan;
import org.trailence.quotas.dto.UserSubscription;
import org.trailence.trail.SharedCollectionUtils;
import org.trailence.trail.dto.CreatePublicLinkRequest;
import org.trailence.trail.dto.MyTrailLink;
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
import org.trailence.trail.dto.TrailLinkContent;
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
	private final UserQuotasRepository quotaRepo;
	
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
		return new TestUserLoggedIn(user.getEmail(), user.getPassword(), keyPair, auth, response.getCookie("trailence_token"), TestUserLoggedIn.DEFAULT_CLIENT_VERSION);
	}
	
	public UserQuotasEntity getQuotas(String email) {
		return quotaRepo.findById(email.toLowerCase()).block();
	}
	
	public UserQuotasEntity getQuotas(TestUserLoggedIn user) {
		return getQuotas(user.getEmail());
	}
	
	@AllArgsConstructor
	@Data
	public static class TestUserLoggedIn {
		private String email;
		private String password;
		private KeyPair keyPair;
		private AuthResponse auth;
		private String trustToken;
		private int clientVersion;
		public static final int DEFAULT_CLIENT_VERSION = 20300;
		public static final int OLD_CLIENT_VERSION = 20101;
		
		public TestUserLoggedIn usingOldClient() {
			this.clientVersion = OLD_CLIENT_VERSION;
			return this;
		}

		public TestUserLoggedIn usingRecentClient() {
			this.clientVersion = DEFAULT_CLIENT_VERSION;
			return this;
		}
		
		public RequestSpecification request() {
			var req = RestAssured.given().header("Authorization", "Bearer " + auth.getAccessToken());
			if (clientVersion > AuthDetails.MIN_VERSION) req = req.header(AuthDetails.HEADER_VERSION, Integer.toString(clientVersion));
			return req;
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
		
		public List<TrailCollection> createCollections(TrailCollection[] dtos, int expectedError, String expectedErrorCode) {
			var response = post("/api/trail-collection/v1/_bulkCreate", dtos);
			if (expectedError > 0) {
				TestUtils.expectError(response, expectedError, expectedErrorCode);
				return null;
			}
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(TrailCollection[].class);
			assertThat(list).hasSizeLessThanOrEqualTo(dtos.length);
			for (var i = 0; i < list.length; ++i) {
				var col = list[i];
				for (var j = 0; j < list.length; ++j)
					if (j != i) assertThat(col.getUuid()).isNotEqualTo(list[j].getUuid());
				var dtoOpt = Arrays.stream(dtos).filter(d -> d.getUuid().equals(col.getUuid())).findAny();
				assertThat(dtoOpt).isPresent();
				var dto = dtoOpt.get();
				assertThat(col.getName()).isEqualTo(dto.getName());
				assertThat(col.getType()).isEqualTo(dto.getType());
				assertThat(col.getOwner()).isEqualTo(email.toLowerCase());
				assertThat(col.getVersion()).isEqualTo(1L);
			}
			return Arrays.asList(list);
		}
		
		public List<TrailCollection> createCollections(TrailCollection[] dtos) {
			return createCollections(dtos, -1, null);
		}
		
		public List<TrailCollection> createCollections(int nbCollections, int expectedError, String expectedErrorCode) {
			var dtos = new TrailCollection[nbCollections];
			for (int i = 0; i < dtos.length; ++i) dtos[i] = generateRandomCollection();
			return createCollections(dtos, expectedError, expectedErrorCode);
		}
		
		public TrailCollection generateRandomCollection() {
			return new TrailCollection(UUID.randomUUID().toString(), email, 0, 0, 0, RandomStringUtils.insecure().nextAlphanumeric(3, 20), TrailCollectionType.CUSTOM, null, null);
		}
		
		public List<TrailCollection> createCollections(int nbCollections) {
			return createCollections(nbCollections, -1, null);
		}
		
		public TrailCollection createCollection() {
			var list = createCollections(1);
			assertThat(list).hasSize(1);
			return list.getFirst();
		}
		
		public TrailCollection createSharedCollection(String... friends) {
			var list = createCollections(new TrailCollection[] {
				new TrailCollection(UUID.randomUUID().toString(), email, 0, 0, 0, RandomStringUtils.insecure().nextAlphanumeric(3, 20), TrailCollectionType.SHARED, List.of(friends), null)
			});
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
		
		public Track generateRandomTrack(TrailCollection collection) {
			var random = new Random();
			return generateRandomTrack(collection, random, 0, 10, 0, 100, 0, 10);
		}
		
		public Track generateRandomTrack(TrailCollection collection, Random random, int minNbSegments, int maxNbSegments, int minPointsPerSegment, int maxPointsPerSegment, int minWayPoints, int maxWayPoints) {
			var segments = new Segment[random.nextInt(minNbSegments, maxNbSegments + 1)];
			for (var i = 0; i < segments.length; ++i) {
				segments[i] = new Segment(new Point[random.nextInt(minPointsPerSegment, maxPointsPerSegment + 1)]);
				for (var j = 0; j < segments[i].getP().length; ++j) {
					segments[i].getP()[j] = new Point(
						random.nextLong(-900000000, 900000001),
						random.nextLong(-1800000000, 1800000001),
						random.nextLong(-10000, 10000),
						random.nextLong(-0x3000000000000000L, 0x3000000000000000L),
						random.nextLong(0, 1000000),
						random.nextLong(0, 1000000),
						null,
						null
					);
				}
			}
			var wayPoints = new WayPoint[random.nextInt(minWayPoints, maxWayPoints + 1)];
			for (var i = 0; i < wayPoints.length; ++i) {
				wayPoints[i] = new WayPoint(
					random.nextLong(-900000000, 900000001),
					random.nextLong(-1800000000, 1800000001),
					random.nextLong(-10000, 10000),
					random.nextLong(-0x3000000000000000L, 0x3000000000000000L),
					RandomStringUtils.insecure().nextAlphanumeric(0, 100), RandomStringUtils.insecure().nextAlphanumeric(0, 100),
					null, null
				);
			}
			return new Track(
				UUID.randomUUID().toString(),
				TrailCollectionType.SHARED.equals(collection.getType()) ? SharedCollectionUtils.SHARED_OWNER_PREFIX + collection.getUuid() : email,
				0, 0, 0,
				segments, wayPoints, 0);
		}
		
		public Track createTrack(TrailCollection collection) {
			return createTrack(generateRandomTrack(collection), -1, null);
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
			assertThat(track.getOwner()).isEqualTo(SharedCollectionUtils.isSharedCollectionOwner(dto.getOwner()) ? dto.getOwner() : email.toLowerCase());
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
		
		public Track getTrack(String owner, String uuid) {
			var response = get("/api/track/v1/" + owner + "/" + uuid);
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(Track.class);
		}
		
		public void deleteTracks(Track... tracks) {
			deleteTracks(Arrays.asList(tracks));
		}
		
		public void deleteTracks(List<Track> tracks) {
			String shareId = !tracks.isEmpty() && SharedCollectionUtils.isSharedCollectionOwner(tracks.getFirst().getOwner()) ? "/" + tracks.getFirst().getOwner() : "";
			var response = post("/api/track/v1/_bulkDelete" + shareId, tracks.stream().map(Track::getUuid).toList());
			assertThat(response.statusCode()).isEqualTo(200);
		}
		
		public Trail createTrail(TrailCollection collection, boolean sameCurrentAndOriginalTracks) {
			return createTrails(collection, 1, sameCurrentAndOriginalTracks).getFirst();
		}
		
		public List<Trail> createTrails(TrailCollection collection, int nbTrails, boolean sameCurrentAndOriginalTracks) {
			var trails = new LinkedList<Trail>();
			for (int i = 0; i < nbTrails; ++i) {
				var track1 = createTrack(collection);
				var track2 = sameCurrentAndOriginalTracks ? track1 : createTrack(collection);
				var trail = new Trail(
					UUID.randomUUID().toString(),
					TrailCollectionType.SHARED.equals(collection.getType()) ? SharedCollectionUtils.SHARED_OWNER_PREFIX + collection.getUuid() : email,
					0, 0, 0,
					RandomStringUtils.insecure().nextAlphanumeric(0, 201),
					RandomStringUtils.insecure().nextAlphanumeric(0, 50001),
					RandomStringUtils.insecure().nextAlphanumeric(0, 101),
					null,
					RandomStringUtils.insecure().nextAlphanumeric(0, 3),
					RandomStringUtils.insecure().nextAlphanumeric(0, 21),
					null, null, null, null, null, null, null,
					track1.getUuid(),
					track2.getUuid(),
					collection.getUuid(),
					null, null, null, null
				);
				trails.add(trail);
			}
			var response = post("/api/trail/v1/_bulkCreate", trails);
			assertThat(response.statusCode()).isEqualTo(200);
			var list = response.getBody().as(Trail[].class);
			assertThat(list).hasSize(nbTrails);
			String expectedOwner = TrailCollectionType.SHARED.equals(collection.getType()) ? SharedCollectionUtils.SHARED_OWNER_PREFIX + collection.getUuid() : email.toLowerCase();
			for (int i = 0; i < nbTrails; ++i) {
				var created = list[i];
				for (int j = 0; j < nbTrails; ++j) if (j != i) assertThat(list[j].getUuid()).isNotEqualTo(created.getUuid());
				var trailOpt = trails.stream().filter(t -> t.getUuid().equals(created.getUuid())).findAny();
				assertThat(trailOpt).isPresent();
				var trail = trailOpt.get();
				assertThat(created.getUuid()).isEqualTo(trail.getUuid());
				assertThat(created.getOwner()).isEqualTo(expectedOwner);
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
			String shareId = trails.length > 0 && SharedCollectionUtils.isSharedCollectionOwner(trails[0].getOwner()) ? "/" + trails[0].getOwner() : ""; 
			var response = post("/api/trail/v1/_bulkDelete" + shareId, Stream.of(trails).map(Trail::getUuid).toList());
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
					TrailCollectionType.SHARED.equals(collection.getType()) ? SharedCollectionUtils.SHARED_OWNER_PREFIX + collection.getUuid() : email,
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
			String expectedOwner = TrailCollectionType.SHARED.equals(collection.getType()) ? SharedCollectionUtils.SHARED_OWNER_PREFIX + collection.getUuid() : email.toLowerCase();
			Tag[] result = new Tag[parents.length];
			for (int i = 0; i < parents.length; ++i) {
				var created = list[i];
				for (int j = 0; j < parents.length; ++j) if (j != i) assertThat(list[j].getUuid()).isNotEqualTo(created.getUuid());
				var dtoOpt = dtos.stream().filter(d -> d.getUuid().equals(created.getUuid())).findAny();
				assertThat(dtoOpt).isPresent();
				var dto = dtoOpt.get();
				result[dtos.indexOf(dto)] = created;
				assertThat(created.getUuid()).isEqualTo(dto.getUuid());
				assertThat(created.getOwner()).isEqualTo(expectedOwner);
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
			String shareId = !tags.isEmpty() && SharedCollectionUtils.isSharedCollectionOwner(tags.getFirst().getOwner()) ? "/" + tags.getFirst().getOwner() : "";
			var response = post("/api/tag/v1/_bulkDelete" + shareId, tags.stream().map(Tag::getUuid).toList());
			assertThat(response.statusCode()).isEqualTo(200);
		}
		
		public void deleteTags(Tag... tags) {
			deleteTags(Arrays.asList(tags));
		}
		
		public TrailTag createTrailTag(Trail trail, Tag tag) {
			var dto = new TrailTag(trail.getOwner(), tag.getUuid(), trail.getUuid(), 0);
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
		
		public void deleteTrailTags(TrailTag... tags) {
			var response = post("/api/tag/v1/trails/_bulkDelete", tags);
			assertThat(response.statusCode()).isEqualTo(200);
		}

		public Tuple2<Photo, byte[]> createPhoto(Trail trail) {
			return createPhoto(trail, 123456, 123456, -1, null);
		}

		public Tuple2<Photo, byte[]> createPhoto(Trail trail, int minFileSize, int maxFileSize, int expectedStatus, String expectedErrorCode) {
			var uuid = UUID.randomUUID().toString();
			var random = new Random();
			var content = new byte[random.nextInt(minFileSize, maxFileSize + 1)];
			var path = "/api/photo/v1/" + trail.getUuid() + "/" + uuid;
			if (clientVersion >= SharedCollectionUtils.MIN_VERSION_FOR_SHARED) {
				path += "/" + trail.getOwner();
			}
			random.nextBytes(content);
			var response = request()
				.contentType(ContentType.BINARY)
				.header("X-Description", "test")
				.header("X-DateTaken", "123456789")
				.header("X-Latitude", "147")
				.header("X-Longitude", "369852")
				.header("X-Index", "12")
				.body(content)
				.post(path);
			if (expectedStatus > 0) {
				TestUtils.expectError(response, expectedStatus, expectedErrorCode);
				return null;
			}
			assertThat(response.statusCode()).isEqualTo(200);
			var photo = response.getBody().as(Photo.class);
			assertThat(photo.getUuid()).isEqualTo(uuid);
			String expectedOwner = clientVersion >= SharedCollectionUtils.MIN_VERSION_FOR_SHARED && SharedCollectionUtils.isSharedCollectionOwner(trail.getOwner()) ? trail.getOwner() : email.toLowerCase();
			assertThat(photo.getOwner()).isEqualTo(expectedOwner);
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
		
		public List<Photo> updatePhotos(Photo... photos) {
			return updatePhotos(Arrays.asList(photos));
		}
		
		public List<Photo> updatePhotos(List<Photo> photos) {
			var response = put("/api/photo/v1/_bulkUpdate", photos);
			assertThat(response.statusCode()).isEqualTo(200);
			return Arrays.asList(response.getBody().as(Photo[].class));
		}
		
		public long deletePhotos(Photo... photos) {
			return deletePhotos(Arrays.asList(photos));
		}
		
		public long deletePhotos(List<Photo> photos) {
			String shareId = !photos.isEmpty() && SharedCollectionUtils.isSharedCollectionOwner(photos.getFirst().getOwner()) ? "/" + photos.getFirst().getOwner() : "";
			var response = post("/api/photo/v1/_bulkDelete" + shareId, photos.stream().map(Photo::getUuid).toList());
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(Long.class);
		}

		public List<Share> getShares() {
			var response = get("/api/share/v2");
			assertThat(response.statusCode()).isEqualTo(200);
			return Arrays.asList(response.getBody().as(Share[].class));
		}
		
		public MyTrailLink createPublicLink(Trail trail) {
			Response response;
			if (clientVersion < 20300)
				response = post("/api/trail-link/v1", trail.getUuid());
			else
				response = post("/api/trail-link/v2", new CreatePublicLinkRequest(trail.getOwner(), trail.getUuid()));
			assertThat(response.statusCode()).isEqualTo(200);
			var link = response.getBody().as(MyTrailLink.class);
			assertThat(link.getLink()).isNotNull();
			assertThat(link.getTrailOwner()).isEqualTo(trail.getOwner());
			assertThat(link.getTrailUuid()).isEqualTo(trail.getUuid());
			return link;
		}
		
		public List<MyTrailLink> getPublicLinks() {
			var response = get("/api/trail-link/v" + (clientVersion < 20300 ? "1" : "2"));
			assertThat(response.statusCode()).isEqualTo(200);
			return Arrays.asList(response.getBody().as(MyTrailLink[].class));
		}
		
		public TrailLinkContent getPublicLinkContent(MyTrailLink link) {
			var response = get("/api/trail-link/v" + (clientVersion < 20300 ? "1" : "2") + "/trail/" + link.getLink());
			assertThat(response.statusCode()).isEqualTo(200);
			return response.getBody().as(TrailLinkContent.class);
		}
		
		public void deletePublicLink(MyTrailLink link) {
			Response response;
			if (clientVersion < 20300)
				response = delete("/api/trail-link/v1/" + link.getTrailUuid());
			else
				response = delete("/api/trail-link/v2/" + link.getTrailOwner() + "/" + link.getTrailUuid());
			assertThat(response.statusCode()).isEqualTo(200);
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
				.cookie("trailence_token", trustToken)
				.body(new RenewTokenRequest(auth.getEmail(), initRenew.getRandom(), auth.getKeyId(), signature, new HashMap<String, Object>(), null, null))
				.post("/api/auth/v1/renew");
			assertThat(response.statusCode()).isEqualTo(200);
			var authRenew = response.getBody().as(AuthResponse.class);
			assertThat(authRenew.getAccessToken()).isNotNull();
			assertThat(authRenew.getEmail()).isEqualTo(getEmail().toLowerCase());
			assertThat(authRenew.getPreferences()).isNotNull();
			assertThat(authRenew.getQuotas()).isNotNull();
			assertThat(authRenew.getKeyId()).isEqualTo(auth.getKeyId());
			this.trustToken = response.getCookie("trailence_token");
			assertThat(this.trustToken).isNotBlank();
			this.auth = authRenew;
			return authRenew;
		}
	}
	
	public static class TestAdminLoggedIn extends TestUserLoggedIn {
		private TestAdminLoggedIn(TestUserLoggedIn admin) {
			super(admin.getEmail(), admin.getPassword(), admin.getKeyPair(), admin.getAuth(), admin.getTrustToken(), TestUserLoggedIn.DEFAULT_CLIENT_VERSION);
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
