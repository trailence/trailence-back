package org.trailence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.trailence.auth.dto.LoginRequest;
import org.trailence.test.AbstractTest;
import org.trailence.user.dto.ChangePasswordRequest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class TestChangePassword extends AbstractTest {

	@Test
	void testChangePassword() {
		var user = test.createUserAndLogin();
		
		var response = user.get("/api/user/v1/sendChangePasswordCode?lang=es");
		assertThat(response.statusCode()).isEqualTo(200);
		
		var email = assertMailSent("trailence@trailence.org", user.getEmail().toLowerCase());
		assertThat(email.getT1()).isEqualTo("Your code to change your password on trailence.org");
		assertThat(email.getT2()).contains("Here is your code to change your password on trailence.org: ");
		int i = email.getT2().indexOf("Here is your code to change your password on trailence.org: ");
		int j = email.getT2().indexOf("\r\n", i);
		var code = email.getT2().substring(i + 60, j);

		response = user.post("/api/user/v1/changePassword", new ChangePasswordRequest(user.getPassword(), "new_password", code));
		assertThat(response.statusCode()).isEqualTo(200);
		
		var keyPair = test.generateKeyPair();
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "new_password", keyPair.getPublic().getEncoded(), null, new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
	}

}
