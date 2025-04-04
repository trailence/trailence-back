package org.trailence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestService.TestUser;
import org.trailence.test.stubs.CaptchaStub;
import org.trailence.user.dto.RegisterNewUserCodeRequest;
import org.trailence.user.dto.RegisterNewUserRequest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class TestRegisterAndDelete extends AbstractTest {

	@Test
	void testRegisterThenDelete() {
		var email = test.email();
		
		var captchaToken = RandomStringUtils.random(30);
		var stub = CaptchaStub.stubCaptcha(wireMockServer, captchaToken, true);
		
		var response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RegisterNewUserCodeRequest(email, "en", captchaToken))
			.post("/api/user/v1/sendRegisterCode");
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);

		var mail = assertMailSent("trailence@trailence.org", email.toLowerCase());
		assertThat(mail.getT1()).isEqualTo("Confirm the creation of your account on trailence.org");
		assertThat(mail.getT2()).contains("Here is your code to confirm the creation of your account on trailence.org: ");
		int i = mail.getT2().indexOf("Here is your code to confirm the creation of your account on trailence.org: ");
		int j = mail.getT2().indexOf("\r\n", i);
		var code = mail.getT2().substring(i + 76, j);

		response = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(new RegisterNewUserRequest(email, "en", code, "my_password"))
			.post("/api/user/v1/registerNewUser");
		assertThat(response.statusCode()).isEqualTo(200);

		assertThat(test.asAdmin().listUsers().getElements().stream().anyMatch(u -> u.getEmail().equals(email.toLowerCase()))).isTrue();
		
		// login
		var user = test.login(new TestUser(email, "my_password"), null, new HashMap<String, Object>());
		
		// delete me
		response = user.post("/api/user/v1/sendDeletionCode?lang=en", "");
		assertThat(response.statusCode()).isEqualTo(200);
		
		mail = assertMailSent("trailence@trailence.org", email.toLowerCase());
		assertThat(mail.getT1()).isEqualTo("Confirm the deletion of your account on trailence.org");
		assertThat(mail.getT2()).contains("Here is your code to confirm the deletion of your account on trailence.org: ");
		i = mail.getT2().indexOf("Here is your code to confirm the deletion of your account on trailence.org: ");
		j = mail.getT2().indexOf("\r\n", i);
		code = mail.getT2().substring(i + 76, j);
		
		response = user.post("/api/user/v1/deleteMe", code);
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(test.asAdmin().listUsers().getElements().stream().anyMatch(u -> u.getEmail().equals(email.toLowerCase()))).isFalse();
	}
	
}
