package org.trailence.test;

import java.io.IOException;

import org.trailence.global.rest.ApiError;

import io.restassured.response.Response;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestUtils {

	public static void expectError(Response response, int statusCode, String errorCode) {
		if (response.statusCode() != statusCode) {
			throw new AssertionError("Expected status code " + statusCode + ", found " + response.statusCode() + " with body: " + response.getBody().asString());
		}
		var error = response.getBody().as(ApiError.class);
		if (!error.getErrorCode().equals(errorCode)) {
			throw new AssertionError("Expected error code " + errorCode + ", found " + error.getErrorCode() + " with message " + error.getErrorMessage());
		}
	}
	
	public static byte[] getResource(String filename) throws IOException {
		return TestUtils.class.getClassLoader().getResourceAsStream(filename).readAllBytes();
	}
	
}
