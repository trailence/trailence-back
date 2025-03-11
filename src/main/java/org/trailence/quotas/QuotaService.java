package org.trailence.quotas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.mutable.MutableLong;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AliasedExpression;
import org.springframework.data.relational.core.sql.Assignments;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.core.sql.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.MinusExpression;
import org.trailence.global.db.PlusExpression;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.dto.PageResult;
import org.trailence.global.exceptions.ConflictException;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.QuotaExceededException;
import org.trailence.quotas.db.PlanEntity;
import org.trailence.quotas.db.PlanRepository;
import org.trailence.quotas.db.UserQuotasEntity;
import org.trailence.quotas.db.UserQuotasRepository;
import org.trailence.quotas.db.UserSubscriptionEntity;
import org.trailence.quotas.db.UserSubscriptionRepository;
import org.trailence.quotas.dto.Plan;
import org.trailence.quotas.dto.UserQuotas;
import org.trailence.quotas.dto.UserSubscription;

import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {
	
	private final UserQuotasRepository quotasRepo;
	private final PlanRepository planRepo;
	private final UserSubscriptionRepository subscriptionsRepo;
	private final R2dbcEntityTemplate r2dbc;
	
	public Mono<UserQuotas> getUserQuotas(String email) {
		return quotasRepo.findById(email.toLowerCase()).map(this::toDto);
	}
	
	private UserQuotas toDto(UserQuotasEntity entity) {
		return new UserQuotas(
			entity.getCollectionsUsed(),
			entity.getCollectionsMax(),
			entity.getTrailsUsed(),
			entity.getTrailsMax(),
			entity.getTracksUsed(),
			entity.getTracksMax(),
			entity.getTracksSizeUsed(),
			entity.getTracksSizeMax(),
			entity.getPhotosUsed(),
			entity.getPhotosMax(),
			entity.getPhotosSizeUsed(),
			entity.getPhotosSizeMax(),
			entity.getTagsUsed(),
			entity.getTagsMax(),
			entity.getTrailTagsUsed(),
			entity.getTrailTagsMax(),
			entity.getSharesUsed(),
			entity.getSharesMax()
		);
	}
	
	private static final String COL_SUBSCRIPTIONS_COUNT = "nb_subscriptions";
	private static final String COL_ACTIVE_SUBSCRIPTIONS_COUNT = "nb_active_subscriptions";
	private static final Map<String, Object> planDtoFieldMapping = new HashMap<>();
	
	static {
		planDtoFieldMapping.put("name", PlanEntity.COL_NAME);
		planDtoFieldMapping.put("collections", PlanEntity.COL_COLLECTIONS);
		planDtoFieldMapping.put("trails", PlanEntity.COL_TRAILS);
		planDtoFieldMapping.put("tracks", PlanEntity.COL_TRACKS);
		planDtoFieldMapping.put("tracksSize", PlanEntity.COL_TRACKS_SIZE);
		planDtoFieldMapping.put("photos", PlanEntity.COL_PHOTOS);
		planDtoFieldMapping.put("photosSize", PlanEntity.COL_PHOTOS_SIZE);
		planDtoFieldMapping.put("tags", PlanEntity.COL_TAGS);
		planDtoFieldMapping.put("trailTags", PlanEntity.COL_TRAIL_TAGS);
		planDtoFieldMapping.put("shares", PlanEntity.COL_SHARES);
		planDtoFieldMapping.put("subscriptionsCount", COL_SUBSCRIPTIONS_COUNT);
		planDtoFieldMapping.put("activeSubscriptionsCount", COL_ACTIVE_SUBSCRIPTIONS_COUNT);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<PageResult<Plan>> getPlans(Pageable pageable) {
		long now = System.currentTimeMillis();
		String sql = new SqlBuilder()
		.select(
			AsteriskFromTable.create(PlanEntity.TABLE),
			Column.create(COL_SUBSCRIPTIONS_COUNT, Table.create(COL_SUBSCRIPTIONS_COUNT)),
			Column.create(COL_ACTIVE_SUBSCRIPTIONS_COUNT, Table.create(COL_ACTIVE_SUBSCRIPTIONS_COUNT))
		)
		.from(PlanEntity.TABLE)
		// calculate number of subscriptions
		.leftJoinSubSelect(new SqlBuilder()
			.select(
				UserSubscriptionEntity.COL_PLAN_NAME,
				SimpleFunction.create("COUNT", List.of(AsteriskFromTable.create(UserSubscriptionEntity.TABLE))).as(COL_SUBSCRIPTIONS_COUNT)
			)
			.from(UserSubscriptionEntity.TABLE)
			.groupBy(UserSubscriptionEntity.COL_PLAN_NAME)
			.build(),
			Conditions.isEqual(Column.create(UserSubscriptionEntity.COL_PLAN_NAME.getName(), Table.create(COL_SUBSCRIPTIONS_COUNT)), PlanEntity.COL_NAME),
			COL_SUBSCRIPTIONS_COUNT
		)
		// calculate number of active subscriptions
		.leftJoinSubSelect(new SqlBuilder()
			.select(
				UserSubscriptionEntity.COL_PLAN_NAME,
				SimpleFunction.create("COUNT", List.of(AsteriskFromTable.create(UserSubscriptionEntity.TABLE))).as(COL_ACTIVE_SUBSCRIPTIONS_COUNT)
			)
			.from(UserSubscriptionEntity.TABLE)
			.where(
				Conditions.isLessOrEqualTo(UserSubscriptionEntity.COL_STARTS_AT, SQL.literalOf(now))
				.and(
					Conditions.isNull(UserSubscriptionEntity.COL_ENDS_AT)
					.or(Conditions.isGreaterOrEqualTo(UserSubscriptionEntity.COL_ENDS_AT, SQL.literalOf(now)))
				)
			)
			.groupBy(UserSubscriptionEntity.COL_PLAN_NAME)
			.build(),
			Conditions.isEqual(Column.create(UserSubscriptionEntity.COL_PLAN_NAME.getName(), Table.create(COL_ACTIVE_SUBSCRIPTIONS_COUNT)), PlanEntity.COL_NAME),
			COL_ACTIVE_SUBSCRIPTIONS_COUNT
		)
		.pageable(pageable, planDtoFieldMapping)
		.build();
		return Mono.zip(
			r2dbc.query(DbUtils.operation(sql, null), this::toPlanDto)
				.all().collectList().publishOn(Schedulers.parallel()),
			planRepo.count().publishOn(Schedulers.parallel())
		).map(result -> new PageResult<Plan>(pageable, result.getT1(), result.getT2()));
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	@Transactional
	public Mono<Plan> createPlan(Plan plan) {
		return planRepo.findByName(plan.getName())
		.flatMap(existing -> Mono.<PlanEntity>error(new ConflictException("already-exists", "Plan " + plan.getName() + " already exists")))
		.switchIfEmpty(Mono.defer(() -> r2dbc.insert(toEntity(plan))))
		.map(this::toDto);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	@Transactional
	public Mono<Plan> updatePlan(String planName, Plan plan) {
		if (TrailenceUtils.FREE_PLAN.equals(planName)) return Mono.error(new ForbiddenException());
		return planRepo.findByName(planName)
		.switchIfEmpty(Mono.error(new NotFoundException("plan", plan.getName())))
		.flatMap(entity -> {
			if (plan.getName().equals(planName))
				return planRepo.save(toEntity(plan)).flatMap(newEntity -> computeQuotas().thenReturn(newEntity));
			// name changed => delete + insert + rename all in subscriptions
			return planRepo.findByName(plan.getName())
			.flatMap(existing -> Mono.<PlanEntity>error(new ConflictException("already-exists", "Plan " + plan.getName() + " already exists")))
			.switchIfEmpty(Mono.defer(() ->
				r2dbc.insert(toEntity(plan))
				.flatMap(newEntity ->
					r2dbc.getDatabaseClient().sql(
						"UPDATE " + UserSubscriptionEntity.TABLE.toString()
						+ " SET " + UserSubscriptionEntity.COL_PLAN_NAME.getName().toString() + " = " + SQL.literalOf(plan.getName())
						+ " WHERE " + UserSubscriptionEntity.COL_PLAN_NAME.toString() + " = " + SQL.literalOf(planName)
					).fetch().rowsUpdated()
					.then(planRepo.delete(entity))
					.then(computeQuotas())
					.thenReturn(newEntity)
				)
			));
		})
		.map(this::toDto);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<Void> deletePlan(String planName) {
		if (TrailenceUtils.FREE_PLAN.equals(planName)) return Mono.error(new ForbiddenException());
		return planRepo.findByName(planName)
		.switchIfEmpty(Mono.error(new NotFoundException("plan", planName)))
		.flatMap(entity ->
			subscriptionsRepo.countByPlanName(planName)
			.flatMap(usage -> {
				if (usage.longValue() > 0) return Mono.error(new ForbiddenException("plan-used"));
				return Mono.empty();
			})
			.then(planRepo.delete(entity))
		).then();
	}
	
	private Plan toDto(PlanEntity entity) {
		return new Plan(
			entity.getName(),
			entity.getCollections(),
			entity.getTrails(),
			entity.getTracks(),
			entity.getTracksSize(),
			entity.getPhotos(),
			entity.getPhotosSize(),
			entity.getTags(),
			entity.getTrailTags(),
			entity.getShares(),
			null, null
		);
	}
	
	private Plan toPlanDto(Row row) {
		return new Plan(
			row.get(PlanEntity.COL_NAME.getName().toString(), String.class),
			row.get(PlanEntity.COL_COLLECTIONS.getName().toString(), Long.class),
			row.get(PlanEntity.COL_TRAILS.getName().toString(), Long.class),
			row.get(PlanEntity.COL_TRACKS.getName().toString(), Long.class),
			row.get(PlanEntity.COL_TRACKS_SIZE.getName().toString(), Long.class),
			row.get(PlanEntity.COL_PHOTOS.getName().toString(), Long.class),
			row.get(PlanEntity.COL_PHOTOS_SIZE.getName().toString(), Long.class),
			row.get(PlanEntity.COL_TAGS.getName().toString(), Long.class),
			row.get(PlanEntity.COL_TRAIL_TAGS.getName().toString(), Long.class),
			row.get(PlanEntity.COL_SHARES.getName().toString(), Long.class),
			Optional.ofNullable(row.get(COL_SUBSCRIPTIONS_COUNT, Long.class)).orElse(0L),
			Optional.ofNullable(row.get(COL_ACTIVE_SUBSCRIPTIONS_COUNT, Long.class)).orElse(0L)
		);
	}
	
	private PlanEntity toEntity(Plan plan) {
		return new PlanEntity(
			plan.getName(),
			plan.getCollections(),
			plan.getTrails(),
			plan.getTracks(),
			plan.getTracksSize(),
			plan.getPhotos(),
			plan.getPhotosSize(),
			plan.getTags(),
			plan.getTrailTags(),
			plan.getShares()
		);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Flux<UserSubscription> getUserSubscriptions(String email) {
		return subscriptionsRepo.findAllByUserEmail(email.toLowerCase()).map(this::toDto);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<UserSubscription> addUserSubscription(String email, String planName) {
		long now = System.currentTimeMillis();
		UserSubscriptionEntity entity = new UserSubscriptionEntity(
			UUID.randomUUID(),
			email.toLowerCase(),
			planName,
			now,
			null
		);
		return r2dbc.insert(entity).map(this::toDto)
		.flatMap(result -> updateUserQuotas(email.toLowerCase(), now).thenReturn(result));
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<UserSubscription> stopUserSubscription(String email, UUID uuid) {
		long now = System.currentTimeMillis();
		return subscriptionsRepo.findByUserEmailAndUuid(email.toLowerCase(), uuid)
		.switchIfEmpty(Mono.error(new NotFoundException("subscription", uuid.toString())))
		.flatMap(entity -> {
			entity.setEndsAt(now);
			return subscriptionsRepo.save(entity);
		})
		.flatMap(entity -> updateUserQuotas(email.toLowerCase(), now + 1).thenReturn(entity))
		.map(this::toDto);
	}
	
	private UserSubscription toDto(UserSubscriptionEntity entity) {
		return new UserSubscription(
			entity.getUuid(),
			entity.getPlanName(),
			entity.getStartsAt(),
			entity.getEndsAt()
		);
	}
	
	public Mono<Integer> addCollections(String email, int nb) {
		return incrementQuota(email, nb, UserQuotasEntity.COL_COLLECTIONS_USED, UserQuotasEntity.COL_COLLECTIONS_MAX, "collections");
	}
	
	public Mono<Void> collectionsDeleted(String email, long nb) {
		return decrementQuota(email, UserQuotasEntity.COL_COLLECTIONS_USED, nb);
	}

	
	public Mono<Integer> addTrails(String email, int nb) {
		return incrementQuota(email, nb, UserQuotasEntity.COL_TRAILS_USED, UserQuotasEntity.COL_TRAILS_MAX, "trails");
	}
	
	public Mono<Void> trailsDeleted(String email, long nb) {
		return decrementQuota(email, UserQuotasEntity.COL_TRAILS_USED, nb);
	}

	
	public Mono<Void> addTrack(String email, int dataSize) {
		return incrementQuota(email, 1, UserQuotasEntity.COL_TRACKS_USED, UserQuotasEntity.COL_TRACKS_MAX, "tracks")
		.then(incrementQuota(email, dataSize, UserQuotasEntity.COL_TRACKS_SIZE_USED, UserQuotasEntity.COL_TRACKS_SIZE_MAX, "tracks-size", false))
		.then();
	}
	
	public Mono<Void> updateTrackSize(String email, int diff) {
		return updateQuota(email, UserQuotasEntity.COL_TRACKS_SIZE_USED, UserQuotasEntity.COL_TRACKS_SIZE_MAX, diff, "tracks-size");
	}
	
	public Mono<Void> tracksDeleted(String email, long nb, long size) {
		return decrementQuota(email, UserQuotasEntity.COL_TRACKS_USED, nb)
		.then(decrementQuota(email, UserQuotasEntity.COL_TRACKS_SIZE_USED, size));
	}

	
	public Mono<Integer> addTags(String email, int nb) {
		return incrementQuota(email, nb, UserQuotasEntity.COL_TAGS_USED, UserQuotasEntity.COL_TAGS_MAX, "tags");
	}
	
	public Mono<Void> tagsDeleted(String email, long nb) {
		return decrementQuota(email, UserQuotasEntity.COL_TAGS_USED, nb);
	}

	
	public Mono<Integer> addTrailTags(String email, int nb) {
		return incrementQuota(email, nb, UserQuotasEntity.COL_TRAIL_TAGS_USED, UserQuotasEntity.COL_TRAIL_TAGS_MAX, "trail-tags");
	}
	
	public Mono<Void> trailTagsDeleted(String email, long nb) {
		return decrementQuota(email, UserQuotasEntity.COL_TRAIL_TAGS_USED, nb);
	}

	
	public Mono<Integer> addShares(String email, int nb) {
		return incrementQuota(email, nb, UserQuotasEntity.COL_SHARES_USED, UserQuotasEntity.COL_SHARES_MAX, "shares");
	}
	
	public Mono<Void> sharesDeleted(String email, long nb) {
		return decrementQuota(email, UserQuotasEntity.COL_SHARES_USED, nb);
	}

	
	@Transactional
	public Mono<Void> addPhoto(String email, long fileSize) {
		return incrementQuota(email, 1, UserQuotasEntity.COL_PHOTOS_USED, UserQuotasEntity.COL_PHOTOS_MAX, "photos")
		.then(incrementQuota(email, fileSize, UserQuotasEntity.COL_PHOTOS_SIZE_USED, UserQuotasEntity.COL_PHOTOS_SIZE_MAX, "photos-size", false))
		.then();
	}
	
	@Transactional
	public Mono<Void> photoDeleted(String email, long fileSize) {
		return decrementQuota(email, UserQuotasEntity.COL_PHOTOS_USED, 1)
		.then(decrementQuota(email, UserQuotasEntity.COL_PHOTOS_SIZE_USED, fileSize));
	}

	
	private Mono<Integer> incrementQuota(String email, int nb, Column columnUsed, Column columnMax, String quotaType) {
		return incrementQuota(email, nb, columnUsed, columnMax, quotaType, true).map(Long::intValue);
	}
	
	private Mono<Long> incrementQuota(String email, long nb, Column columnUsed, Column columnMax, String quotaType, boolean allowLess) {
		if (nb == 0) return Mono.just(0L);
		var newUsed = new PlusExpression(columnUsed, SQL.literalOf(nb));
		var query = DbUtils.update(
			Update.builder()
			.table(UserQuotasEntity.TABLE)
			.set(Assignments.value(columnUsed, newUsed))
			.where(
				Conditions.isEqual(UserQuotasEntity.COL_EMAIL, SQL.literalOf(email))
				.and(Conditions.isLessOrEqualTo(newUsed, columnMax))
			).build(),
			null,
			r2dbc
		);
		return r2dbc.getDatabaseClient().sql(query).fetch().rowsUpdated()
		.flatMap(updated -> {
			if (updated > 0) return Mono.just(nb);
			if (nb == 1 || !allowLess) return Mono.error(new QuotaExceededException(quotaType));
			return r2dbc.getDatabaseClient().sql(
				DbUtils.select(
					Select.builder().select(new AliasedExpression(new MinusExpression(columnMax, columnUsed), "remaining"))
					.from(UserQuotasEntity.TABLE)
					.limit(1)
					.where(Conditions.isEqual(UserQuotasEntity.COL_EMAIL, SQL.literalOf(email)))
					.build(),
					null, r2dbc
				)
			).fetch().first().map(row -> ((Number) row.get("remaining")).intValue())
			.flatMap(remaining -> {
				if (remaining <= 0 || remaining >= nb) return Mono.error(new QuotaExceededException(quotaType));
				return incrementQuota(email, remaining, columnUsed, columnMax, quotaType, true);
			});
		});
	}
	
	private Mono<Void> decrementQuota(String email, Column columnUsed, long nb) {
		if (nb == 0) return Mono.empty();
		var query = DbUtils.update(
			Update.builder()
			.table(UserQuotasEntity.TABLE)
			.set(Assignments.value(columnUsed, SimpleFunction.create("GREATEST", List.of(SQL.literalOf(0), new MinusExpression(columnUsed, SQL.literalOf(nb))))))
			.where(Conditions.isEqual(UserQuotasEntity.COL_EMAIL, SQL.literalOf(email)))
			.build(),
			null,
			r2dbc
		);
		return r2dbc.getDatabaseClient().sql(query).fetch().rowsUpdated().then();
	}
	
	private Mono<Void> updateQuota(String email, Column columnUsed, Column columnMax, int diff, String quotaType) {
		if (diff == 0) return Mono.empty();
		var newUsed = diff < 0 ? SimpleFunction.create("GREATEST", List.of(SQL.literalOf(0), new MinusExpression(columnUsed, SQL.literalOf(-diff)))) : new PlusExpression(columnUsed, SQL.literalOf(diff));
		Condition condition = Conditions.isEqual(UserQuotasEntity.COL_EMAIL, SQL.literalOf(email));
		if (diff > 0) condition = condition.and(Conditions.isLessOrEqualTo(newUsed, columnMax));
		var query = DbUtils.update(
			Update.builder()
			.table(UserQuotasEntity.TABLE)
			.set(Assignments.value(columnUsed, newUsed))
			.where(condition)
			.build(),
			null,
			r2dbc
		);
		return r2dbc.getDatabaseClient().sql(query).fetch().rowsUpdated()
		.flatMap(updated -> updated > 0 ? Mono.empty() : Mono.error(new QuotaExceededException(quotaType)));
	}
	
	public Mono<Void> initUserQuotas(String email, long now) {
		return r2dbc.getDatabaseClient().sql(
			"WITH subscriptions AS (SELECT plans.*"
			+ " FROM user_subscriptions"
			+ " LEFT JOIN plans ON plans.name = user_subscriptions.plan_name"
			+ " AND user_subscriptions.starts_at <= " + now
			+ " AND (user_subscriptions.ends_at IS NULL OR user_subscriptions.ends_at >= " + now + ")"
			+ " WHERE user_subscriptions.user_email = " + SQL.literalOf(email)
			+ ") INSERT INTO user_quotas"
			+ " (email,collections_used,collections_max,trails_max,tracks_max,tracks_size_max,photos_max,photos_size_max,tags_max,trail_tags_max,shares_max)"
			+ " VALUES ("
			+ SQL.literalOf(email)
			+ ",1"
			+ ",(SELECT COALESCE(SUM(collections), 0) FROM subscriptions)"
			+ ",(SELECT COALESCE(SUM(trails), 0) FROM subscriptions)"
			+ ",(SELECT COALESCE(SUM(tracks), 0) FROM subscriptions)"
			+ ",(SELECT COALESCE(SUM(tracks_size), 0) FROM subscriptions)"
			+ ",(SELECT COALESCE(SUM(photos), 0) FROM subscriptions)"
			+ ",(SELECT COALESCE(SUM(photos_size), 0) FROM subscriptions)"
			+ ",(SELECT COALESCE(SUM(tags), 0) FROM subscriptions)"
			+ ",(SELECT COALESCE(SUM(trail_tags), 0) FROM subscriptions)"
			+ ",(SELECT COALESCE(SUM(shares), 0) FROM subscriptions)"
			+ ")"
		).fetch().rowsUpdated().then();
	}
	
	private Mono<Void> updateUserQuotas(String email, long now) {
		return r2dbc.getDatabaseClient().sql(
				"WITH subscriptions AS (SELECT plans.*"
				+ " FROM user_subscriptions"
				+ " LEFT JOIN plans ON plans.name = user_subscriptions.plan_name"
				+ " AND user_subscriptions.starts_at <= " + now
				+ " AND (user_subscriptions.ends_at IS NULL OR user_subscriptions.ends_at >= " + now + ")"
				+ " WHERE user_subscriptions.user_email = " + SQL.literalOf(email)
				+ ") UPDATE user_quotas SET"
				+ " collections_max = (SELECT COALESCE(SUM(collections), 0) FROM subscriptions),"
				+ " trails_max = (SELECT COALESCE(SUM(trails), 0) FROM subscriptions),"
				+ " tracks_max = (SELECT COALESCE(SUM(tracks), 0) FROM subscriptions),"
				+ " tracks_size_max = (SELECT COALESCE(SUM(tracks_size), 0) FROM subscriptions),"
				+ " photos_max = (SELECT COALESCE(SUM(photos), 0) FROM subscriptions),"
				+ " photos_size_max = (SELECT COALESCE(SUM(photos_size), 0) FROM subscriptions),"
				+ " tags_max = (SELECT COALESCE(SUM(tags), 0) FROM subscriptions),"
				+ " trail_tags_max = (SELECT COALESCE(SUM(trail_tags), 0) FROM subscriptions),"
				+ " shares_max = (SELECT COALESCE(SUM(shares), 0) FROM subscriptions)"
				+ " WHERE user_quotas.email = " + SQL.literalOf(email)
			).fetch().rowsUpdated().then();
	}
	
	private static final String QUERY_COMPUTE_QUOTAS =
		"WITH users_max AS (SELECT"
		+ " user_subscriptions.user_email as email,"
		+ " COALESCE(SUM(collections), 0) as collections_max, COALESCE(SUM(trails), 0) as trails_max,"
		+ " COALESCE(SUM(tracks), 0) as tracks_max, COALESCE(SUM(tracks_size), 0) as tracks_size_max,"
		+ " COALESCE(SUM(photos), 0) as photos_max, COALESCE(SUM(photos_size), 0) as photos_size_max,"
		+ " COALESCE(SUM(tags), 0) as tags_max, COALESCE(SUM(trail_tags), 0) as trail_tags_max, COALESCE(SUM(shares), 0) as shares_max"
		+ " FROM user_subscriptions"
		+ " LEFT JOIN plans ON plans.name = user_subscriptions.plan_name"
		+ " AND user_subscriptions.starts_at <= {now}"
		+ " AND (user_subscriptions.ends_at IS NULL OR user_subscriptions.ends_at >= {now})"
		+ " GROUP BY user_subscriptions.user_email"
		+ ") "
		+ "UPDATE user_quotas SET "
		+ "collections_used = (SELECT count(*) FROM collections WHERE collections.owner = user_quotas.email),"
		+ "trails_used = (SELECT count(*) FROM trails WHERE trails.owner = user_quotas.email),"
		+ "tracks_used = (SELECT count(*) FROM tracks WHERE tracks.owner = user_quotas.email),"
		+ "tracks_size_used = (SELECT COALESCE(sum(octet_length(tracks.data)), 0) FROM tracks WHERE tracks.owner = user_quotas.email),"
		+ "photos_used = (SELECT count(*) FROM photos WHERE photos.owner = user_quotas.email),"
		+ "photos_size_used = (SELECT COALESCE(sum(files.size),0) FROM photos left join files on files.id = photos.file_id WHERE photos.owner = user_quotas.email),"
		+ "tags_used = (SELECT count(*) FROM tags WHERE tags.owner = user_quotas.email),"
		+ "trail_tags_used = (SELECT count(*) FROM trails_tags WHERE trails_tags.owner = user_quotas.email),"
		+ "shares_used = (SELECT count(*) FROM shares WHERE shares.owner = user_quotas.email),"
		+ "collections_max = (SELECT collections_max FROM users_max WHERE users_max.email = user_quotas.email),"
		+ "trails_max = (SELECT trails_max FROM users_max WHERE users_max.email = user_quotas.email),"
		+ "tracks_max = (SELECT tracks_max FROM users_max WHERE users_max.email = user_quotas.email),"
		+ "tracks_size_max = (SELECT tracks_size_max FROM users_max WHERE users_max.email = user_quotas.email),"
		+ "photos_max = (SELECT photos_max FROM users_max WHERE users_max.email = user_quotas.email),"
		+ "photos_size_max = (SELECT photos_size_max FROM users_max WHERE users_max.email = user_quotas.email),"
		+ "tags_max = (SELECT tags_max FROM users_max WHERE users_max.email = user_quotas.email),"
		+ "trail_tags_max = (SELECT trail_tags_max FROM users_max WHERE users_max.email = user_quotas.email),"
		+ "shares_max = (SELECT shares_max FROM users_max WHERE users_max.email = user_quotas.email)"
		;

	@Scheduled(initialDelayString = "1d", fixedDelayString = "1d")
	public Mono<Void> computeQuotas() {
		MutableLong start = new MutableLong();
		return Mono.defer(() -> {
			log.info("Computing users' quotas");
			start.setValue(System.currentTimeMillis());
			return r2dbc.getDatabaseClient().sql(QUERY_COMPUTE_QUOTAS.replace("{now}", "" + System.currentTimeMillis())).fetch().rowsUpdated();			
		}).flatMap(nb  -> {
			log.info("Users quotas usage updated: {} in {} ms.", nb, System.currentTimeMillis() - start.getValue());
			return Mono.empty();
		});
	}
	
}
