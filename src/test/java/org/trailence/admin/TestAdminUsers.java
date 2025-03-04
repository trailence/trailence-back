package org.trailence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.trailence.global.dto.PageResult;
import org.trailence.init.FreePlanProperties;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.user.dto.User;

import io.restassured.common.mapper.TypeRef;

class TestAdminUsers extends AbstractTest {
	
	@Autowired private FreePlanProperties freePlan;

	@Test
	void testGetUsersAndKeysWithAdminAccount() {
		assertThat(freePlan.getCollections()).isPositive();
		assertThat(freePlan.getTrails()).isPositive();
		assertThat(freePlan.getTracks()).isPositive();
		assertThat(freePlan.getTracksSize()).isPositive();
		assertThat(freePlan.getPhotos()).isPositive();
		assertThat(freePlan.getPhotosSize()).isPositive();
		assertThat(freePlan.getTags()).isPositive();
		assertThat(freePlan.getTrailTags()).isPositive();
		assertThat(freePlan.getShares()).isPositive();
		
		var user = test.createUserAndLogin(true, null);
		
		var response = user.get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		
		var users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		assertThat(users.getCount()).isPositive();
		assertThat(users.getElements()).isNotEmpty();
		assertThat(users.getPage()).isZero();
		assertThat(users.getSize()).isEqualTo(-1);
		assertThat(users.getElements()).allMatch(u -> 
			u.getQuotas().getCollectionsMax() == freePlan.getCollections() &&
			u.getQuotas().getTrailsMax() == freePlan.getTrails() &&
			u.getQuotas().getTracksMax() == freePlan.getTracks() &&
			u.getQuotas().getTracksSizeMax() == freePlan.getTracksSize() &&
			u.getQuotas().getPhotosMax() == freePlan.getPhotos() &&
			u.getQuotas().getPhotosSizeMax() == freePlan.getPhotosSize() &&
			u.getQuotas().getTagsMax() == freePlan.getTags() &&
			u.getQuotas().getTrailTagsMax() == freePlan.getTrailTags() &&
			u.getQuotas().getSharesMax() == freePlan.getShares()
		);
		for (int i = 0; i < 3 && i < users.getElements().size(); ++i) {
			var keysResponse = user.get("/api/admin/users/v1/{user}/keys", users.getElements().get(i).getEmail());
			assertThat(keysResponse.statusCode()).isEqualTo(200);
		}
		
		response = user.get("/api/admin/users/v1?page=1&size=2&sort=createdAt,desc");
		assertThat(response.statusCode()).isEqualTo(200);
		users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		assertThat(users.getCount()).isPositive();
		assertThat(users.getPage()).isEqualTo(1);
		assertThat(users.getSize()).isEqualTo(2);
		
		response = user.get("/api/admin/users/v1?page=1&size=2&sort=minAppVersion,asc");
		assertThat(response.statusCode()).isEqualTo(200);
		users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		assertThat(users.getCount()).isPositive();
		assertThat(users.getPage()).isEqualTo(1);
		assertThat(users.getSize()).isEqualTo(2);
	}

	@Test
	void testGetUsersAndKeysWithoutAdminAccount() {
		var user = test.createUserAndLogin(false, null);
		
		var response = user.get("/api/admin/users/v1");
		TestUtils.expectError(response, 403, "forbidden");
		
		response = user.get("/api/admin/users/v1/{user}/keys", user.getEmail());
		TestUtils.expectError(response, 403, "forbidden");
	}
	
	@Test
	void updateUserRoles() {
		var user = test.createUserAndLogin(false, null);
		
		var response = test.asAdmin().get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		var users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		var myUser = users.getElements().stream().filter(u -> u.getEmail().equals(user.getEmail().toLowerCase())).findAny();
		assertThat(myUser).isPresent();
		assertThat(myUser.get().getRoles()).isEmpty();
		
		response = test.asAdmin().put("/api/admin/users/v1/" + user.getEmail() + "/roles", List.of("hello", "world"));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(String[].class)).isEqualTo(new String[] { "hello", "world" });
		
		response = test.asAdmin().get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		myUser = users.getElements().stream().filter(u -> u.getEmail().equals(user.getEmail().toLowerCase())).findAny();
		assertThat(myUser).isPresent();
		assertThat(myUser.get().getRoles()).isEqualTo(List.of("hello", "world"));
		
		response = test.asAdmin().put("/api/admin/users/v1/" + user.getEmail() + "/roles", List.of());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(String[].class)).isEqualTo(new String[] {});
		
		response = test.asAdmin().get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		myUser = users.getElements().stream().filter(u -> u.getEmail().equals(user.getEmail().toLowerCase())).findAny();
		assertThat(myUser).isPresent();
		assertThat(myUser.get().getRoles()).isEmpty();
	}
	
}
