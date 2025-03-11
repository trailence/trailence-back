package org.trailence.ping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.trailence.test.AbstractTest;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;

class TestPing extends AbstractTest {

	@Test
	void testPing() {
		var response = RestAssured.given().get("/api/ping");
		assertThat(response.statusCode()).isEqualTo(200);
		var body = response.getBody().as(new TypeRef<Map<String, Object>>() {});
		assertThat(body.get("minSupportedVersion")).isNotNull().matches(s -> s.toString().matches("[0-9]+\\.[0-9]+\\.[0-9]+"));
	}
	
}
