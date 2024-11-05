package org.trailence.test.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CaptchaStub {

	public static StubMapping stubCaptcha(WireMockServer server, String token, boolean success) {
		return server.stubFor(
			post(urlEqualTo("/captcha/siteverify"))
			.withFormParam("secret", equalTo("captchaSecret"))
			.withFormParam("response", equalTo(token))
			.willReturn(
				aResponse()
				.withStatus(200)
				.withBody("{\"success\":" + success + "}")
				.withHeader("Content-Type", "application/json")
			)
		);
	}
	
}
