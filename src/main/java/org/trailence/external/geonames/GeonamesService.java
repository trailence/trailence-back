package org.trailence.external.geonames;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Service
public class GeonamesService {

	@Value("${trailence.external.geonames.username:}")
	private String username;
	
	@SuppressWarnings("unchecked")
	public Mono<List<List<String>>> findNearbyPlaceName(double lat, double lng, String language) {
		if (username.length() == 0) return Mono.just(List.of());
		WebClient client = WebClient.builder().baseUrl("http://api.geonames.org").build();
		return client.get()
		.uri("/findNearbyPlaceNameJSON?lat={lat}&lng={lng}&lang={lang}&style=full&localCountry=false&username={username}", Map.of("lat", lat, "lng", lng, "lang", language, "username", username))
		.exchangeToMono(response -> response.bodyToMono(Map.class))
		.map(response -> {
			Object geonames = response.get("geonames");
			if (!(geonames instanceof Collection)) return List.of();
			Collection<Object> geonamesCol = (Collection<Object>)geonames;
			List<List<String>> result = new LinkedList<>();
			for (Object element : geonamesCol) {
				if (!(element instanceof Map)) continue;
				Map<String, Object> elementMap = (Map<String, Object>) element;
				List<String> place = new LinkedList<>();
				getPlaceElement(place, elementMap, "toponymName");
				getPlaceElement(place, elementMap, "adminName5");
				getPlaceElement(place, elementMap, "adminName4");
				getPlaceElement(place, elementMap, "adminName3");
				getPlaceElement(place, elementMap, "adminName2");
				getPlaceElement(place, elementMap, "adminName1");
				if (!place.isEmpty())
					result.add(place);
			}
			return result;
		});
	}
	
	private void getPlaceElement(List<String> place, Map<String, Object> elementMap, String attributeName) {
		Object attribute = elementMap.get(attributeName);
		if (attribute instanceof String s) {
			if (s.trim().length() == 0) return;
			if (!place.isEmpty() && s.equals(place.get(place.size() - 1))) return;
			place.add(s);
		}
	}
	
}
