package org.trailence.external.geonames;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.trailence.geo.dto.Place;

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
		.uri("/findNearbyPlaceNameJSON?lat={lat}&lng={lng}&lang={lang}&style=full&localCountry=false&username={username}&radius=2", Map.of("lat", lat, "lng", lng, "lang", language, "username", username))
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
	
	@SuppressWarnings("unchecked")
	public Mono<List<Place>> searchPlace(String searchTerm) {
		if (username.length() == 0) return Mono.just(List.of());
		String[] terms = searchTerm.split(" ");
		if (terms.length == 0) return Mono.just(List.of());
		String nameStart = terms[0];
		String name = String.join(" ", Arrays.asList(terms).stream().filter(s -> s.length() > 2).toList());
		if (name.length() < 3) return Mono.just(List.of());
		WebClient client = WebClient.builder().baseUrl("http://api.geonames.org").build();
		return client.get()
		.uri("/search?maxRows=10&featureClass=L&featureClass=P&featureClass=T&featureClass=H&fuzzy=0.5&orderby=relevance&type=json&username=" + username + "&name={name}&name_startsWith={nameStart}", name, nameStart)
		.exchangeToMono(response -> response.bodyToMono(Map.class))
		.map(response -> {
			Object geonames = response.get("geonames");
			if (!(geonames instanceof Collection)) return List.of();
			Collection<Object> geonamesCol = (Collection<Object>)geonames;
			List<Place> result = new LinkedList<>();
			for (Object element : geonamesCol) {
				if (!(element instanceof Map)) continue;
				Map<String, Object> elementMap = (Map<String, Object>) element;
				List<String> names = new LinkedList<>();
				getPlaceElement(names, elementMap, "toponymName");
				getPlaceElement(names, elementMap, "adminName5");
				getPlaceElement(names, elementMap, "adminName4");
				getPlaceElement(names, elementMap, "adminName3");
				getPlaceElement(names, elementMap, "adminName2");
				getPlaceElement(names, elementMap, "adminName1");
				getPlaceElement(names, elementMap, "countryName");
				if (names.isEmpty()) continue;
				double lat;
				double lng;
				try {
					lat = Double.parseDouble(elementMap.get("lat").toString());
					lng = Double.parseDouble(elementMap.get("lng").toString());
				} catch (Exception e) {
					continue;
				}
				result.add(new Place(names, lat, lng));
			}
			return result;
		});
	}
	
}
