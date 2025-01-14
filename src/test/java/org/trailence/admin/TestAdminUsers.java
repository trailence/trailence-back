package org.trailence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.trailence.global.dto.PageResult;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.user.dto.User;

import io.restassured.common.mapper.TypeRef;

class TestAdminUsers extends AbstractTest {

	@Test
	void testGetUsersAndKeysWithAdminAccount() {
		var user = test.createUserAndLogin(true);
		
		var response = user.get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		
		var users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		assertThat(users.getCount()).isPositive();
		assertThat(users.getElements()).isNotEmpty();
		assertThat(users.getPage()).isZero();
		assertThat(users.getSize()).isEqualTo(-1);
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
		var user = test.createUserAndLogin(false);
		
		var response = user.get("/api/admin/users/v1");
		TestUtils.expectError(response, 403, "forbidden");
		
		response = user.get("/api/admin/users/v1/{user}/keys", user.getEmail());
		TestUtils.expectError(response, 403, "forbidden");
	}
	
}
