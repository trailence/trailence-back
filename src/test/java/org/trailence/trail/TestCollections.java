package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.test.TestService.TestUserLoggedIn;
import org.trailence.trail.dto.TrailCollection;
import org.trailence.trail.dto.TrailCollectionType;

import io.restassured.common.mapper.TypeRef;

class TestCollections extends AbstractTest {

	@Test
	void userHasMyTrailsByDefault() {
		var user = test.createUserAndLogin();
		var response = user.post("/api/trail-collection/v1/_bulkGetUpdates", List.<Versioned>of());
		assertThat(response.statusCode()).isEqualTo(200);
		var update = response.getBody().as(new TypeRef<UpdateResponse<TrailCollection>>() {});
		assertThat(update.getDeleted()).isEmpty();
		assertThat(update.getUpdated()).isEmpty();
		assertThat(update.getCreated()).hasSize(1);
		var collection = update.getCreated().getFirst();
		assertThat(collection.getType()).isEqualTo(TrailCollectionType.MY_TRAILS);
		assertThat(collection.getName()).isEmpty();
	}
	
	@Test
	void crud() {
		var user = test.createUserAndLogin();
		// create 6 collections
		var create = List.of(
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.CUSTOM),
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.CUSTOM),
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.CUSTOM),
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.CUSTOM),
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.CUSTOM),
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.CUSTOM)
		);
		var beforeCreate = System.currentTimeMillis();
		var response = user.post("/api/trail-collection/v1/_bulkCreate", create);
		assertThat(response.statusCode()).isEqualTo(200);
		var created = response.getBody().as(TrailCollection[].class);
		var afterCreate = System.currentTimeMillis();
		assertThat(created).hasSameSizeAs(create);
		for (var expected : create)
			assertThat(created).as(expected.toString()).satisfiesOnlyOnce(col -> {
				assertThat(col.getUuid()).isEqualTo(expected.getUuid());
				assertThat(col.getName()).isEqualTo(expected.getName());
				assertThat(col.getType()).isEqualTo(TrailCollectionType.CUSTOM);
				assertThat(col.getOwner()).isEqualTo(user.getEmail().toLowerCase());
				assertThat(col.getVersion()).isEqualTo(1L);
				assertThat(col.getCreatedAt()).isGreaterThanOrEqualTo(beforeCreate).isLessThanOrEqualTo(afterCreate);
			});
		
		var update = getCollections(user, List.of());
		assertThat(update.getDeleted()).isEmpty();
		assertThat(update.getUpdated()).isEmpty();
		assertThat(update.getCreated())
			.hasSize(create.size() + 1)
			.satisfiesOnlyOnce(col -> assertThat(col.getType()).isEqualTo(TrailCollectionType.MY_TRAILS));
		for (var expected : create)
			assertThat(created).as(expected.toString()).satisfiesOnlyOnce(col -> {
				assertThat(col.getUuid()).isEqualTo(expected.getUuid());
				assertThat(col.getName()).isEqualTo(expected.getName());
				assertThat(col.getType()).isEqualTo(TrailCollectionType.CUSTOM);
				assertThat(col.getOwner()).isEqualTo(user.getEmail().toLowerCase());
				assertThat(col.getVersion()).isEqualTo(1L);
				assertThat(col.getCreatedAt()).isGreaterThanOrEqualTo(beforeCreate).isLessThanOrEqualTo(afterCreate);
			});
		
		// delete collections at index 2 and 4
		user.deleteCollections(create.get(2), create.get(4));

		update = getCollections(user, Arrays.stream(created).map(col -> new Versioned(col.getUuid(), user.getEmail(), col.getVersion())).toList());
		assertThat(update.getDeleted())
			.hasSize(2)
			.satisfiesOnlyOnce(deleted -> assertThat(deleted.getUuid()).isEqualTo(create.get(2).getUuid()))
			.satisfiesOnlyOnce(deleted -> assertThat(deleted.getUuid()).isEqualTo(create.get(4).getUuid()));
		assertThat(update.getUpdated()).isEmpty();
		assertThat(update.getCreated())
			.hasSize(1)
			.satisfiesOnlyOnce(col -> assertThat(col.getType()).isEqualTo(TrailCollectionType.MY_TRAILS));

		// update collections at index 3 and 5
		var updateRequest = List.of(create.get(3), create.get(5)).stream().map(col -> new TrailCollection(col.getUuid(), user.getEmail(), 1L, 0, 0, "updated" + RandomStringUtils.random(5), TrailCollectionType.CUSTOM)).toList();
		response = user.put("/api/trail-collection/v1/_bulkUpdate", updateRequest);
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(TrailCollection[].class);
		assertThat(updated)
			.hasSize(2);
		for (var expected : updateRequest)
			assertThat(updated).as(expected.toString()).satisfiesOnlyOnce(c -> {
				assertThat(c.getUuid()).isEqualTo(expected.getUuid());
				assertThat(c.getName()).isEqualTo(expected.getName());
				assertThat(c.getVersion()).isEqualTo(2L);
			});

		update = getCollections(user, Arrays.stream(created).map(col -> new Versioned(col.getUuid(), user.getEmail(), col.getVersion())).toList());
		assertThat(update.getDeleted()).hasSize(2);
		assertThat(update.getUpdated())
			.hasSize(2)
			.satisfiesOnlyOnce(c -> assertThat(c.getUuid()).isEqualTo(updateRequest.get(0).getUuid()))
			.satisfiesOnlyOnce(c -> assertThat(c.getUuid()).isEqualTo(updateRequest.get(1).getUuid()));
		assertThat(update.getCreated())
			.hasSize(1)
			.satisfiesOnlyOnce(col -> assertThat(col.getType()).isEqualTo(TrailCollectionType.MY_TRAILS));
	}
	
	@Test
	void cannotCreateOrDeleteMyTrails() {
		var user = test.createUserAndLogin();
		
		var response = user.post("/api/trail-collection/v1/_bulkCreate", List.of(
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.MY_TRAILS)
		));
		TestUtils.expectError(response, 400, "invalid-type-value");

		var mytrails = getCollections(user, List.of()).getCreated().getFirst();
		response = user.post("/api/trail-collection/v1/_bulkDelete", List.of(mytrails.getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(getCollections(user, List.of()).getCreated()).hasSize(1);
	}
	
	@Test
	void collectionWithTooLongName() {
		var user = test.createUserAndLogin();
		
		var response = user.post("/api/trail-collection/v1/_bulkCreate", List.of(
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(51), TrailCollectionType.CUSTOM)
		));
		TestUtils.expectError(response, 400, "invalid-name-too-long");
		
		var mytrails = getCollections(user, List.of()).getCreated().getFirst();
		mytrails.setName(RandomStringUtils.randomAlphanumeric(51));
		response = user.put("/api/trail-collection/v1/_bulkUpdate", List.of(mytrails));
		TestUtils.expectError(response, 400, "invalid-name-too-long");
	}
	
	@Test
	void createCollectionWithWrongUuid() {
		var user = test.createUserAndLogin();
		
		var response = user.post("/api/trail-collection/v1/_bulkCreate", List.of(
			new TrailCollection("not a uuid", user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(51), TrailCollectionType.CUSTOM)
		));
		TestUtils.expectError(response, 400, "invalid-uuid");

		response = user.post("/api/trail-collection/v1/_bulkCreate", List.of(
			new TrailCollection(null, user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(51), TrailCollectionType.CUSTOM)
		));
		TestUtils.expectError(response, 400, "missing-uuid");
	}
	
	@Test
	void createCollectionWithWrongOwner() {
		var user = test.createUserAndLogin();
		
		var response = user.post("/api/trail-collection/v1/_bulkCreate", List.of(
			new TrailCollection(UUID.randomUUID().toString(), "not me", 0, 0, 0, RandomStringUtils.randomAlphanumeric(10), TrailCollectionType.CUSTOM)
		));
		assertThat(response.statusCode()).isEqualTo(200);
		var created = response.getBody().as(TrailCollection[].class);
		assertThat(created).hasOnlyOneElementSatisfying(col -> {
			assertThat(col.getType()).isEqualTo(TrailCollectionType.CUSTOM);
			assertThat(col.getOwner()).isEqualTo(user.getEmail().toLowerCase());
			assertThat(col.getVersion()).isEqualTo(1L);
		});
	}
	
	@Test
	void createCollectionWithVersionCreatedAtAndUpdatedAt() {
		var user = test.createUserAndLogin();
		
		var beforeCreate = System.currentTimeMillis();
		var response = user.post("/api/trail-collection/v1/_bulkCreate", List.of(
			new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 10L, 20L, 30L, RandomStringUtils.randomAlphanumeric(10), TrailCollectionType.CUSTOM)
		));
		assertThat(response.statusCode()).isEqualTo(200);
		var created = response.getBody().as(TrailCollection[].class);
		var afterCreate = System.currentTimeMillis();
		assertThat(created).hasOnlyOneElementSatisfying(col -> {
			assertThat(col.getType()).isEqualTo(TrailCollectionType.CUSTOM);
			assertThat(col.getOwner()).isEqualTo(user.getEmail().toLowerCase());
			assertThat(col.getVersion()).isEqualTo(1L);
			assertThat(col.getCreatedAt()).isGreaterThanOrEqualTo(beforeCreate).isLessThanOrEqualTo(afterCreate);
			assertThat(col.getUpdatedAt()).isGreaterThanOrEqualTo(beforeCreate).isLessThanOrEqualTo(afterCreate);
		});
	}
	
	@Test
	void cannotAccessToCollectionOfAnotherUser() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var col2 = user2.createCollection();
		
		String originalName = col2.getName();
		col2.setName("hacked");
		var response = user1.put("/api/trail-collection/v1/_bulkUpdate", List.of(col2));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getCollections().stream().filter(c -> c.getUuid().equals(col2.getUuid()) && c.getName().equals(originalName)).findAny()).isPresent();
		
		response = user1.post("/api/trail-collection/v1/_bulkDelete", List.of(col2.getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getCollections()).hasSize(2);
	}
	
	@Test
	void createTwiceTheSameCreateOnlyFirst() {
		var user = test.createUserAndLogin();
		var create = new TrailCollection(UUID.randomUUID().toString(), user.getEmail(), 0, 0, 0, RandomStringUtils.randomAlphanumeric(3, 20), TrailCollectionType.CUSTOM);
		var response = user.post("/api/trail-collection/v1/_bulkCreate", List.of(create, create));
		assertThat(response.statusCode()).isEqualTo(200);
		var created = response.getBody().as(TrailCollection[].class);
		assertThat(created).singleElement().extracting("uuid").isEqualTo(create.getUuid());
		assertThat(user.getCollections()).hasSize(2);
		
		var originalName = create.getName();
		create.setName("new name");
		response = user.post("/api/trail-collection/v1/_bulkCreate", List.of(create, create));
		created = response.getBody().as(TrailCollection[].class);
		assertThat(created).singleElement().extracting("name").isEqualTo(originalName);
		assertThat(user.getCollections()).hasSize(2).anyMatch(c -> c.getUuid().equals(create.getUuid()) && c.getName().equals(originalName));
	}
	
	@Test
	void updateWithOlderVersionDoNotUpdate() {
		var user = test.createUserAndLogin();
		var col = user.createCollection();
		
		col.setName("update1");
		var response = user.put("/api/trail-collection/v1/_bulkUpdate", List.of(col));
		assertThat(response.statusCode()).isEqualTo(200);
		var updatedList = response.getBody().as(TrailCollection[].class);
		assertThat(updatedList).hasSize(1);
		var updated1 = updatedList[0];
		assertThat(updated1.getVersion()).isEqualTo(2);
		assertThat(updated1.getName()).isEqualTo("update1");
		
		col.setName("update2");
		response = user.put("/api/trail-collection/v1/_bulkUpdate", List.of(col));
		assertThat(response.statusCode()).isEqualTo(200);
		updatedList = response.getBody().as(TrailCollection[].class);
		assertThat(updatedList).hasSize(1);
		var updated2 = updatedList[0];
		assertThat(updated2.getVersion()).isEqualTo(2);
		assertThat(updated2.getName()).isEqualTo("update1");
	}
	
	@Test
	void updateWithSameValuesDoNotUpdate() {
		var user = test.createUserAndLogin();
		var col = user.createCollection();
		
		var response = user.put("/api/trail-collection/v1/_bulkUpdate", List.of(col));
		assertThat(response.statusCode()).isEqualTo(200);
		var updatedList = response.getBody().as(TrailCollection[].class);
		assertThat(updatedList).hasSize(1);
		assertThat(updatedList[0]).isEqualTo(col);
	}
	
	private UpdateResponse<TrailCollection> getCollections(TestUserLoggedIn user, List<Versioned> known) {
		var response = user.post("/api/trail-collection/v1/_bulkGetUpdates", known);
		assertThat(response.statusCode()).isEqualTo(200);
		return response.getBody().as(new TypeRef<UpdateResponse<TrailCollection>>() {});
	}

}
