package org.trailence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.trailence.auth.dto.ForgotPasswordRequest;
import org.trailence.auth.dto.LoginRequest;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.test.stubs.CaptchaStub;
import org.trailence.user.dto.ResetPasswordRequest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class TestForgotPassword extends AbstractTest {

	@Test
	void testForgotPassword() {
		var user = test.createUser();
		
		var captchaToken = RandomStringUtils.random(30);
		var stub = CaptchaStub.stubCaptcha(wireMockServer, captchaToken, true);
		
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new ForgotPasswordRequest(user.getEmail(), captchaToken, "en"))
			.post("/api/auth/v1/forgot");
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);

		var email = assertMailSent("trailence@trailence.org", user.getEmail().toLowerCase());
		assertThat(email.getT1()).isEqualTo("Your code to change your password on trailence.org");
		assertThat(email.getT2()).contains("Here is your code to change your password on trailence.org: ");
		int i = email.getT2().indexOf("Here is your code to change your password on trailence.org: ");
		int j = email.getT2().indexOf("\r\n", i);
		var code = email.getT2().substring(i + 60, j);

		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new ResetPasswordRequest(user.getEmail(), "new_password", code))
			.post("/api/user/v1/resetPassword");
		assertThat(response.statusCode()).isEqualTo(200);
		
		var keyPair = test.generateKeyPair();
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "new_password", keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		assertThat(response.statusCode()).isEqualTo(200);
	}
	
	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 3, 4 })
	void testForgotPasswordWithInvalidCodeAttempts(int invalidAttempts) {
		var user = test.createUser();
		
		var captchaToken = RandomStringUtils.random(30);
		var stub = CaptchaStub.stubCaptcha(wireMockServer, captchaToken, true);
		
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new ForgotPasswordRequest(user.getEmail(), captchaToken, "en"))
			.post("/api/auth/v1/forgot");
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);

		var email = assertMailSent("trailence@trailence.org", user.getEmail().toLowerCase());
		assertThat(email.getT1()).isEqualTo("Your code to change your password on trailence.org");
		assertThat(email.getT2()).contains("Here is your code to change your password on trailence.org: ");
		int i = email.getT2().indexOf("Here is your code to change your password on trailence.org: ");
		int j = email.getT2().indexOf("\r\n", i);
		var code = email.getT2().substring(i + 60, j);

		for (int attempts = 0; attempts < invalidAttempts; ++attempts) {
			var invalidCode = RandomStringUtils.randomNumeric(6);
			while (invalidCode.equals(code)) invalidCode = RandomStringUtils.randomNumeric(6);
			response = RestAssured.given()
				.contentType(ContentType.JSON)
				.body(new ResetPasswordRequest(user.getEmail(), "new_password", invalidCode))
				.post("/api/user/v1/resetPassword");
			TestUtils.expectError(response, 400, "invalid-code");
		}
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new ResetPasswordRequest(user.getEmail(), "new_password", code))
			.post("/api/user/v1/resetPassword");
		if (invalidAttempts >= 3) {
			TestUtils.expectError(response, 400, "invalid-code");
		} else {
			assertThat(response.statusCode()).isEqualTo(200);
		}
		
		var keyPair = test.generateKeyPair();
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new LoginRequest(user.getEmail(), "new_password", keyPair.getPublic().getEncoded(), new HashMap<String, Object>(), null))
			.post("/api/auth/v1/login");
		if (invalidAttempts >= 3) {
			TestUtils.expectError(response, 403, "invalid-credentials");
		} else {
			assertThat(response.statusCode()).isEqualTo(200);
		}
	}
	
	@Test
	void testForgotPasswordCodeCancelled() {
		var user = test.createUser();
		
		var captchaToken = RandomStringUtils.random(30);
		var stub = CaptchaStub.stubCaptcha(wireMockServer, captchaToken, true);
		
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new ForgotPasswordRequest(user.getEmail(), captchaToken, "en"))
			.post("/api/auth/v1/forgot");
		assertThat(response.statusCode()).isEqualTo(200);

		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);

		var email = assertMailSent("trailence@trailence.org", user.getEmail().toLowerCase());
		assertThat(email.getT1()).isEqualTo("Your code to change your password on trailence.org");
		assertThat(email.getT2()).contains("Here is your code to change your password on trailence.org: ");
		int i = email.getT2().indexOf("Here is your code to change your password on trailence.org: ");
		int j = email.getT2().indexOf("\r\n", i);
		var code = email.getT2().substring(i + 60, j);
		
		assertThat(email.getT2()).contains("https://trailence.org/link/");
		i = email.getT2().indexOf("https://trailence.org/link/");
		j = email.getT2().indexOf("\r\n", i);
		var token = email.getT2().substring(i + 27, j);
		
		response = RestAssured.given()
			.delete("/api/user/v1/changePassword?token=" + token);
		assertThat(response.statusCode()).isEqualTo(200);
		
		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new ResetPasswordRequest(user.getEmail(), "new_password", code))
			.post("/api/user/v1/resetPassword");
		TestUtils.expectError(response, 400, "invalid-code");
	}


	@Test
	void testForgotPasswordInvalidCaptcha() {
		var user = test.createUser();
		
		var captchaToken = RandomStringUtils.random(30);
		var stub = CaptchaStub.stubCaptcha(wireMockServer, captchaToken, false);
		
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new ForgotPasswordRequest(user.getEmail(), captchaToken, "en"))
			.post("/api/auth/v1/forgot");
		assertThat(response.statusCode()).isEqualTo(403);
		
		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);
	}
	
}
