package org.trailence.trail;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.geo.Box;
import org.springframework.data.geo.Point;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.relational.core.sql.CaseExpression;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Expressions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.When;
import org.springframework.r2dbc.core.binding.MutableBindings;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.DivideFloatExpression;
import org.trailence.global.db.MinusExpression;
import org.trailence.global.db.MultiplyExpression;
import org.trailence.global.db.PlusExpression;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.notifications.NotificationsService;
import org.trailence.preferences.UserCommunityService;
import org.trailence.preferences.dto.UserCommunity;
import org.trailence.storage.FileService;
import org.trailence.trail.TrackStorage.V1.StoredData;
import org.trailence.trail.db.ModerationMessageEntity;
import org.trailence.trail.db.ModerationMessageRepository;
import org.trailence.trail.db.PublicPhotoEntity;
import org.trailence.trail.db.PublicPhotoRepository;
import org.trailence.trail.db.PublicTrackEntity;
import org.trailence.trail.db.PublicTrackRepository;
import org.trailence.trail.db.PublicTrailEntity;
import org.trailence.trail.db.PublicTrailRepository;
import org.trailence.trail.db.PublicTrailRepository.SlugAndDate;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.dto.CreatePublicTrailRequest;
import org.trailence.trail.dto.MyPublicTrail;
import org.trailence.trail.dto.PatchPublicTrailRequest;
import org.trailence.trail.dto.PublicTrack;
import org.trailence.trail.dto.PublicTrail;
import org.trailence.trail.dto.PublicTrailSearch;
import org.trailence.trail.dto.PublicTrailSearch.Filters;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsResponse;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileResponse;
import org.trailence.trail.dto.UserTrails;
import org.trailence.trail.exceptions.PublicTrailNotFound;
import org.trailence.trail.exceptions.TrailNotFound;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;
import tools.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicTrailService {
	
	private final PublicTrailRepository publicTrailRepo;
	private final PublicPhotoRepository publicPhotoRepo;
	private final PublicTrackRepository publicTrackRepo;
	private final TrailRepository trailRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final PhotoService photoService;
	private final TrailService trailService;
	private final FileService fileService;
	private final NotificationsService notificationsService;
	private final ModerationMessageRepository messageRepo;
	private final FeedbackService feedbackService;
	private final UserCommunityService userCommunityService;
	
	private static final Map<String, String> TEXT_SEARCH_LANGS = Map.of("fr", "french", "en", "english");
	
	@Transactional
	public Mono<String> create(CreatePublicTrailRequest request, Authentication auth) {
		String author = request.getAuthor().toLowerCase();
		if (author.equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		String authorUuid = request.getAuthorUuid();
		Mono<Tuple3<UUID, String, Optional<PublicTrailEntity>>> newData;
		if (authorUuid == null) newData = generateSlug(request.getName()).map(slug -> Tuples.of(UUID.randomUUID(), slug, Optional.empty()));
		else newData = publicTrailRepo.findFirst1ByAuthorAndAuthorUuid(author, UUID.fromString(authorUuid))
			.flatMap(existing -> 
				publicTrackRepo.deleteById(existing.getUuid())
				.then(publicPhotoRepo.deleteAllByTrailUuid(existing.getUuid()))
				.then(publicTrailRepo.deleteById(existing.getUuid()))
				.then(userCommunityService.removePublication(author))
				.then(Mono.just(Tuples.of(existing.getUuid(), existing.getSlug(), Optional.of(existing))))
			)
			.switchIfEmpty(Mono.defer(() -> generateSlug(request.getName()).map(slug -> Tuples.of(UUID.randomUUID(), slug, Optional.empty()))));
		return newData
		.flatMap(tuple ->
			trailRepo.findTrailToReview(UUID.fromString(request.getTrailUuid()), author)
			.switchIfEmpty(Mono.error(new TrailNotFound(request.getTrailUuid(), author)))
			.flatMap(fromTrail ->
				r2dbc.insert(toTrackEntity(tuple.getT1(), request))
				.then(Flux.fromIterable(request.getPhotos()).flatMap(p -> photoService.transferToPublic(UUID.fromString(p.getUuid()), author, tuple.getT1(), p), 1, 1).then())
				.then(r2dbc.insert(toTrailEntity(tuple.getT1(), tuple.getT2(), tuple.getT3().orElse(null), request)).flatMap(this::updateTextSearch))
				.then(trailService.delete(Flux.just(fromTrail), author))
				.then(notificationsService.create(author, "publications.accepted", List.of(request.getName(), tuple.getT1().toString())))
				.then(userCommunityService.addPublication(author))
			)
			.thenReturn(tuple.getT1().toString())
		);
	}
	
	private Mono<String> generateSlug(String name) {
		name = StringUtils.strip(name.trim());
		name = StringUtils.stripAccents(name);
		name = name.replace(' ', '_').toLowerCase(Locale.US);
		name = URLEncoder.encode(name, StandardCharsets.UTF_8);
		if (name.length() > 190) name = name.substring(0, 190);
		if (name.isEmpty()) name = "trail";
		String initialSlug = name;
		return publicTrailRepo.existsBySlug(initialSlug)
		.flatMap(exists -> {
			if (!exists.booleanValue()) return Mono.just(initialSlug);
			return getNumberedTrailSlugUsed(initialSlug)
			.map(tuple -> {
				long mi = tuple.getT1();
				if (mi > 1) return initialSlug + "-" + (mi - 1);
				long ma = tuple.getT2();
				return initialSlug + "-" + (ma + 1);
			});
		});
	}
	
	private Mono<Tuple2<Long, Long>> getNumberedTrailSlugUsed(String initialSlug) {
		AtomicLong min = new AtomicLong(0L);
		AtomicLong max = new AtomicLong(0L);
		int l = initialSlug.length();
		return publicTrailRepo.findAllSlugsStartingWith(initialSlug + "-").doOnNext(slug -> {
			if (slug.length() <= l + 1) return;
			String end = slug.substring(l + 1);
			try {
				long val = Long.parseLong(end);
				if (val > 0) {
					min.getAndUpdate(m -> m > val ? val : m);
					max.getAndUpdate(m -> m < val ? val : m);
				}
			} catch (Exception _) {
				// ignore
			}
		}).then(Mono.fromSupplier(() -> Tuples.of(min.get(), max.get())));
	}
	
	private PublicTrailEntity toTrailEntity(UUID uuid, String slug, PublicTrailEntity existing, CreatePublicTrailRequest request) {
		long now = System.currentTimeMillis();
		Json nameTranslations = null;
		Json descriptionTranslations = null;
		try {
			nameTranslations = Json.of(TrailenceUtils.mapper.writeValueAsBytes(request.getNameTranslations()));
		} catch (Exception e) {
			log.error("Mapping error", e);
		}
		try {
			descriptionTranslations = Json.of(TrailenceUtils.mapper.writeValueAsBytes(request.getDescriptionTranslations()));
		} catch (Exception e) {
			log.error("Mapping error", e);
		}
		return new PublicTrailEntity(
			uuid,
			request.getAuthor().toLowerCase(),
			request.getAuthorUuid() != null ? UUID.fromString(request.getAuthorUuid()) : null,
			existing != null ? existing.getCreatedAt() : now,
			now,
			slug,
			request.getName(),
			request.getDescription(),
			request.getLocation(),
			request.getDate(),
			request.getDistance(),
			request.getPositiveElevation(),
			request.getNegativeElevation(),
			request.getHighestAltitude(),
			request.getLowestAltitude(),
			request.getDuration(),
			request.getBreaksDuration(),
			request.getEstimatedDuration(),
			request.getLoopType(),
			request.getActivity(),
			new Box(new Point(request.getBoundsWest(), request.getBoundsNorth()), new Point(request.getBoundsEast(), request.getBoundsSouth())),
			request.getTile128ByZoom().get(0),
			request.getTile128ByZoom().get(1),
			request.getTile128ByZoom().get(2),
			request.getTile128ByZoom().get(3),
			request.getTile128ByZoom().get(4),
			request.getTile128ByZoom().get(5),
			request.getTile128ByZoom().get(6),
			request.getTile128ByZoom().get(7),
			request.getTile128ByZoom().get(8),
			request.getTile128ByZoom().get(9),
			request.getSimplifiedPath().stream().map(v -> (int) Math.floor(v * 1000000)).toArray(size -> new Integer[size]),
			existing != null ? existing.getNbRate0() : 0L,
			existing != null ? existing.getNbRate1() : 0L,
			existing != null ? existing.getNbRate2() : 0L,
			existing != null ? existing.getNbRate3() : 0L,
			existing != null ? existing.getNbRate4() : 0L,
			existing != null ? existing.getNbRate5() : 0L,
			request.getLang(),
			nameTranslations,
			descriptionTranslations,
			request.getSourceUrl()
		);
	}
	
	private Mono<Void> updateTextSearch(PublicTrailEntity entity) {
		var dialect = DialectResolver.getDialect(r2dbc.getDatabaseClient().getConnectionFactory());
		MutableBindings bindings = new MutableBindings(dialect.getBindMarkersFactory().create());
		
		Map<String, String> nameTranslations = new HashMap<>();
		try {
			nameTranslations = TrailenceUtils.mapper.readValue(entity.getNameTranslations().asArray(), new TypeReference<Map<String, String>>() {});
		} catch (Exception e) {
			log.error("Mapping error", e);
		}
		List<Tuple3<String, String, String>> searchTexts = new LinkedList<>();
		for (var entry : TEXT_SEARCH_LANGS.entrySet()) {
			String langCode = entry.getKey();
			String langName = entry.getValue();
			String value;
			if (langCode.equals(entity.getLang())) value = entity.getName();
			else if (nameTranslations.containsKey(langCode)) value = nameTranslations.get(langCode);
			else value = entity.getName();
			if (entity.getLocation() != null) value += " " + entity.getLocation();
			searchTexts.add(Tuples.of(langCode, langName, value));
		}
		
		StringBuilder sql = new StringBuilder(256);
		sql.append("UPDATE ").append(PublicTrailEntity.TABLE.toString());
		sql.append(" SET ");
		boolean first = true;
		for (var lang : searchTexts) {
			var marker = bindings.bind(lang.getT3());
			if (first) first = false; else sql.append(',');
			sql.append("search_text_").append(lang.getT1()).append(" = to_tsvector('").append(lang.getT2()).append("', unaccent(lower(").append(marker.getPlaceholder()).append(")))");
		}
		sql.append(" WHERE ").append(PublicTrailEntity.COL_UUID.toString()).append(" = '").append(entity.getUuid().toString()).append('\'');
		
		return r2dbc.getDatabaseClient().sql(DbUtils.operation(sql.toString(), bindings)).fetch().rowsUpdated().then();
	}
	
	private static PublicTrackEntity toTrackEntity(UUID uuid, CreatePublicTrailRequest request) {
		try {
			return new PublicTrackEntity(
				uuid,
				TrackStorage.V1V2Bridge.v1DtoToV2(new StoredData(request.getFullTrack(), request.getWayPoints()))
			);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public Mono<SearchByTileResponse> searchByTiles(SearchByTileRequest request) {
		if (request.getZoom() < 1 || request.getZoom() > 10) return Mono.error(new BadRequestException("invalid-zoom", "Invalid zoom level"));
		if (request.getTiles().size() > 1000) return Mono.error(new BadRequestException("too-many-tiles", "Maximum 1000 tiles by request"));

		Column zoomColumn = Column.create("tile_zoom" + request.getZoom(), PublicTrailEntity.TABLE);
		
		var dialect = DialectResolver.getDialect(r2dbc.getDatabaseClient().getConnectionFactory());
		MutableBindings bindings = new MutableBindings(dialect.getBindMarkersFactory().create()); 
		Condition where = getConditionOnTilesAndFilters(zoomColumn, request.getTiles(), request.getFilters(), bindings);
		
		String sql = new SqlBuilder()
		.select(
			Expressions.just("count(*) as nb_trails"),
			zoomColumn.as("tile")
		)
		.from(PublicTrailEntity.TABLE)
		.where(where)
		.groupBy(zoomColumn)
		.build();

		return r2dbc.query(DbUtils.operation(sql, bindings), row -> new PublicTrailSearch.NbTrailsByTile(row.get("tile", Integer.class), row.get("nb_trails", Long.class)))
			.all().collectList()
			.flatMap(counts -> {
				SearchByTileResponse response = new SearchByTileResponse(counts, null);
				Integer maxCount = request.getReturnUuidsWhenLessThan();
				if (maxCount != null && (maxCount.intValue() < 1 || maxCount.intValue() > 200)) maxCount = null;
				if (maxCount == null) return Mono.just(response);
				long total = 0;
				List<Integer> tiles = new LinkedList<>();
				for (var tile : counts) {
					total += tile.getNbTrails();
					tiles.add(tile.getTile());
				}
				if (total == 0 || total > maxCount.longValue()) return Mono.just(response);
				return getUuidsFromTilesSearch(zoomColumn, tiles, request.getFilters())
				.map(uuids -> {
					response.setUuids(uuids);
					return response;
				});
			});
	}
	
	private Mono<List<String>> getUuidsFromTilesSearch(Column zoomColumn, List<Integer> tiles, Filters filters) {
		var dialect = DialectResolver.getDialect(r2dbc.getDatabaseClient().getConnectionFactory());
		MutableBindings bindings = new MutableBindings(dialect.getBindMarkersFactory().create()); 
		String sql = new SqlBuilder()
		.select(PublicTrailEntity.COL_UUID)
		.from(PublicTrailEntity.TABLE)
		.where(getConditionOnTilesAndFilters(zoomColumn, tiles, filters, bindings))
		.build();
		return r2dbc.query(DbUtils.operation(sql, bindings), row -> row.get(0, UUID.class).toString())
		.all().collectList();
	}
	
	private Condition getConditionOnTilesAndFilters(Column zoomColumn, List<Integer> tiles, Filters filters, MutableBindings bindings) {
		Condition where = Conditions.in(zoomColumn,tiles.stream().map(SQL::literalOf).toList());
		if (filters != null) {
			where = applyFilterNumeric(where, filters.getDuration(), new MinusExpression(PublicTrailEntity.COL_DURATION, PublicTrailEntity.COL_BREAKS_DURATION));
			where = applyFilterNumeric(where, filters.getEstimatedDuration(), PublicTrailEntity.COL_ESTIMATED_DURATION);
			where = applyFilterNumeric(where, filters.getDistance(), PublicTrailEntity.COL_DISTANCE);
			where = applyFilterNumeric(where, filters.getPositiveElevation(), PublicTrailEntity.COL_POSITIVE_ELEVATION);
			where = applyFilterNumeric(where, filters.getNegativeElevation(), PublicTrailEntity.COL_NEGATIVE_ELEVATION);
			where = applyFilterList(where, filters.getLoopTypes(), PublicTrailEntity.COL_LOOP_TYPE);
			where = applyFilterList(where, filters.getActivities(), PublicTrailEntity.COL_ACTIVITY);
			where = applyFilterRate(where, filters.getRate());
			if (filters.getTextSearch() != null && filters.getTextSearchLang() != null && !filters.getTextSearch().isBlank() && TEXT_SEARCH_LANGS.keySet().contains(filters.getTextSearchLang())) {
				var marker = bindings.bind(filters.getTextSearch());
				where = where.and(
					Conditions.just(
						"search_text_" + filters.getTextSearchLang() +
						" @@ websearch_to_tsquery('" + TEXT_SEARCH_LANGS.get(filters.getTextSearchLang()) + "', unaccent(lower(" + marker.getPlaceholder() + ")))"
					)
				);
			}
		}
		return where;
	}
	
	private Condition applyFilterNumeric(Condition where, PublicTrailSearch.FilterNumeric filter, Expression valueExpression) {
		if (filter == null) return where;
		if (filter.getFrom() == null && filter.getTo() == null) return where;
		if (filter.getFrom() != null)
			where = where.and(Conditions.isGreaterOrEqualTo(valueExpression, SQL.literalOf(filter.getFrom())));
		if (filter.getTo() != null)
			where = where.and(Conditions.isLessOrEqualTo(valueExpression, SQL.literalOf(filter.getFrom())));
		return where;
	}
	
	private Condition applyFilterList(Condition where, List<String> values, Expression valueExpression) {
		if (values == null || values.isEmpty()) return where;
		where = where.and(Conditions.in(valueExpression, values.stream().map(SQL::literalOf).toList()));
		return where;
	}
	
	private Condition applyFilterRate(Condition where, PublicTrailSearch.FilterNumeric filter) {
		if (filter == null) return where;
		if (filter.getFrom() == null && filter.getTo() == null) return where;
		Expression ratesCount = new PlusExpression(
			PublicTrailEntity.COL_NB_RATE0,
			new PlusExpression(
				PublicTrailEntity.COL_NB_RATE1,
				new PlusExpression(
					PublicTrailEntity.COL_NB_RATE2,
					new PlusExpression(
						PublicTrailEntity.COL_NB_RATE3,
						new PlusExpression(
							PublicTrailEntity.COL_NB_RATE4,
							PublicTrailEntity.COL_NB_RATE5
						)
					)
				)
			)
		);
		Expression ratesTotal = new PlusExpression(
			PublicTrailEntity.COL_NB_RATE1,
			new PlusExpression(
				new MultiplyExpression(PublicTrailEntity.COL_NB_RATE2, SQL.literalOf(2)),
				new PlusExpression(
					new MultiplyExpression(PublicTrailEntity.COL_NB_RATE3, SQL.literalOf(3)),
					new PlusExpression(
						new MultiplyExpression(PublicTrailEntity.COL_NB_RATE4, SQL.literalOf(4)),
						new MultiplyExpression(PublicTrailEntity.COL_NB_RATE5, SQL.literalOf(5))
					)
				)
			)
		);
		Expression rateValue = new DivideFloatExpression(ratesTotal, ratesCount);
		if (filter.getFrom() != null) {
			where = where.and(Conditions.isGreater(ratesCount, SQL.literalOf(0)));
			where = where.and(Conditions.isGreaterOrEqualTo(rateValue, SQL.literalOf(filter.getFrom())));
			if (filter.getTo() != null) {
				where = where.and(Conditions.isLessOrEqualTo(rateValue, SQL.literalOf(filter.getTo())));
			}
		} else {
			where = where.and(
				Conditions.isEqual(
					CaseExpression.create(
						When.when(Conditions.isEqual(ratesCount, SQL.literalOf(0)), SQL.literalOf(true))
					)
					.elseExpression(Conditions.isLessOrEqualTo(rateValue, SQL.literalOf(filter.getTo()))),
					SQL.literalOf(true)
				)
			);
		}
		return where;
	}
	
	public Mono<SearchByBoundsResponse> searchByBounds(SearchByBoundsRequest request) {
		int limit = request.getMaxResults() != null ? request.getMaxResults() : 200;
		if (limit < 1) limit = 1;
		if (limit > 200) limit = 200;
		int l = limit;
		
		String sql = new SqlBuilder()
		.select(PublicTrailEntity.COL_UUID)
		.from(PublicTrailEntity.TABLE)
		.where(Conditions.just("bounds && box '((" + request.getWest() + "," + request.getNorth() + "),(" + request.getEast() + "," + request.getSouth() + "))'"))
		.limit(l + 1)
		.build();
		
		return r2dbc.query(DbUtils.operation(sql, null), row -> row.get("uuid", UUID.class).toString()).all()
		.collectList()
		.map(uuids -> new SearchByBoundsResponse(
			uuids.size() <= l ? uuids : uuids.subList(0, l),
			uuids.size() > l
		));
	}
	
	public Mono<List<String>> getAllIds(long offset, int limit) {
		String sql = new SqlBuilder()
		.select(PublicTrailEntity.COL_UUID)
		.from(PublicTrailEntity.TABLE)
		.orderBy(List.of(Order.asc("uuid")), Map.of("uuid", "uuid"))
		.offset(offset)
		.limit(limit)
		.build();
		
		return r2dbc.query(DbUtils.operation(sql, null), row -> row.get("uuid", UUID.class).toString()).all()
		.collectList();
	}
	
	public Mono<PublicTrail> getById(String uuid, Authentication auth) {
		return publicTrailRepo.findById(UUID.fromString(uuid))
		.switchIfEmpty(Mono.error(new PublicTrailNotFound(uuid)))
		.flatMap(trail -> toDto(trail, auth));
	}
	
	public Mono<PublicTrail> getBySlug(String slug, Authentication auth) {
		return publicTrailRepo.findOneBySlug(slug)
		.switchIfEmpty(Mono.defer(() -> {
			String slug2 = URLEncoder.encode(slug, StandardCharsets.UTF_8);
			if (slug2.equals(slug)) return Mono.empty();
			return publicTrailRepo.findOneBySlug(slug2)
			.switchIfEmpty(Mono.error(new PublicTrailNotFound(slug)));
		}))
		.flatMap(trail -> toDto(trail, auth));
	}
	
	public Mono<List<PublicTrail>> getByIds(List<String> uuids, Authentication auth) {
		if (uuids.size() > 200) return Mono.error(new BadRequestException("too-many-trails", "Maximum 200 trails"));
		return publicTrailRepo.findAllById(uuids.stream().map(UUID::fromString).toList())
		.collectList()
		.flatMap(trails -> toDtos(trails, auth));
	}

	private Mono<PublicTrail> toDto(PublicTrailEntity entity, Authentication auth) {
		return publicPhotoRepo.findAllByTrailUuid(entity.getUuid()).collectList()
		.flatMap(photos ->
			userCommunityService.getUserCommunity(entity.getAuthor())
			.map(userCommunity -> this.toPublicTrailDto(entity, photos.stream(), userCommunity, auth))
		);
	}
	
	private Mono<List<PublicTrail>> toDtos(List<PublicTrailEntity> trails, Authentication auth) {
		return publicPhotoRepo.findAllByTrailUuidIn(trails.stream().map(PublicTrailEntity::getUuid).toList()).collectList()
		.flatMap(photos ->
			userCommunityService.getUsersCommunity(trails.stream().map(t -> t.getAuthor()).toList()).collectList()
			.map(usersCommunity ->
				trails.stream()
				.map(trail -> this.toPublicTrailDto(
					trail,
					photos.stream().filter(p -> p.getTrailUuid().equals(trail.getUuid())),
					usersCommunity.stream().filter(a -> a.getEmail().equals(trail.getAuthor())).findAny().orElse(new UserCommunity()),
					auth
				))
				.toList()
			)
		);
	}
	
	public Flux<MyPublicTrail> getMines(Authentication auth) {
		String user = TrailenceUtils.email(auth);
		return publicTrailRepo.findMyPublicTrails(user);
	}
	
	public Mono<UserTrails> getUserTrails(String userPublicId, Authentication auth) {
		ValidationUtils.field("userId", userPublicId).notBlank().isUuid();
		return userCommunityService.getUserCommunityFromPublicId(userPublicId)
		.flatMap(user ->
			publicTrailRepo.findIdsByAuthor(user.getEmail()).map(UUID::toString).collectList()
			.map(ids -> new UserTrails(ids, user.getAlias(), user.getAvatar(), user.getNbPublications(), user.getNbComments(), user.getNbRates()))
		)
		.switchIfEmpty(Mono.defer(() -> Mono.just(new UserTrails(List.of(), null, null, null, null, null))));
	}
	
	private PublicTrail toPublicTrailDto(PublicTrailEntity entity, Stream<PublicPhotoEntity> photos, UserCommunity authorCommunity, Authentication auth) {
		Map<String, String> nameTranslations = new HashMap<>();
		Map<String, String> descriptionTranslations = new HashMap<>();
		try {
			nameTranslations = TrailenceUtils.mapper.readValue(entity.getNameTranslations().asArray(), new TypeReference<Map<String, String>>() {});
		} catch (Exception e) {
			log.error("Mapping error", e);
		}
		try {
			descriptionTranslations = TrailenceUtils.mapper.readValue(entity.getDescriptionTranslations().asArray(), new TypeReference<Map<String, String>>() {});
		} catch (Exception e) {
			log.error("Mapping error", e);
		}
		return new PublicTrail(
			entity.getUuid().toString(),
			entity.getSlug(),
			entity.getCreatedAt(),
			entity.getUpdatedAt(),
			authorCommunity.getAlias() != null && !authorCommunity.getAlias().isBlank() ? authorCommunity.getAlias() : null,
			authorCommunity.getAvatar(),
			authorCommunity.getPublicId(),
			authorCommunity.getNbPublications(),
			authorCommunity.getNbComments(),
			authorCommunity.getNbRates(),
			auth == null || !auth.getPrincipal().toString().equals(entity.getAuthor()) || entity.getAuthorUuid() == null ? null : entity.getAuthorUuid().toString(),
			auth != null && auth.getPrincipal().toString().equals(entity.getAuthor()),
			entity.getName(),
			entity.getDescription(),
			entity.getLocation(),
			entity.getDate(),
			entity.getDistance(),
			entity.getPositiveElevation(),
			entity.getNegativeElevation(),
			entity.getHighestAltitude(),
			entity.getLowestAltitude(),
			entity.getDuration(),
			entity.getBreaksDuration(),
			entity.getEstimatedDuration(),
			entity.getLoopType(),
			entity.getActivity(),
			entity.getBounds().getFirst().getY(),
			entity.getBounds().getSecond().getY(),
			entity.getBounds().getFirst().getX(),
			entity.getBounds().getSecond().getX(),
			entity.getNbRate0(),
			entity.getNbRate1(),
			entity.getNbRate2(),
			entity.getNbRate3(),
			entity.getNbRate4(),
			entity.getNbRate5(),
			Arrays.stream(entity.getSimplifiedPath()).map(i -> i.doubleValue() / 1000000).toList(),
			photos.map(pe -> new PublicTrail.Photo(
				pe.getUuid().toString(),
				pe.getDescription(),
				pe.getDate(),
				pe.getLatitude(),
				pe.getLongitude(),
				pe.getIndex()
			)).toList(),
			entity.getLang(),
			nameTranslations,
			descriptionTranslations,
			entity.getSourceUrl()
		);
	}
	
	public Mono<Flux<DataBuffer>> getPhotoFileContent(String trailUuid, String photoUuid) {
		return publicPhotoRepo.findById(UUID.fromString(photoUuid))
		.filter(p -> p.getTrailUuid().toString().equals(trailUuid))
		.switchIfEmpty(Mono.error(new NotFoundException("photo", trailUuid + "/" + photoUuid)))
		.flatMap(photo -> Mono.just(fileService.getFileContent(photo.getFileId())));
	}
	
	public Mono<PublicTrack> getTrack(String trailUuid) {
		return publicTrackRepo.findById(UUID.fromString(trailUuid))
		.switchIfEmpty(Mono.error(new NotFoundException("track", trailUuid)))
		.map(track -> {
			try {
				return TrackStorage.V1V2Bridge.v2ToV1Dto(track.getData());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		})
		.map(data -> new PublicTrack(data.s, data.wp));
	}
	
	public Mono<Void> deletePublicTrailAsModerator(String uuid) {
		UUID trailUuid = UUID.fromString(uuid);
		return publicTrailRepo.findById(trailUuid)
		.switchIfEmpty(Mono.error(new TrailNotFound(uuid, "trailence")))
		.flatMap(trail ->
			publicTrailRepo.delete(trail)
			.then(publicTrackRepo.deleteByTrailUuid(trailUuid))
			.then(
				publicPhotoRepo.findAllByTrailUuid(trailUuid)
				.flatMap(photo ->
					fileService.deleteFile(photo.getFileId())
					.then(publicPhotoRepo.delete(photo))
				, 1, 1).then()
			)
			.then(feedbackService.publicTrailDeleted(trailUuid))
			.then(userCommunityService.removePublication(trail.getAuthor()))
			.then(
				messageRepo.deleteAllByUuidInAndMessageType(List.of(trailUuid), ModerationMessageEntity.TYPE_REMOVE)
			)
		);
	}
	
	public Mono<PublicTrail> patchPublicTrail(String uuid, PatchPublicTrailRequest request, Authentication auth) {
		UUID trailUuid = UUID.fromString(uuid);
		return publicTrailRepo.findById(trailUuid)
		.switchIfEmpty(Mono.error(new TrailNotFound(uuid, "trailence")))
		.flatMap(trail -> {
			if (request.getLoopType() != null) trail.setLoopType(request.getLoopType());
			return publicTrailRepo.save(trail);
		})
		.flatMap(_ -> this.getById(uuid, auth))
		;
	}
	
	public Flux<PublicTrailEntity> random() {
		return publicTrailRepo.random();
	}
	
	public Flux<SlugAndDate> slugsWithDate(long offset, int nb) {
		return publicTrailRepo.slugsWithDate(nb, offset);
	}
	
	public Mono<Long> count() {
		return publicTrailRepo.count();
	}
	
	public Mono<String> getCurrentPublicUuid(String trailUuid, String trailOwner) {
		return publicTrailRepo.getPublicUuidFromPrivate(UUID.fromString(trailUuid), trailOwner)
		.map(UUID::toString)
		.switchIfEmpty(Mono.just(""));
	}
	
	public Mono<List<String>> searchExamples(int nb) {
		return publicTrailRepo.searchExamples(nb).collectList();
	}
	
	public Mono<Void> requestRemove(String trailUuid, String message, Authentication auth) {
		if (auth == null) return Mono.error(new ForbiddenException());
		if (trailUuid == null || message == null || message.isBlank()) return Mono.error(new BadRequestException("trailUuid and message are mandatory"));
		UUID uuid = UUID.fromString(trailUuid);
		return publicTrailRepo.findById(uuid)
		.filter(entity -> auth.getPrincipal().toString().equals(entity.getAuthor()))
		.switchIfEmpty(Mono.error(new PublicTrailNotFound(trailUuid)))
		.flatMap(publicTrail ->
			messageRepo.findOneByUuidAndOwnerAndMessageType(publicTrail.getUuid(), publicTrail.getAuthor(), ModerationMessageEntity.TYPE_REMOVE)
			.flatMap(messageEntity -> {
				messageEntity.setAuthorMessage(message);
				return messageRepo.save(messageEntity);
			})
			.switchIfEmpty(Mono.defer(() ->
				r2dbc.insert(new ModerationMessageEntity(publicTrail.getUuid(), publicTrail.getAuthor(), message, null, ModerationMessageEntity.TYPE_REMOVE))
			))
		).then();
	}
	
}
