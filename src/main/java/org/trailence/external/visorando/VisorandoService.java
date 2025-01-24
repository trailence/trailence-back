package org.trailence.external.visorando;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.trailence.global.TrailenceUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@Service
public class VisorandoService {
	
	@Value("${trailence.external.visorando.userRole:}")
	private String userRole;
	
	public boolean isAvailable(Authentication auth) {
		if (userRole == null || userRole.isEmpty()) return true;
		return TrailenceUtils.hasRole(auth, userRole);
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Rando {
		private long id;
		private String url;
	}

	@SuppressWarnings({"java:S3776", "java:S3740"})
	public Mono<List<Rando>> searchBbox(String bbox, Authentication auth) {
		if (!this.isAvailable(auth)) return Mono.just(List.of());
		WebClient client = WebClient.builder().baseUrl("https://www.visorando.com").build();
		return client.get()
		.uri("/?component=rando&task=searchCircuitV2&geolocation=0&metaData=&minDuration=0&maxDuration=720&minDifficulty=1&maxDifficulty=5&loc=&retourDepart=0&&bbox=" + bbox)
		.header("X-Requested-With", "XMLHttpRequest")
		.header("Accept", "application/json")
		.exchangeToMono(response -> response.bodyToMono(List.class))
		.map(response -> {
			List<Rando> items = new LinkedList<>();
			for (var item : response) {
				if (item instanceof Map m) {
					var slug = m.get("R_slug");
					var id = m.get("R_id");
					if (slug instanceof String s) {
						if (id instanceof Number n) {
							items.add(new Rando(n.longValue(), "https://www.visorando.com/randonnee-" + s + "/"));
						} else if (id instanceof String ids) {
							try {
								long idn = Long.parseLong(ids);
								items.add(new Rando(idn, "https://www.visorando.com/randonnee-" + s + "/"));
							} catch (Exception e) {
								// ignore
							}
						}
					}
				}
			}
			return items;
		});
	}
	
}
