package org.trailence.trail.rest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.UnauthorizedException;
import org.trailence.trail.PublicTrailService;
import org.trailence.trail.dto.MyPublicTrail;
import org.trailence.trail.dto.PublicTrack;
import org.trailence.trail.dto.PublicTrail;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsResponse;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/public/trails/v1")
@RequiredArgsConstructor
public class PublicTrailV1Controller {
	
	private final PublicTrailService service;

	@PostMapping("/countByTile")
	public Mono<SearchByTileResponse> searchByTiles(@RequestBody SearchByTileRequest request) {
		return service.searchByTiles(request);
	}
	
	@PostMapping("/searchByBounds")
	public Mono<SearchByBoundsResponse> searchByBounds(@RequestBody SearchByBoundsRequest request) {
		return service.searchByBounds(request);
	}
	
	@GetMapping("/trailById/{uuid}")
	public Mono<PublicTrail> getById(@PathVariable("uuid") String uuid, Authentication auth) {
		return service.getById(uuid, auth);
	}
	
	@GetMapping("/trailBySlug/{slug}")
	public Mono<PublicTrail> getBySlug(@PathVariable("slug") String slug, Authentication auth) {
		return service.getBySlug(slug, auth);
	}
	
	@PostMapping("/trailsByIds")
	public Mono<List<PublicTrail>> getByIds(@RequestBody List<String> uuids, Authentication auth) {
		return service.getByIds(uuids, auth);
	}

	@GetMapping("/mine")
	public Flux<MyPublicTrail> getMines(Authentication auth) {
		if (auth == null) return Flux.error(new UnauthorizedException());
		return service.getMines(auth);
	}
	
	private static final long PHOTO_CACHE_SECONDS = 100L * 24 * 60 * 60;
	
	@GetMapping("/photo/{trailUuid}/{photoUuid}")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getPhotoContent(
		@PathVariable("trailUuid") String trailUuid,
		@PathVariable("photoUuid") String photoUuid
	) {
		return service.getPhotoFileContent(trailUuid, photoUuid)
		.map(flux -> ResponseEntity
			.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.header("Cache-Control", "public, max-age=" + PHOTO_CACHE_SECONDS)
			.header("Expires", Instant.now().plusSeconds(PHOTO_CACHE_SECONDS).toString())
			.body(flux)
		);
	}
	
	@GetMapping("/track/{trailUuid}")
	public Mono<PublicTrack> getTrack(@PathVariable("trailUuid") String trailUuid) {
		return service.getTrack(trailUuid);
	}
	
	@GetMapping(value = "/random", produces = {"text/html"})
	public Mono<String> random() {
		return service.random()
		.collectList()
		.map(trails -> {
			StringBuilder html = new StringBuilder(65536);
			html.append("<html>")
			.append("<head>")
			.append("<meta charset=\"utf-8\">")
			.append("<title>Random Trails / Randonnées au hasard</title>")
			.append("<style>body { font-family: \"Roboto\", \"Helvetica Neue\", sans-serif; font-size: 14px; } h2 { font-size: 20px; }</style>")
			.append("</head>")
			.append("<body>");
			for (var trail : trails) {
				html.append("<h2>").append(StringEscapeUtils.escapeHtml4(trail.getName())).append("</h2>");
				html.append("<p>").append(StringEscapeUtils.escapeHtml4(trail.getDescription()).replace("\n", "<br/>")).append("</p>");
				html.append("<p>");
				html.append("Location: ").append(trail.getLocation()).append("<br/>");
				html.append("Distance: ").append(trail.getDistance() / 1000).append('.').append((trail.getDistance() % 1000) / 10).append(" km<br/>");
				html.append("</p>");
				html.append("<ul>");
				html.append("<li><a href=\"/fr/trail/").append(trail.getSlug()).append("\">Fiche en français</a></li>");
				html.append("<li><a href=\"/en/trail/").append(trail.getSlug()).append("\">Trail in english</a></li>");
				html.append("</ul>");
				html.append("<hr/>");
			}
			html.append("</body></html>");
			return html.toString();
		});
	}
	
	private static final byte[] SITEMAP_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\" xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9 http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\">\n".getBytes(StandardCharsets.UTF_8);
	private static final byte[] SITEMAP_FOOTER = "</urlset>".getBytes(StandardCharsets.UTF_8);
	
	@GetMapping("/sitemap.xml")
	public Mono<ResponseEntity<Flux<DefaultDataBuffer>>> sitemap(ServerHttpRequest request) {
		String baseUrl = "https://trailence.org/";
		return Mono.just(ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_XML)
			.body(Flux.concat(
				Mono.just(DefaultDataBufferFactory.sharedInstance.wrap(SITEMAP_HEADER)),
				service.allSlugs().map(slug -> {
					StringBuilder s = new StringBuilder(2048);
					long ts = slug.getUpdatedAt();
					if (slug.getLatestFeedbackAt() != null && slug.getLatestFeedbackAt().longValue() > ts) ts = slug.getLatestFeedbackAt().longValue();
					if (TrailenceUtils.STARTUP_TIME > ts) ts = TrailenceUtils.STARTUP_TIME;
					String date = DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.format(ts);
					s.append("<url>\n");
						s.append("<loc>").append(baseUrl).append("fr/trail/").append(slug.getSlug()).append("</loc>\n");
						s.append("<lastmod>").append(date).append("</lastmod>\n");
						s.append("<xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"").append(baseUrl).append("en/trail/").append(slug.getSlug()).append("\" />\n");
						s.append("<xhtml:link rel=\"alternate\" hreflang=\"fr\" href=\"").append(baseUrl).append("fr/trail/").append(slug.getSlug()).append("\" />\n");
					s.append("</url>\n");
					s.append("<url>\n");
						s.append("<loc>").append(baseUrl).append("en/trail/").append(slug.getSlug()).append("</loc>\n");
						s.append("<lastmod>").append(date).append("</lastmod>\n");
						s.append("<xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"").append(baseUrl).append("en/trail/").append(slug.getSlug()).append("\" />\n");
						s.append("<xhtml:link rel=\"alternate\" hreflang=\"fr\" href=\"").append(baseUrl).append("fr/trail/").append(slug.getSlug()).append("\" />\n");
					s.append("</url>\n");
					s.append("<url>\n");
						s.append("<loc>").append(baseUrl).append("trail/trailence/").append(slug.getSlug()).append("</loc>\n");
						s.append("<lastmod>").append(date).append("</lastmod>\n");
					s.append("</url>\n");
					return s;
				})
				.buffer(20)
				.map(list -> {
					if (list.isEmpty()) return DefaultDataBufferFactory.sharedInstance.wrap(new byte[0]);
					StringBuilder s = list.getFirst();
					for (var i = 1; i < list.size(); ++i) s.append(list.get(i));
					return DefaultDataBufferFactory.sharedInstance.wrap(s.toString().getBytes(StandardCharsets.UTF_8));
				}),
				Mono.just(DefaultDataBufferFactory.sharedInstance.wrap(SITEMAP_FOOTER))
			))
		);
	}
	
	@GetMapping("/examples")
	public Mono<List<String>> searchExamples(@RequestParam(name = "nb", required = false, defaultValue = "5") int nb) {
		return service.searchExamples(nb);
	}
	
}
