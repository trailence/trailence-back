package org.trailence.misc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.trailence.extensions.dto.UserExtension;
import org.trailence.geo.dto.Place;
import org.trailence.preferences.dto.UserPreferences;
import org.trailence.test.AbstractTest;
import org.trailence.test.stubs.GeonamesStub;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;

class TestMisc extends AbstractTest {

	@Test
	void testPing() {
		var response = RestAssured.given().get("/api/ping");
		assertThat(response.statusCode()).isEqualTo(200);
		var body = response.getBody().as(new TypeRef<Map<String, Object>>() {});
		assertThat(body.get("minSupportedVersion")).isNotNull().matches(s -> s.toString().matches("[0-9]+\\.[0-9]+\\.[0-9]+"));
	}
	
	
	@Test
	void testGetAndSetPreferences() {
		var user = test.createUserAndLogin();
		
		var response = user.get("/api/preferences/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		var prefs = response.getBody().as(UserPreferences.class);
		
		assertThat(prefs.getDateFormat()).isNull();
		assertThat(prefs.getDistanceUnit()).isNull();
		assertThat(prefs.getEstimatedBaseSpeed()).isNull();
		assertThat(prefs.getHourFormat()).isNull();
		assertThat(prefs.getLang()).isNull();
		assertThat(prefs.getLongBreakMaximumDistance()).isNull();
		assertThat(prefs.getLongBreakMinimumDuration()).isNull();
		assertThat(prefs.getOfflineMapMaxKeepDays()).isNull();
		assertThat(prefs.getOfflineMapMaxZoom()).isNull();
		assertThat(prefs.getPhotoCacheDays()).isNull();
		assertThat(prefs.getPhotoMaxPixels()).isNull();
		assertThat(prefs.getPhotoMaxQuality()).isNull();
		assertThat(prefs.getPhotoMaxSizeKB()).isNull();
		assertThat(prefs.getTheme()).isNull();
		assertThat(prefs.getTraceMinMeters()).isNull();
		assertThat(prefs.getTraceMinMillis()).isNull();
		
		prefs = new UserPreferences(
			"fr",
			"IMPERIAL",
			"H24",
			"dd/mm/yyyy",
			"DARK",
			1, 5000L,
			30, (short) 17,
			1000L, 1001L, 1002L,
			500, (short) 90, 250, 20,
			"",
			Map.of("hello", 123),
			Map.of("test_filter", Map.of("search", "hello"))
		);
		
		response = user.put("/api/preferences/v1", prefs);
		assertThat(response.statusCode()).isEqualTo(200);
		var prefs2 = response.getBody().as(UserPreferences.class);
		assertThat(prefs2).isEqualTo(prefs);
		
		response = user.get("/api/preferences/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		prefs2 = response.getBody().as(UserPreferences.class);
		assertThat(prefs2).isEqualTo(prefs);
	}
	
	
	@Test
	void testGetPlaces() throws Exception {
		var user = test.createUserAndLogin();
		
		var stub = GeonamesStub.stubGetPlaces(wireMockServer, 1.256d, 2.478d, "fr");
		
		var response = user.get("/api/place/v1?lat=1.256&lng=2.478&lang=fr");
		assertThat(response.statusCode()).isEqualTo(200);
		var places = response.getBody().as(String[][].class);
		assertThat(places).isEqualTo(new String[][] { new String[] { "Les Adrets-de-l'Estérel", "Draguignan", "Var", "Provence-Alpes-Côte d'Azur" } });
		
		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);
	}
	
	@Test
	void testSearchPlaces() throws Exception {
		var user = test.createUserAndLogin();
		
		var stub = GeonamesStub.stubSearchPlaces(wireMockServer, "Antibes", "fr");
		
		var response = user.get("/api/place/v1/search?lang=fr&terms=Antibes");
		assertThat(response.statusCode()).isEqualTo(200);
		var places = response.getBody().as(Place[].class);
		assertThat(places).hasSize(10);
		assertThat(places[0]).isEqualTo(
			new Place(
				List.of("Antibes", "Provence-Alpes-Côte d'Azur", "France"),
				Double.valueOf("43.58127"), Double.valueOf("7.12487"),
				Double.valueOf("43.616962436963014"), Double.valueOf("43.54557756303699"), Double.valueOf("7.17417115075293"), Double.valueOf("7.075568849247069")
			)
		);
		assertThat(places[1]).isEqualTo(
			new Place(
				List.of("Antilles"),
				Double.valueOf("18.73333"), Double.valueOf("-69.15"),
				Double.valueOf("23.277067409"), Double.valueOf("10.635925244000077"), Double.valueOf("-60.8096896756103"), Double.valueOf("-84.952245207")
			)
		);
		
		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);
	}


	@Test
	void testExtensions() {
		var user = test.createUserAndLogin();
		
		var response = user.post("/api/extensions/v1", List.of());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).isEmpty();
		
		response = user.post("/api/extensions/v1", List.of(new UserExtension(0, "thunderforest.com", Map.of("apikey", "0123456789abcdef0123456789abcdef"))));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).isEmpty();
		
		test.asAdmin().setUserRoles(user.getEmail(), List.of("thunderforest"));
		var authResponse = user.renewToken();
		assertThat(authResponse.getRoles()).singleElement().isEqualTo("thunderforest");
		
		response = user.post("/api/extensions/v1", List.of(new UserExtension(0, "thunderforest.com", Map.of("apikey", "0123456789abcdef0123456789abcdef"))));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).singleElement().isEqualTo(new UserExtension(1, "thunderforest.com", Map.of("apikey", "0123456789abcdef0123456789abcdef")));
		
		response = user.post("/api/extensions/v1", List.of(new UserExtension(0, "thunderforest.com", Map.of("apikey", "123456789abcdef0123456789abcdef0"))));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).singleElement().isEqualTo(new UserExtension(1, "thunderforest.com", Map.of("apikey", "0123456789abcdef0123456789abcdef")));
		
		response = user.post("/api/extensions/v1", List.of(new UserExtension(1, "thunderforest.com", Map.of("apikey", "12345"))));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).singleElement().isEqualTo(new UserExtension(1, "thunderforest.com", Map.of("apikey", "0123456789abcdef0123456789abcdef")));
		
		response = user.post("/api/extensions/v1", List.of(new UserExtension(1, "thunderforest.com", Map.of("apikey", "123456789abcdef0123456789abcdef0"))));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).singleElement().isEqualTo(new UserExtension(2, "thunderforest.com", Map.of("apikey", "123456789abcdef0123456789abcdef0")));
		
		response = user.post("/api/extensions/v1", List.of(
			new UserExtension(2, "thunderforest.com", Map.of("apikey", "23456789abcdef0123456789abcdef01")),
			new UserExtension(0, "unknown", Map.of("apikey", "123456"))
		));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).singleElement().isEqualTo(new UserExtension(3, "thunderforest.com", Map.of("apikey", "23456789abcdef0123456789abcdef01")));
		
		response = user.post("/api/extensions/v1", List.of(
			new UserExtension(3, "thunderforest.com", Map.of("apikey2", "3456789abcdef0123456789abcdef012")),
			new UserExtension(0, "unknown", Map.of("apikey", "3456789abcdef0123456789abcdef012"))
		));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).singleElement().isEqualTo(new UserExtension(3, "thunderforest.com", Map.of("apikey", "23456789abcdef0123456789abcdef01")));
		
		response = user.post("/api/extensions/v1", List.of(
			new UserExtension(3, "thunderforest.com", Map.of("apikey", "3456789abcdef0123456789abcdef012", "wrong", "value")),
			new UserExtension(0, "unknown", Map.of("apikey", "3456789abcdef0123456789abcdef012"))
		));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).singleElement().isEqualTo(new UserExtension(3, "thunderforest.com", Map.of("apikey", "23456789abcdef0123456789abcdef01")));

		response = user.post("/api/extensions/v1", List.of(new UserExtension(-1, "thunderforest.com", Map.of("apikey", "23456789abcdef0123456789abcdef01"))));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(UserExtension[].class)).isEmpty();
	}
	
}
