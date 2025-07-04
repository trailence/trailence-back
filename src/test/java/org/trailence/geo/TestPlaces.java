package org.trailence.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.trailence.geo.dto.Place;
import org.trailence.test.AbstractTest;
import org.trailence.test.stubs.GeonamesStub;

class TestPlaces extends AbstractTest {

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
	
}
