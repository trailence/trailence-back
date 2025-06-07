package org.trailence.external.outdooractive;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.trailence.global.TrailenceUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@SuppressWarnings("rawtypes")
public class OutdoorActiveService {

	@Value("${trailence.external.outdooractive.clientKey:}")
	private String key;
	@Value("${trailence.external.outdooractive.userRole:}")
	private String userRole;
	
	public boolean configured() {
		return key != null && !key.isEmpty();
	}
	
	public boolean available(Authentication auth) {
		if (!this.configured()) return false;
		if (userRole != null && !userRole.isEmpty()) {
			return TrailenceUtils.hasRole(auth, userRole);
		}
		return true;
	}
	
	public Mono<List<String>> search(double lat, double lng, int radius, int limit, Authentication auth) {
		if (!this.available(auth)) return Mono.just(List.of());
		WebClient client = WebClient.builder().baseUrl("https://www.outdooractive.com").build();
		String queryParams = "?location=" + lng + "," + lat +
			"&radius=" + radius +
			"&sortby=distance&limit=" + Math.min(100, limit) +
			"&len_s=500&len_e=50000" +
			"&key=" + this.key;
		return client.get()
		.uri("/api/project/outdooractive/nearby/tour" + queryParams)
		.header("Accept", "application/json")
		.exchangeToMono(response -> response.bodyToMono(Map.class))
		.map(this::extractIds)
		.flatMap(tourIds -> {
			if (tourIds.size() >= limit) return Mono.just(tourIds);
			return client.get()
			.uri("/api/project/outdooractive/nearby/track" + queryParams)
			.header("Accept", "application/json")
			.exchangeToMono(response -> response.bodyToMono(Map.class))
			.map(this::extractIds)
			.map(trackIds -> {
				ArrayList<String> allIds = new ArrayList<>(tourIds.size() + trackIds.size());
				allIds.addAll(tourIds);
				allIds.addAll(trackIds);
				return allIds;
			});
		});
	}
	
	private List<String> extractIds(Map response) {
		List<String> ids = new LinkedList<>();
		if (response.get("result") instanceof Collection result) {
			for (var item : result) {
				if (item instanceof Map itemMap && itemMap.get("id") instanceof String id) {
					ids.add(id);
				}
			}
		}
		return ids;
	}
	
	@Data
	@NoArgsConstructor
	public static class Rando {
		private String id;
		private String title;
		private Long date;
		private List<Point> points;
		private List<Photo> photos;
		private String description;
		private Double rating;
		private String activity;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Point {
		private double lat;
		private double lng;
		private Double ele;
		private Long time;
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Photo {
		private String id;
		private String title;
		private Point point;
	}
	
	@SuppressWarnings("java:S3776")
	public Mono<List<Rando>> getDetails(List<String> ids, String lang, Authentication auth) {
		if (!this.available(auth)) return Mono.just(List.of());
		WebClient client = WebClient.builder()
			.baseUrl("https://api-oa.com")
			.exchangeStrategies(ExchangeStrategies.builder().codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024)).build())
			.build();
		return client.get()
		.uri("/api/v2/project/outdooractive/contents/" + String.join(",", ids) + "?lang=" + lang + "&display=verbose&key=" + this.key)
		.exchangeToMono(response -> response.bodyToMono(Map.class))
		.map(response -> {
			List<Rando> result = new LinkedList<>();
			if (response.get("answer") instanceof Map answer && answer.get("contents") instanceof Collection contents) {
				for (var content : contents) {
					if (content instanceof Map map) {
						var rando = toDto(map);
						if (rando != null) result.add(rando);
					}
				}
			}
			return result;
		});
	}
	
	@SuppressWarnings("java:S3776")
	private Rando toDto(Map map) {
		Rando rando = new Rando();
		if (map.get("id") instanceof String id) rando.id = id; else return null;
		if (map.get("title") instanceof String s) rando.title = s;
		if (map.get("geoJson") instanceof Map geoJson && geoJson.get("coordinates") instanceof List coordinates) {
			List times = null;
			if (geoJson.get("properties") instanceof Map properties && properties.get("times") instanceof List l) {
				times = l;
			}
			rando.points = new LinkedList<>();
			for (int i = 0; i < coordinates.size(); i++) {
				var coord = coordinates.get(i);
				if (coord instanceof List c) {
					var point = toPoint(c);
					if (point != null) {
						if (times != null && i < times.size() && times.get(i) instanceof Number time)
							point.time = time.longValue();
						rando.points.add(point);
					}
				}
			}
		}
		if (map.get("images") instanceof Collection images) {
			rando.photos = new LinkedList<>();
			for (var imageItem : images) {
				if (imageItem instanceof Map image) {
					Photo p = new Photo();
					if (image.get("id") instanceof String s) p.id = s;
					if (image.get("title") instanceof String s) p.title = s;
					if (image.get("point") instanceof List coord) {
						p.point = toPoint(coord);
					}
					if (p.id != null && !p.id.isEmpty() &&
						(p.point != null ||
						 (image.get("meta") instanceof Map imageMeta && imageMeta.get("source") instanceof Map source && source.get("workflow") != null)))
							rando.photos.add(p);
				}
			}
		}
		if (map.get("texts") instanceof Map texts) {
			if (texts.get("short") instanceof String s && !s.trim().isEmpty())
				rando.description = s;
			if (texts.get("long") instanceof String s) {
				if (rando.description == null)
					rando.description = s;
				else
					rando.description += "<p>" + s + "</p>";
			}
			// "startingPoint" => way point ?
		}
		if (map.get("meta") instanceof Map meta && meta.get("timestamp") instanceof Map ts && ts.get("createdAt") instanceof String s) {
			try {
				rando.date = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s, Instant::from).toEpochMilli();
			} catch (Exception e) {
				// ignore
			}
		}
		if (map.get("communityInfo") instanceof Map communityInfo && communityInfo.get("rating") instanceof Number rating) {
			rando.rating = rating.doubleValue();
		}
		if (map.get("category") instanceof Map category) {
			if (category.get("id") instanceof String categoryId) {
				rando.activity = categoryIdToActivity(categoryId);
			}
		}
		if (rando.points != null && rando.points.size() > 2) return rando;
		return null;
	}
	
	private Point toPoint(List coord) {
		List<Double> values = new ArrayList<>(3);
		for (var v : coord) {
			if (v instanceof Number n) values.add(n.doubleValue());
		}
		if (values.size() >= 2) {
			return new Point(values.get(1), values.get(0), values.size() >= 3 ? values.get(2) : null, null);
		}
		return null;
	}
	
	private String categoryIdToActivity(String id) {
		switch (id) {
		case "5210": case "5710":
			return "walk";
		case "5143": case "5140": case "5141": case "5142": case "5191": case "5643": case "5640": case "5641": case "5642": case "5691":
			return "hiking";
		case "5102": case "5602":
			return "road-biking";
		case "5101": case "5104": case "5262": case "5603": case "5601": case "5604": case "5762":
			return "moutain-biking";
		case "5170": case "5171": case "5670": case "5671":
			return "running";
		case "5190": case "5690":
			return "via-ferrata";
		case "5122": case "5125": case "5622": case "5625":
			return "snowshoeing";
		case "5124": case "5120": case "5323": case "5121": case "5624": case "5620": case "5823": case "5621":
			return "skiing";
		case "5330": case "5830":
			return "rock-climbing";
		case "5061": case "5062": case "5329": case "5339": case "5561": case "5562": case "5829": case "5839":
			return "on-water";
		case "5081": case "5581":
			return "horseback-riding";
		default: return null;
		}
	}
	
}
