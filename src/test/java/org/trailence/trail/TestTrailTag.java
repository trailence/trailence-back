package org.trailence.trail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.trail.dto.TrailTag;

class TestTrailTag extends AbstractTest {

	@Test
	void createDelete() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail1 = user.createTrail(mytrails, true);
		var trail2 = user.createTrail(mytrails, true);
		var tag1 = user.createTag(mytrails, null);
		var tag2 = user.createTag(mytrails, null);
		var tag3 = user.createTag(mytrails, null);
		
		var t1 = user.createTrailTag(trail1, tag1);
		var t2 = user.createTrailTag(trail1, tag2);
		var t3 = user.createTrailTag(trail2, tag2);
		var t4 = user.createTrailTag(trail2, tag3);
		
		assertThat(user.getTrailTags()).containsExactlyInAnyOrder(t1, t2, t3, t4);
		
		var response = user.post("/api/tag/v1/trails/_bulkDelete", List.of(t2, t3));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(user.getTrailTags()).containsExactlyInAnyOrder(t1, t4);
	}
	
	@Test
	void deleteCollectionDeleteTrailTags() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var col = user.createCollection();
		var trail1 = user.createTrail(col, true);
		var trail2 = user.createTrail(col, true);
		var trail3 = user.createTrail(mytrails, true);
		var tag1 = user.createTag(col, null);
		var tag2 = user.createTag(col, null);
		var tag3 = user.createTag(mytrails, null);
		
		var t1 = user.createTrailTag(trail1, tag1);
		var t2 = user.createTrailTag(trail1, tag2);
		var t3 = user.createTrailTag(trail2, tag2);
		var t4 = user.createTrailTag(trail3, tag3);
		
		assertThat(user.getTrailTags()).containsExactlyInAnyOrder(t1, t2, t3, t4);
		
		user.deleteCollections(col);
		
		assertThat(user.getTrailTags()).containsExactlyInAnyOrder(t4);
	}
	
	@Test
	void deleteTrailDeleteTrailTags() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail1 = user.createTrail(mytrails, true);
		var trail2 = user.createTrail(mytrails, true);
		var tag1 = user.createTag(mytrails, null);
		var tag2 = user.createTag(mytrails, null);
		var tag3 = user.createTag(mytrails, null);
		
		var t1 = user.createTrailTag(trail1, tag1);
		var t2 = user.createTrailTag(trail1, tag2);
		var t3 = user.createTrailTag(trail2, tag2);
		var t4 = user.createTrailTag(trail2, tag3);
		
		assertThat(user.getTrailTags()).containsExactlyInAnyOrder(t1, t2, t3, t4);
		
		user.deleteTrails(trail1);
		assertThat(user.getTrailTags()).containsExactlyInAnyOrder(t3, t4);
	}
	
	@Test
	void createInvalid() {
		var user = test.createUserAndLogin();
		var mytrails = user.getMyTrails();
		var trail1 = user.createTrail(mytrails, true);
		var tag1 = user.createTag(mytrails, null);
		var col = user.createCollection();
		var trail2 = user.createTrail(col, true);
		var tag2 = user.createTag(col, null);
		
		var response = user.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(tag1.getUuid(), trail2.getUuid(), 0)));
		TestUtils.expectError(response, 400, "invalid-input");
		
		response = user.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(tag2.getUuid(), trail1.getUuid(), 0)));
		TestUtils.expectError(response, 400, "invalid-input");
		
		response = user.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(UUID.randomUUID().toString(), trail1.getUuid(), 0)));
		TestUtils.expectError(response, 400, "invalid-input");
		
		response = user.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(null, trail1.getUuid(), 0)));
		TestUtils.expectError(response, 400, "missing-tagUuid");
		
		response = user.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag("1234", trail1.getUuid(), 0)));
		TestUtils.expectError(response, 400, "invalid-tagUuid");
		
		response = user.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(tag1.getUuid(), UUID.randomUUID().toString(), 0)));
		TestUtils.expectError(response, 400, "invalid-input");
		
		response = user.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(tag1.getUuid(), null, 0)));
		TestUtils.expectError(response, 400, "missing-trailUuid");
		
		response = user.post("/api/tag/v1/trails/_bulkCreate", List.of(new TrailTag(tag1.getUuid(), "1234", 0)));
		TestUtils.expectError(response, 400, "invalid-trailUuid");
	}
	
}
