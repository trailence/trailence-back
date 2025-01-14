package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.trail.dto.Tag;
import org.trailence.trail.dto.TrailTag;

@SuppressWarnings("java:S117")
class TestTags extends AbstractTest {

	@Test
	void crud() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		
		var tag1 = user.createTag(mytrails, null);
		var tag2 = user.createTag(mytrails, null);
		var tag3 = user.createTag(mytrails, null);
		var tag4 = user.createTag(mytrails, null);
		
		var tag2_1 = user.createTag(mytrails, tag2);
		var tag2_2 = user.createTag(mytrails, tag2);
		var tag2_2_1 = user.createTag(mytrails, tag2_2);
		
		user.expectTags(tag1, tag2, tag3, tag4, tag2_1, tag2_2, tag2_2_1);
		
		// update tag1
		tag1.setName("updated");
		var response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag1));
		assertThat(response.statusCode()).isEqualTo(200);
		var list = response.getBody().as(Tag[].class);
		assertThat(list).hasSize(1);
		tag1 = list[0];
		assertThat(tag1.getName()).isEqualTo("updated");
		assertThat(tag1.getVersion()).isEqualTo(2L);
		
		user.expectTags(tag1, tag2, tag3, tag4, tag2_1, tag2_2, tag2_2_1);
		
		// delete tag3 and 2_2
		response = user.post("/api/tag/v1/_bulkDelete", List.of(tag3.getUuid(), tag2_2.getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);
		
		user.expectTags(tag1, tag2, tag4, tag2_1);
	}
	
	@Test
	void moveToAnotherParent() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		
		var tag1 = user.createTag(mytrails, null);
		var tag2 = user.createTag(mytrails, null);
		var tag2_1 = user.createTag(mytrails, tag2);
		var tag2_2 = user.createTag(mytrails, tag2);
		var tag2_2_1 = user.createTag(mytrails, tag2_2);
		
		user.expectTags(tag1, tag2, tag2_1, tag2_2, tag2_2_1);
		
		// move tag1 under tag 2_1
		tag1.setParentUuid(tag2_1.getUuid());
		var response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag1));
		assertThat(response.statusCode()).isEqualTo(200);
		var list = response.getBody().as(Tag[].class);
		assertThat(list).hasSize(1);
		tag1 = list[0];
		assertThat(tag1.getParentUuid()).isEqualTo(tag2_1.getUuid());
		assertThat(tag1.getVersion()).isEqualTo(2);
		user.expectTags(tag1, tag2, tag2_1, tag2_2, tag2_2_1);
		
		// move tag1 to root
		tag1.setParentUuid(null);
		response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag1));
		assertThat(response.statusCode()).isEqualTo(200);
		list = response.getBody().as(Tag[].class);
		assertThat(list).hasSize(1);
		tag1 = list[0];
		assertThat(tag1.getParentUuid()).isNull();
		assertThat(tag1.getVersion()).isEqualTo(3);
		user.expectTags(tag1, tag2, tag2_1, tag2_2, tag2_2_1);
		
		// move tag2_2 under tag1
		tag2_2.setParentUuid(tag1.getUuid());
		response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag2_2));
		assertThat(response.statusCode()).isEqualTo(200);
		list = response.getBody().as(Tag[].class);
		assertThat(list).hasSize(1);
		tag2_2 = list[0];
		assertThat(tag2_2.getParentUuid()).isEqualTo(tag1.getUuid());
		assertThat(tag2_2.getVersion()).isEqualTo(2);
		user.expectTags(tag1, tag2, tag2_1, tag2_2, tag2_2_1);

		// move back to tag2
		tag2_2.setParentUuid(tag2.getUuid());
		response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag2_2));
		assertThat(response.statusCode()).isEqualTo(200);
		list = response.getBody().as(Tag[].class);
		assertThat(list).hasSize(1);
		tag2_2 = list[0];
		assertThat(tag2_2.getParentUuid()).isEqualTo(tag2.getUuid());
		assertThat(tag2_2.getVersion()).isEqualTo(3);
		user.expectTags(tag1, tag2, tag2_1, tag2_2, tag2_2_1);
	}
	
	@Test
	void createOrUpdateWithLinkedDataThatDoesNotBelongToTheUser() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		
		var mytrails1 = user1.getMyTrails();
		var mytrails2 = user2.getMyTrails();
		var tag2 = user2.createTag(mytrails2, null);
		
		// create on collection of another user
		var response = user1.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			UUID.randomUUID().toString(), user1.getEmail(),	0, 0, 0, RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			null,
			mytrails2.getUuid()
		)));
		TestUtils.expectError(response, 404, "collection-not-found");
		assertThat(user1.getTags()).isEmpty();
		assertThat(user2.getTags()).singleElement().extracting("uuid").isEqualTo(tag2.getUuid());
		
		// create with parent from another user
		response = user1.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			UUID.randomUUID().toString(), user1.getEmail(),	0, 0, 0, RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			tag2.getUuid(),
			mytrails1.getUuid()
		)));
		TestUtils.expectError(response, 404, "tag-not-found");
		
		var tag1 = user1.createTag(mytrails1, null);
		
		// update with parent from another user
		tag1.setParentUuid(tag2.getUuid());
		response = user1.put("/api/tag/v1/_bulkUpdate", List.of(tag1));
		TestUtils.expectError(response, 404, "tag-not-found");
	}
	
	@Test
	void cannotAccessToTagsOfAnotherUser() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var col1 = user1.getMyTrails();
		var col2 = user2.getMyTrails();
		var tag1 = user1.createTag(col1, null);
		var tag2 = user2.createTag(col2, null);
		var tag3 = user2.createTag(col2, null);
		var trail1 = user1.createTrail(col1, true);
		var trail2 = user2.createTrail(col2, true);
		var tt2 = user2.createTrailTag(trail2, tag2);
		
		// cannot update tag
		var originalName = tag2.getName();
		tag2.setName("hacked");
		var response = user1.put("/api/tag/v1/_bulkUpdate", List.of(tag2));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getTags().stream().filter(t -> t.getUuid().equals(tag2.getUuid()) && t.getName().equals(originalName)).findAny()).isPresent();
		
		// cannot delete tag
		response = user1.post("/api/tag/v1/_bulkDelete", List.of(tag2.getUuid()));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getTags().stream().filter(t -> t.getUuid().equals(tag2.getUuid()) && t.getName().equals(originalName)).findAny()).isPresent();
		
		// cannot attach tags
		response = user1.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(tag3.getUuid(), trail2.getUuid(), 0)));
		TestUtils.expectError(response, 400, "invalid-input");
		response = user1.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(tag1.getUuid(), trail2.getUuid(), 0)));
		TestUtils.expectError(response, 400, "invalid-input");
		response = user1.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(tag3.getUuid(), trail1.getUuid(), 0)));
		TestUtils.expectError(response, 400, "invalid-input");
		
		// cannot remove trail tag
		response = user1.post("/api/tag/v1/trails/_bulkDelete", List.of(tt2));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user2.getTrailTags().stream().filter(t -> t.getTrailUuid().equals(tt2.getTrailUuid()) && t.getTagUuid().equals(tt2.getTagUuid())).findAny()).isPresent();
	}
	
	@Test
	void createWithInvalidInput() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		
		var response = user.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			null,
			user.getEmail(),
			0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			null,
			mytrails.getUuid()
		)));
		TestUtils.expectError(response, 400, "missing-uuid");
		
		response = user.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			"1234",
			user.getEmail(),
			0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			null,
			mytrails.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-uuid");
		
		response = user.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			UUID.randomUUID().toString(),
			user.getEmail(),
			0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(51),
			null,
			mytrails.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-name-too-long");
		
		response = user.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			UUID.randomUUID().toString(),
			user.getEmail(),
			0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			"1234",
			mytrails.getUuid()
		)));
		TestUtils.expectError(response, 400, "invalid-parentUuid");
		
		response = user.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			UUID.randomUUID().toString(),
			user.getEmail(),
			0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			UUID.randomUUID().toString(),
			mytrails.getUuid()
		)));
		TestUtils.expectError(response, 404, "tag-not-found");
		
		response = user.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			UUID.randomUUID().toString(),
			user.getEmail(),
			0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			null,
			"1234"
		)));
		TestUtils.expectError(response, 400, "invalid-collectionUuid");
		
		response = user.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			UUID.randomUUID().toString(),
			user.getEmail(),
			0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			null,
			null
		)));
		TestUtils.expectError(response, 400, "missing-collectionUuid");
		
		response = user.post("/api/tag/v1/_bulkCreate", List.of(new Tag(
			UUID.randomUUID().toString(),
			user.getEmail(),
			0, 0, 0,
			RandomStringUtils.insecure().nextAlphanumeric(0, 51),
			null,
			UUID.randomUUID().toString()
		)));
		TestUtils.expectError(response, 404, "collection-not-found");
	}
	
	@Test
	void updateWithInvalidInput() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var tag = user.createTag(mytrails, null);
		
		tag.setName(RandomStringUtils.insecure().nextAlphanumeric(51));
		var response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag));
		TestUtils.expectError(response, 400, "invalid-name-too-long");
	}
	
	@Test
	void deleteCollectionDeleteTags() {
		var user = test.createUserAndLogin();
		var col = user.createCollection();
		var tag = user.createTag(col, null);
		
		user.expectTags(tag);
		user.deleteCollections(col);
		user.expectTags();
	}
	
	@Test
	void createTwiceTheSameCreateOnlyFirst() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		var tag = user.createTag(col, null);
		
		var tag2 = new Tag(
			tag.getUuid(), user.getEmail(), 0, 0, 0,
			"updated", null, col.getUuid()
		);
		var response = user.post("/api/tag/v1/_bulkCreate", List.of(tag2));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(Tag[].class)).singleElement().isEqualTo(tag);
		assertThat(user.getTags()).singleElement().isEqualTo(tag);
	}
	
	@Test
	void updateWithOlderVersionDoNotUpdate() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		var tag = user.createTag(col, null);
		
		var tag2 = new Tag(
			tag.getUuid(), user.getEmail(), 1, 0, 0,
			"updated", null, col.getUuid()
		);
		var response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag2));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Tag[].class)[0];
		assertThat(updated.getVersion()).isEqualTo(2);
		assertThat(updated.getName()).isEqualTo("updated");
		
		var tag3 = new Tag(
			tag.getUuid(), user.getEmail(), 1, 0, 0,
			"updated again", null, col.getUuid()
		);
		response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag3));
		assertThat(response.statusCode()).isEqualTo(200);
		updated = response.getBody().as(Tag[].class)[0];
		assertThat(updated.getVersion()).isEqualTo(2);
		assertThat(updated.getName()).isEqualTo("updated");
	}
	
	@Test
	void updateWithSameValuesDoNotUpdate() {
		var user = test.createUserAndLogin();
		var col = user.getMyTrails();
		var tag = user.createTag(col, null);
		
		var tag2 = new Tag(
			tag.getUuid(), user.getEmail(), 1, 0, 0,
			tag.getName(), null, col.getUuid()
		);
		var response = user.put("/api/tag/v1/_bulkUpdate", List.of(tag2));
		assertThat(response.statusCode()).isEqualTo(200);
		var updated = response.getBody().as(Tag[].class)[0];
		assertThat(updated).isEqualTo(tag);
	}
	
}
