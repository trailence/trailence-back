package org.trailence.test.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CurrencyStub {

	public static StubMapping stubCurrency(WireMockServer server, String usd) {
		return server.stubFor(
			get(urlEqualTo("/currency"))
			.willReturn(
				aResponse()
				.withStatus(200)
				.withBody("{\"eur\": { \"usd\": " + usd + "}}")
				.withHeader("Content-Type", "application/json")
			)
		);
	}
	
}
