package org.trailence.ping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.trailence.test.AbstractTest;

import io.restassured.RestAssured;

class TestPing extends AbstractTest {

	@Test
	void testPing() {
		var response = RestAssured.given().get("/api/ping");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().asString()).isEqualTo("ping");
	}
	
}
