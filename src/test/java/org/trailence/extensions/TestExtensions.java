package org.trailence.extensions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.trailence.extensions.dto.UserExtension;
import org.trailence.test.AbstractTest;

class TestExtensions extends AbstractTest {

	@Test
	void test() {
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
