package org.trailence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.trailence.global.dto.PageResult;
import org.trailence.quotas.dto.Plan;
import org.trailence.test.AbstractTest;

import io.restassured.common.mapper.TypeRef;

class TestAdminPlans extends AbstractTest {
	
	private static final String PATH = "/api/admin/plans/v1";

	@Test
	void crud() {
		var name = RandomStringUtils.insecure().nextAlphabetic(20);
		var response = test.asAdmin().post(PATH, new Plan(name, 1, 2, 3, 4, 5, 6, 7, 8, 9, null, null));
		assertThat(response.statusCode()).isEqualTo(200);
		
		response = test.asAdmin().get(PATH);
		assertThat(response.statusCode()).isEqualTo(200);
		var plans = response.as(new TypeRef<PageResult<Plan>>() {}).getElements();
		assertThat(plans.stream().filter(p -> p.getName().equals(name)).findAny()).isPresent().get().satisfies(plan -> {
			assertThat(plan.getCollections()).isEqualTo(1);
			assertThat(plan.getTrails()).isEqualTo(2);
			assertThat(plan.getTracks()).isEqualTo(3);
			assertThat(plan.getTracksSize()).isEqualTo(4);
			assertThat(plan.getPhotos()).isEqualTo(5);
			assertThat(plan.getPhotosSize()).isEqualTo(6);
			assertThat(plan.getTags()).isEqualTo(7);
			assertThat(plan.getTrailTags()).isEqualTo(8);
			assertThat(plan.getShares()).isEqualTo(9);
			assertThat(plan.getActiveSubscriptionsCount()).isZero();
			assertThat(plan.getSubscriptionsCount()).isZero();
		});
		
		var newName = RandomStringUtils.insecure().nextAlphabetic(20);
		response = test.asAdmin().put(PATH + "/" + name, new Plan(newName, 10, 20, 30, 40, 50, 60, 70, 80, 90, null, null));
		assertThat(response.statusCode()).isEqualTo(200);

		response = test.asAdmin().get(PATH);
		assertThat(response.statusCode()).isEqualTo(200);
		plans = response.as(new TypeRef<PageResult<Plan>>() {}).getElements();
		assertThat(plans.stream().filter(p -> p.getName().equals(newName)).findAny()).isPresent().get().satisfies(plan -> {
			assertThat(plan.getCollections()).isEqualTo(10);
			assertThat(plan.getTrails()).isEqualTo(20);
			assertThat(plan.getTracks()).isEqualTo(30);
			assertThat(plan.getTracksSize()).isEqualTo(40);
			assertThat(plan.getPhotos()).isEqualTo(50);
			assertThat(plan.getPhotosSize()).isEqualTo(60);
			assertThat(plan.getTags()).isEqualTo(70);
			assertThat(plan.getTrailTags()).isEqualTo(80);
			assertThat(plan.getShares()).isEqualTo(90);
			assertThat(plan.getActiveSubscriptionsCount()).isZero();
			assertThat(plan.getSubscriptionsCount()).isZero();
		});
		assertThat(plans.stream().noneMatch(p -> p.getName().equals(name))).isTrue();
		
		response = test.asAdmin().delete(PATH + "/" + newName);
		assertThat(response.statusCode()).isEqualTo(200);
		
		response = test.asAdmin().get(PATH);
		assertThat(response.statusCode()).isEqualTo(200);
		plans = response.as(new TypeRef<PageResult<Plan>>() {}).getElements();
		assertThat(plans.stream().noneMatch(p -> p.getName().equals(name))).isTrue();
		assertThat(plans.stream().noneMatch(p -> p.getName().equals(newName))).isTrue();
	}
	
	
}
