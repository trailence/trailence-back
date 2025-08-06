package org.trailence.init.migrations;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.r2dbc.core.binding.MutableBindings;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.init.Migration;
import org.trailence.translations.TranslationService;

import io.r2dbc.postgresql.codec.Json;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

public class AddLanguageAndTranslationsToPublicTrails implements Migration {

	@Override
	public String id() {
		return "0.18_public_trails_language_detect_and_translate";
	}
	
	@Override
	@SuppressWarnings("java:S3776")
	public void execute(R2dbcEntityTemplate db, ApplicationContext context) throws Exception {
		var service = context.getBean(TranslationService.class);
		db.query(
			DbUtils.operation("SELECT uuid, name, description FROM public_trails", null),
			row -> Tuples.of(row.get("uuid", UUID.class), row.get("name", String.class), row.get("description", String.class))
		).all()
		.flatMap(trail ->
			service.detectLanguage(trail.getT3())
			.switchIfEmpty(Mono.just("fr"))
			.flatMap(lang -> {
				if (!lang.equals("fr") && !lang.equals("en")) lang = "fr";
				String target = lang.equals("fr") ? "en" : "fr";
				String l = lang;
				return Mono.zip(
					service.translate(trail.getT2(), lang, target).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty())),
					service.translate(trail.getT3(), lang, target).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
				)
				.flatMap(translations -> {
					var dialect = DialectResolver.getDialect(db.getDatabaseClient().getConnectionFactory());
					MutableBindings bindings = new MutableBindings(dialect.getBindMarkersFactory().create());
					StringBuilder sql = new StringBuilder(512);
					var langBinding = bindings.bind(l);
					sql.append("UPDATE public_trails SET lang = " + langBinding.getPlaceholder());
					if (translations.getT1().isPresent()) {
						try {
							var b = bindings.bind(Json.of(TrailenceUtils.mapper.writeValueAsBytes(Map.of(target, translations.getT1().get()))));
							sql.append(", name_translations = " + b.getPlaceholder());
						} catch (Exception e) { /* ignore */ }
					}
					if (translations.getT2().isPresent()) {
						try {
							var b = bindings.bind(Json.of(TrailenceUtils.mapper.writeValueAsBytes(Map.of(target, translations.getT2().get()))));
							sql.append(", description_translations = " + b.getPlaceholder());
						} catch (Exception e) { /* ignore */ }
					}
					var uuidBinding = bindings.bind(trail.getT1());
					sql.append(" WHERE uuid = " + uuidBinding.getPlaceholder());
					return db.getDatabaseClient().sql(DbUtils.operation(sql.toString(), bindings)).fetch().rowsUpdated();
				});
			})
		)
		.subscribe();
	}
	
}
