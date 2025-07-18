package org.trailence.trail;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.geo.Box;
import org.springframework.data.geo.Point;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Expressions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.MinusExpression;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.notifications.NotificationsService;
import org.trailence.preferences.UserPreferencesService;
import org.trailence.storage.FileService;
import org.trailence.trail.TrackService.StoredData;
import org.trailence.trail.db.PublicPhotoEntity;
import org.trailence.trail.db.PublicPhotoRepository;
import org.trailence.trail.db.PublicTrackEntity;
import org.trailence.trail.db.PublicTrackRepository;
import org.trailence.trail.db.PublicTrailEntity;
import org.trailence.trail.db.PublicTrailFeedbackEntity;
import org.trailence.trail.db.PublicTrailFeedbackReplyEntity;
import org.trailence.trail.db.PublicTrailFeedbackReplyRepository;
import org.trailence.trail.db.PublicTrailFeedbackRepository;
import org.trailence.trail.db.PublicTrailRepository;
import org.trailence.trail.db.PublicTrailRepository.SlugAndDate;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.dto.CreateFeedbackRequest;
import org.trailence.trail.dto.CreatePublicTrailRequest;
import org.trailence.trail.dto.MyFeedback;
import org.trailence.trail.dto.MyPublicTrail;
import org.trailence.trail.dto.PublicTrack;
import org.trailence.trail.dto.PublicTrail;
import org.trailence.trail.dto.PublicTrailFeedback;
import org.trailence.trail.dto.PublicTrailFeedback.Reply;
import org.trailence.trail.dto.PublicTrailSearch;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsResponse;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileResponse;

import com.fasterxml.jackson.core.type.TypeReference;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicTrailService {
	
	private final PublicTrailRepository publicTrailRepo;
	private final PublicPhotoRepository publicPhotoRepo;
	private final PublicTrackRepository publicTrackRepo;
	private final TrailRepository trailRepo;
	private final PublicTrailFeedbackRepository feedbackRepo;
	private final PublicTrailFeedbackReplyRepository feedbackReplyRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final PhotoService photoService;
	private final TrailService trailService;
	private final FileService fileService;
	private final NotificationsService notificationsService;
	private final UserPreferencesService prefService;
	
	@Transactional
	public Mono<String> create(CreatePublicTrailRequest request, Authentication auth) {
		String author = request.getAuthor().toLowerCase();
		if (author.equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		String authorUuid = request.getAuthorUuid();
		Mono<Tuple3<UUID, String, Optional<PublicTrailEntity>>> newData;
		if (authorUuid == null) newData = generateSlug(request.getName()).map(slug -> Tuples.of(UUID.randomUUID(), slug, Optional.empty()));
		else newData = publicTrailRepo.findFirst1ByAuthorAndAuthorUuid(author, UUID.fromString(authorUuid))
			.flatMap(existing -> 
				publicTrackRepo.deleteById(existing.getUuid())
				.then(publicPhotoRepo.deleteAllByTrailUuid(existing.getUuid()))
				.then(publicTrailRepo.deleteById(existing.getUuid()))
				.then(Mono.just(Tuples.of(existing.getUuid(), existing.getSlug(), Optional.of(existing))))
			)
			.switchIfEmpty(Mono.defer(() -> generateSlug(request.getName()).map(slug -> Tuples.of(UUID.randomUUID(), slug, Optional.empty()))));
		return newData
		.flatMap(tuple ->
			trailRepo.findTrailToReview(UUID.fromString(request.getTrailUuid()), author)
			.switchIfEmpty(Mono.error(new NotFoundException("trail", request.getTrailUuid() + "-" + author)))
			.flatMap(fromTrail ->
				r2dbc.insert(toTrackEntity(tuple.getT1(), request))
				.then(Flux.fromIterable(request.getPhotos()).flatMap(p -> photoService.transferToPublic(UUID.fromString(p.getUuid()), author, tuple.getT1(), p), 1, 1).then())
				.then(r2dbc.insert(toTrailEntity(tuple.getT1(), tuple.getT2(), tuple.getT3().orElse(null), request)))
				.then(trailService.delete(Flux.just(fromTrail), author))
				.then(notificationsService.create(author, "publications.accepted", List.of(request.getName(), tuple.getT1().toString())))
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
			AtomicLong min = new AtomicLong(0L);
			AtomicLong max = new AtomicLong(0L);
			int l = initialSlug.length();
			return publicTrailRepo.findAllSlugsStartingWith(initialSlug + "-").doOnNext(slug -> {
				if (slug.length() > l + 1) {
					String end = slug.substring(l + 1);
					try {
						long val = Long.parseLong(end);
						if (val > 0) {
							min.getAndUpdate(m -> m > val ? val : m);
							max.getAndUpdate(m -> m < val ? val : m);
						}
					} catch (Exception e) {
						// ignore
					}
				}
			})
			.then(Mono.fromSupplier(() -> {
				long mi = min.get();
				if (mi > 1) return initialSlug + "-" + (mi - 1);
				long ma = max.get();
				return initialSlug + "-" + (ma + 1);
			}));
		});
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
			descriptionTranslations
		);
	}
	
	private static PublicTrackEntity toTrackEntity(UUID uuid, CreatePublicTrailRequest request) {
		try {
			return new PublicTrackEntity(
				uuid,
				TrackService.compress(new TrackService.StoredData(request.getFullTrack(), request.getWayPoints()))
			);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public Mono<SearchByTileResponse> searchByTiles(SearchByTileRequest request) {
		if (request.getZoom() < 1 || request.getZoom() > 10) return Mono.error(new BadRequestException("invalid-zoom", "Invalid zoom level"));
		if (request.getTiles().size() > 1000) return Mono.error(new BadRequestException("too-many-tiles", "Maximum 1000 tiles by request"));

		Column zoomColumn = Column.create("tile_zoom" + request.getZoom(), PublicTrailEntity.TABLE);
		
		Condition where = Conditions.in(zoomColumn, request.getTiles().stream().map(SQL::literalOf).toList());
		if (request.getFilters() != null) {
			where = applyFilterNumeric(where, request.getFilters().getDuration(), new MinusExpression(PublicTrailEntity.COL_DURATION, PublicTrailEntity.COL_BREAKS_DURATION));
			where = applyFilterNumeric(where, request.getFilters().getEstimatedDuration(), PublicTrailEntity.COL_ESTIMATED_DURATION);
			where = applyFilterNumeric(where, request.getFilters().getDistance(), PublicTrailEntity.COL_DISTANCE);
			where = applyFilterNumeric(where, request.getFilters().getPositiveElevation(), PublicTrailEntity.COL_POSITIVE_ELEVATION);
			where = applyFilterNumeric(where, request.getFilters().getNegativeElevation(), PublicTrailEntity.COL_NEGATIVE_ELEVATION);
			where = applyFilterList(where, request.getFilters().getLoopTypes(), PublicTrailEntity.COL_LOOP_TYPE);
			where = applyFilterList(where, request.getFilters().getActivities(), PublicTrailEntity.COL_ACTIVITY);
		}
		
		String sql = new SqlBuilder()
		.select(
			Expressions.just("count(*) as nb_trails"),
			zoomColumn.as("tile")
		)
		.from(PublicTrailEntity.TABLE)
		.where(where)
		.groupBy(zoomColumn)
		.build();

		return r2dbc.query(DbUtils.operation(sql, null), row -> new PublicTrailSearch.NbTrailsByTile(row.get("tile", Integer.class), row.get("nb_trails", Long.class)))
			.all().collectList().map(SearchByTileResponse::new);
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
	
	public Mono<PublicTrail> getById(String uuid, Authentication auth) {
		return publicTrailRepo.findById(UUID.fromString(uuid))
		.flatMap(trail ->
			publicPhotoRepo.findAllByTrailUuid(trail.getUuid()).collectList()
			.flatMap(photos ->
				prefService.getAlias(trail.getAuthor())
				.map(alias -> this.toPublicTrailDto(trail, photos.stream(), alias, auth))
			)
		);
	}
	
	public Mono<PublicTrail> getBySlug(String slug, Authentication auth) {
		return publicTrailRepo.findOneBySlug(slug)
		.switchIfEmpty(Mono.defer(() -> {
			String slug2 = URLEncoder.encode(slug, StandardCharsets.UTF_8);
			if (slug2.equals(slug)) return Mono.empty();
			return publicTrailRepo.findOneBySlug(slug2);
		}))
		.flatMap(trail ->
			publicPhotoRepo.findAllByTrailUuid(trail.getUuid()).collectList()
			.flatMap(photos ->
				prefService.getAlias(trail.getAuthor())
				.map(alias -> this.toPublicTrailDto(trail, photos.stream(), alias, auth))
			)
			
		);
	}
	
	public Mono<List<PublicTrail>> getByIds(List<String> uuids, Authentication auth) {
		if (uuids.size() > 200) return Mono.error(new BadRequestException("too-many-trails", "Maximum 200 trails"));
		return publicTrailRepo.findAllById(uuids.stream().map(UUID::fromString).toList())
		.collectList()
		.flatMap(trails ->
			publicPhotoRepo.findAllByTrailUuidIn(trails.stream().map(PublicTrailEntity::getUuid).toList()).collectList()
			.flatMap(photos ->
				prefService.getAliases(trails.stream().map(t -> t.getAuthor()).toList()).collectList()
				.map(aliases ->
					trails.stream()
					.map(trail -> this.toPublicTrailDto(
						trail,
						photos.stream().filter(p -> p.getTrailUuid().equals(trail.getUuid())),
						aliases.stream().filter(a -> a.getT1().equals(trail.getAuthor())).map(Tuple2::getT2).findAny(),
						auth
					))
					.toList()
				)
			)
		);
	}
	
	public Flux<MyPublicTrail> getMines(Authentication auth) {
		String user = auth.getPrincipal().toString();
		return publicTrailRepo.findMyPublicTrails(user);
	}
	
	private PublicTrail toPublicTrailDto(PublicTrailEntity entity, Stream<PublicPhotoEntity> photos, Optional<String> authorAlias, Authentication auth) {
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
			authorAlias.orElse(null),
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
			descriptionTranslations
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
				return TrackService.uncompress(track.getData(), new TypeReference<StoredData>() {});
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		})
		.map(data -> new PublicTrack(data.s, data.wp));
	}
	
	@Transactional
	public Mono<Void> createFeedback(CreateFeedbackRequest request, Authentication auth) {
		long date = System.currentTimeMillis();
		String email = auth.getPrincipal().toString();
		UUID trailUuid = UUID.fromString(request.getTrailUuid());
		
		String comment = request.getComment();
		if (comment != null) {
			comment = comment.trim();
			if (comment.isEmpty()) comment = null;
		}
		Integer rate = request.getRate();
		if (rate != null && (rate.intValue() < 0 || rate.intValue() > 5)) rate = null;
		
		if (comment == null && rate == null) return Mono.empty();
		
		String c = comment;
		Integer r = rate;
		
		return publicTrailRepo.getAuthorAndName(trailUuid)
		.flatMap(authorAndName -> {
			if (authorAndName.getAuthor().equals(email)) return Mono.empty();
			Mono<Optional<PublicTrailFeedbackEntity>> existingRate = r == null ? Mono.just(Optional.empty()) :
				feedbackRepo.findOneByPublicTrailUuidAndEmailAndRateIsNotNull(trailUuid, email).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()));
			return existingRate.flatMap(existingRateOpt -> {
				if (existingRateOpt.isPresent()) {
					PublicTrailFeedbackEntity entity = existingRateOpt.get();
					Mono<Void> update = entity.getRate().intValue() != r.intValue() ? this.updateRate(entity, r) : Mono.empty();
					return update
					.then(Mono.defer(() -> {
						if (c == null) return Mono.empty();
						return this.createFeedback(trailUuid, email, date, null, c, authorAndName.getAuthor(), authorAndName.getName());
					}));
				}
				return this.createFeedback(trailUuid, email, date, r, c, authorAndName.getAuthor(), authorAndName.getName());
				
			});
		});
	}
	
	private Mono<Void> createFeedback(UUID trailUuid, String email, long date, Integer rate, String comment, String trailAuthor, String trailName) {
		PublicTrailFeedbackEntity entity = new PublicTrailFeedbackEntity(
			UUID.randomUUID(),
			trailUuid,
			email,
			date,
			rate,
			comment
		);
		return r2dbc.insert(entity)
		.flatMap(feedback -> {
			if (feedback.getRate() == null) return Mono.empty();
			return addRateToTrail(trailUuid, feedback.getRate());
		})
		.then(Mono.defer(() -> 
			notificationsService.create(trailAuthor, "comments.someone_leave_" + (rate != null ? (comment != null ? "a_rate_with_comment" : "a_rate") : "a_comment"), List.of(trailUuid.toString(), trailName))
		));
	}
	
	private Mono<Void> addRateToTrail(UUID trailUuid, int rate) {
		String col = "nb_rate" + rate;
		String sql = "UPDATE public_trails SET " + col + " = " + col + " + 1 WHERE uuid = '" + trailUuid.toString() + "'";
		return r2dbc.getDatabaseClient().sql(sql).fetch().rowsUpdated().then();
	}
	
	private Mono<Void> updateRate(PublicTrailFeedbackEntity entity, int newRate) {
		String col1 = "nb_rate" + entity.getRate().intValue();
		String col2 = "nb_rate" + newRate;
		return r2dbc.getDatabaseClient().sql("UPDATE public_trail_feedback SET rate = " + newRate + " WHERE uuid = '" + entity.getUuid().toString() + "'").fetch().rowsUpdated()
		.then(r2dbc.getDatabaseClient().sql("UPDATE public_trails SET " + col1 + " = " + col1 + " - 1, " + col2 + " = " + col2 + " + 1 WHERE uuid = '" + entity.getPublicTrailUuid().toString() + "'").fetch().rowsUpdated().then());
	}
	
	public Mono<PublicTrailFeedback.Reply> replyToFeedback(String feedbackUuid, String reply, Authentication auth) {
		long date = System.currentTimeMillis();
		String email = auth.getPrincipal().toString();
		UUID uuid = UUID.fromString(feedbackUuid);
		if (reply == null) return Mono.empty();
		String comment = reply.trim();
		if (comment.isEmpty()) return Mono.empty();
		String c = comment;
		return feedbackRepo.findById(uuid)
		.switchIfEmpty(Mono.error(new NotFoundException("feedback", feedbackUuid)))
		.flatMap(feedback -> 
			r2dbc.insert(new PublicTrailFeedbackReplyEntity(UUID.randomUUID(), uuid, email, date, c))
			.doOnNext(r ->
				feedbackReplyRepo.findAllByReplyTo(feedback.getUuid())
				.map(re -> re.getEmail())
				.distinct()
				.collectList()
				.flatMap(users -> {
					Set<String> recipients = new HashSet<>();
					if (!email.equals(feedback.getEmail())) recipients.add(feedback.getEmail());
					for (var u : users) if (!u.equals(email)) recipients.add(u);
					if (recipients.isEmpty()) return Mono.empty();
					return publicTrailRepo.findById(feedback.getPublicTrailUuid())
					.flatMap(trail ->
						Flux.fromIterable(recipients)
						.flatMap(recipient -> notificationsService.create(recipient, "comments.reply", List.of(feedback.getPublicTrailUuid().toString(), trail.getName())))
						.then()
					);
				})
				.subscribe()
			)
		)
		.flatMap(entity ->
			prefService.getPreferences(email)
			.map(pref -> new PublicTrailFeedback.Reply(entity.getUuid().toString(), pref.getAlias(), true, entity.getDate(), c))
		);
	}
	
	public Mono<MyFeedback> getMyFeedback(String trailUuid, Authentication auth) {
		String email = auth.getPrincipal().toString();
		Optional<UUID> isUuid = TrailenceUtils.ifUuid(trailUuid);
		return (isUuid.isPresent() ? Mono.just(isUuid.get()) : publicTrailRepo.findOneBySlug(trailUuid).map(e -> e.getUuid()))
		.flatMap(uuid -> 
			feedbackRepo.findOneByPublicTrailUuidAndEmailAndRateIsNotNull(uuid, email)
			.map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
			.flatMap(myRateOpt ->
				feedbackRepo.findFirst1ByPublicTrailUuidAndEmailAndCommentIsNotNullOrderByDateDesc(uuid, email)
				.map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
				.map(myLatestCommentOpt -> new MyFeedback(
					myRateOpt.isPresent() ? myRateOpt.get().getRate() : null,
					myRateOpt.isPresent() ? myRateOpt.get().getDate() : null,
					myLatestCommentOpt.isPresent() ? myLatestCommentOpt.get().getDate() : null
				))
			)
		);
	}
	
	public Mono<List<PublicTrailFeedback>> getFeedbacks(String trailUuid, long pageFromDate, int size, String excludeFromStartingDate, Integer filterRate, Authentication auth) {
		String youEmail = auth == null ? "" : auth.getPrincipal().toString();
		var sql = new StringBuilder(2048)
		.append("SELECT ")
		.append("public_trail_feedback.uuid")
		.append(",public_trail_feedback.date")
		.append(",public_trail_feedback.rate")
		.append(",public_trail_feedback.comment")
		.append(",user_preferences.alias")
		.append(",public_trail_feedback.email")
		.append(" FROM public_trail_feedback")
		.append(" LEFT JOIN user_preferences ON user_preferences.email = public_trail_feedback.email")
		.append(" WHERE public_trail_feedback.public_trail_uuid = '").append(trailUuid).append('\'');
		if (pageFromDate > 0)
			sql.append(" AND public_trail_feedback.date <= ").append(pageFromDate);
		if (filterRate != null) {
			sql.append(" AND public_trail_feedback.rate = ").append(filterRate);
		}
		if (excludeFromStartingDate != null) {
			String notIn = String.join(",", Stream.of(excludeFromStartingDate.split(",")).map(TrailenceUtils::ifUuid).filter(o -> o.isPresent()).map(Optional::get).map(uuid -> "'" + uuid.toString() + "'").toList());
			if (!notIn.isEmpty())
				sql.append(" AND public_trail_feedback.uuid NOT IN (").append(notIn).append(')');
		}
		sql.append(" ORDER BY public_trail_feedback.date DESC");
		sql.append(" LIMIT ").append(size > 100 || size < 1 ? 100 : size);
		return r2dbc.query(DbUtils.operation(sql.toString(), null), row -> new PublicTrailFeedback(
			row.get("uuid", UUID.class).toString(),
			row.get("alias", String.class),
			youEmail.equals(row.get("email", String.class)),
			row.get("date", Long.class),
			row.get("rate", Integer.class),
			row.get("comment", String.class),
			new LinkedList<>()
		)).all().collectList()
		.flatMap(feedbacks -> {
			if (feedbacks.isEmpty()) return Mono.just(feedbacks);
			String sqlReplies = "SELECT public_trail_feedback_reply.reply_to, public_trail_feedback_reply.uuid, user_preferences.alias, public_trail_feedback_reply.date, public_trail_feedback_reply.comment, public_trail_feedback_reply.email FROM public_trail_feedback_reply LEFT JOIN user_preferences ON user_preferences.email = public_trail_feedback_reply.email WHERE public_trail_feedback_reply.reply_to IN ("
						+ String.join(",", feedbacks.stream().map(f -> "'" + f.getUuid() + "'").collect(Collectors.toSet())) + ") ORDER BY public_trail_feedback_reply.date DESC";
			return r2dbc.query(DbUtils.operation(sqlReplies, null), row -> new TmpReply(
				row.get("reply_to", UUID.class).toString(),
				row.get("uuid", UUID.class).toString(),
				row.get("alias", String.class),
				youEmail.equals(row.get("email", String.class)),
				row.get("date", Long.class),
				row.get("comment", String.class)
			)).all().collectList()
			.map(replies -> {
				for (var reply : replies) {
					var feedbackOpt = feedbacks.stream().filter(f -> f.getUuid().equals(reply.getCommentUuid())).findAny();
					if (feedbackOpt.isPresent()) feedbackOpt.get().getReplies().add(new Reply(reply.getUuid(), reply.getAlias(), reply.isYou(), reply.getDate(), reply.getComment()));
				}
				return feedbacks;
			});
		});
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TmpReply {
		private String commentUuid;
		private String uuid;
		private String alias;
		private boolean you;
		private long date;
		private String comment;
	}
	
	@Transactional
	public Mono<Void> deleteComment(String feedbackUuid, Authentication auth) {
		String user = auth == null ? "" : auth.getPrincipal().toString();
		boolean moderator = TrailenceUtils.hasRole(auth, TrailenceUtils.ROLE_MODERATOR);
		return feedbackRepo.findById(UUID.fromString(feedbackUuid))
		.switchIfEmpty(Mono.error(new NotFoundException("feedback", feedbackUuid)))
		.flatMap(entity -> {
			if (!moderator && !entity.getEmail().equals(user)) return Mono.error(new ForbiddenException());
			if (entity.getComment() == null) return Mono.empty();
			if (entity.getRate() != null) {
				entity.setComment(null);
				return feedbackRepo.save(entity);
			} else {
				return feedbackRepo.delete(entity).thenReturn(entity);
			}
		})
		.flatMap(entity -> feedbackReplyRepo.deleteAllByReplyTo(entity.getUuid()));
	}
	
	public Mono<Void> deleteReply(String uuid, Authentication auth) {
		String user = auth == null ? "" : auth.getPrincipal().toString();
		boolean moderator = TrailenceUtils.hasRole(auth, TrailenceUtils.ROLE_MODERATOR);
		return feedbackReplyRepo.findById(UUID.fromString(uuid))
		.switchIfEmpty(Mono.error(new NotFoundException("feedback-reply", uuid)))
		.flatMap(entity -> {
			if (!moderator && !entity.getEmail().equals(user)) return Mono.error(new ForbiddenException());
			return feedbackRepo.deleteById(entity.getUuid());
		});
	}
	
	public Flux<PublicTrailEntity> random() {
		return publicTrailRepo.random();
	}
	
	public Flux<SlugAndDate> allSlugs() {
		return publicTrailRepo.allSlugs();
	}
	
	public Mono<String> getCurrentPublicUuid(String trailUuid, String trailOwner) {
		return publicTrailRepo.getPublicUuidFromPrivate(UUID.fromString(trailUuid), trailOwner)
		.map(UUID::toString)
		.switchIfEmpty(Mono.just(""));
	}
	
}
