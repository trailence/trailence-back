package org.trailence.trail;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SiteMapService {
	
	private final PublicTrailService service;

	@Value("${trailence.hostname:trailence.org}")
	private String hostname;
	@Value("${trailence.protocol:https}")
	private String protocol;

	private static final byte[] XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes(StandardCharsets.UTF_8);
	private static final byte[] SITEMAP_INDEX_HEADER = "<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n".getBytes(StandardCharsets.UTF_8);
	private static final byte[] SITEMAP_INDEX_FOOTER = "</sitemapindex>".getBytes(StandardCharsets.UTF_8);
	private static final byte[] URLSET_HEADER = "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\" xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9 http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\">\n".getBytes(StandardCharsets.UTF_8);
	private static final byte[] URLSET_FOOTER = "</urlset>".getBytes(StandardCharsets.UTF_8);
	
	private static final String LOC_START = "<loc>";
	private static final String LOC_END = "</loc>\n";
	private static final String LASTMOD_START = "<lastmod>";
	private static final String LASTMOD_END = "</lastmod>\n";
	private static final String URL_START = "<url>\n";
	private static final String URL_END = "</url>\n";
	private static final String SITEMAP_START = "<sitemap>\n";
	private static final String SITEMAP_END = "</sitemap>\n";
	
	private static final int MAX_TRAILS_BY_SITEMAP = 1000; // around 1MB for each 1000 trails
	
	public Flux<byte[]> generateSiteMapIndex() {
		return Flux.concat(
			Mono.just(XML_HEADER),
			Mono.just(SITEMAP_INDEX_HEADER),
			service.count().map(count -> {
				int pages = (int) count.longValue() / MAX_TRAILS_BY_SITEMAP;
				if (count.longValue() % MAX_TRAILS_BY_SITEMAP > 0) pages++;
				StringBuilder s = new StringBuilder();
				for (int page = 0; page < pages; page++) {
					s.append(SITEMAP_START)
						.append(LOC_START)
							.append(protocol).append("://").append(hostname).append("/api/public/trails/v1/sitemaps/").append(page + 1).append("/sitemap.xml")
						.append(LOC_END)
					.append(SITEMAP_END);
				}
				return s.toString().getBytes(StandardCharsets.UTF_8);
			}),
			Mono.just(SITEMAP_INDEX_FOOTER)
		);
	}
	
	public Flux<byte[]> generateSiteMapPage(int page) {
		return Flux.concat(
			Mono.just(XML_HEADER),
			Mono.just(URLSET_HEADER),
			service.slugsWithDate(((long) (page - 1)) * MAX_TRAILS_BY_SITEMAP, MAX_TRAILS_BY_SITEMAP)
			.map(slug -> {
				StringBuilder s = new StringBuilder(2048);
				long ts = slug.getUpdatedAt();
				if (slug.getLatestFeedbackAt() != null && slug.getLatestFeedbackAt().longValue() > ts) ts = slug.getLatestFeedbackAt().longValue();
				if (TrailenceUtils.STARTUP_TIME > ts) ts = TrailenceUtils.STARTUP_TIME;
				String date = DateFormatUtils.ISO_8601_EXTENDED_DATE_FORMAT.format(ts);
				s.append(URL_START);
					s.append(LOC_START);publicUrl("fr", slug.getSlug(), s).append(LOC_END);
					s.append(LASTMOD_START).append(date).append(LASTMOD_END);
					alternate("en", slug.getSlug(), s);
					alternate("fr", slug.getSlug(), s);
				s.append(URL_END);
				s.append(URL_START);
					s.append(LOC_START);publicUrl("en", slug.getSlug(), s).append(LOC_END);
					s.append(LASTMOD_START).append(date).append(LASTMOD_END);
					alternate("en", slug.getSlug(), s);
					alternate("fr", slug.getSlug(), s);
				s.append(URL_END);
				s.append(URL_START);
					s.append(LOC_START).append(protocol).append("://").append(hostname).append("/trail/trailence/").append(slug.getSlug()).append(LOC_END);
					s.append(LASTMOD_START).append(date).append(LASTMOD_END);
				s.append(URL_END);
				return s;
			})
			.buffer(20)
			.map(list -> {
				if (list.isEmpty()) return new byte[0];
				StringBuilder s = list.getFirst();
				for (var i = 1; i < list.size(); ++i) s.append(list.get(i));
				return s.toString().getBytes(StandardCharsets.UTF_8);
			}),
			Mono.just(URLSET_FOOTER)
		);
	}
	
	private StringBuilder publicUrl(String lang, String slug, StringBuilder s) {
		return s.append(protocol).append("://").append(hostname).append('/').append(lang).append("/trail/").append(slug);
	}
	
	private StringBuilder alternate(String lang, String slug, StringBuilder s) {
		s.append("<xhtml:link rel=\"alternate\" hreflang=\"").append(lang).append("\" href=\"");
		publicUrl(lang, slug, s);
		return s.append("\" />\n");
	}
	
}
