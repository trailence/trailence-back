package org.trailence.test.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.util.List;

import org.trailence.test.TestUtils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.ExactMatchMultiValuePattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GeonamesStub {

	public static StubMapping stubGetPlaces(WireMockServer server, double lat, double lng, String lang) throws IOException {
		return server.stubFor(
			get(urlPathEqualTo("/geonames/findNearbyPlaceNameJSON"))
			.withQueryParam("lat", equalTo(Double.toString(lat)))
			.withQueryParam("lng", equalTo(Double.toString(lng)))
			.withQueryParam("lang", equalTo(lang))
			.withQueryParam("style", equalTo("full"))
			.withQueryParam("localCountry", equalTo("false"))
			.withQueryParam("radius", equalTo("2"))
			.withQueryParam("username", equalTo("geo_user"))
			.willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(TestUtils.getResource("geonames/places.json")))
		);
	}
	
	public static StubMapping stubSearchPlaces(WireMockServer server, String name, String lang) throws IOException {
		return server.stubFor(
			get(urlPathEqualTo("/geonames/search"))
			.withQueryParam("maxRows", equalTo("10"))
			.withQueryParam("featureClass", new ExactMatchMultiValuePattern(List.of(
				equalTo("L"), equalTo("P"), equalTo("T"), equalTo("H")
			)))
			.withQueryParam("fuzzy", equalTo("0.6"))
			.withQueryParam("orderby", equalTo("relevance"))
			.withQueryParam("type", equalTo("json"))
			.withQueryParam("lang", equalTo(lang))
			.withQueryParam("name", equalTo(name))
			.withQueryParam("username", equalTo("geo_user"))
			.willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(TestUtils.getResource("geonames/search.json")))
		);
	}
	
}
