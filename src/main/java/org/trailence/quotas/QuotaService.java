package org.trailence.quotas;

import java.util.List;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AliasedExpression;
import org.springframework.data.relational.core.sql.Assignments;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.MinusExpression;
import org.trailence.global.db.PlusExpression;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.QuotaExceededException;
import org.trailence.quotas.db.UserQuotaInit;
import org.trailence.quotas.db.UserQuotasEntity;
import org.trailence.quotas.db.UserQuotasRepository;
import org.trailence.quotas.dto.UserQuotas;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {
	
	private final UserQuotasRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	
	public Mono<UserQuotas> getUserQuotas(String email) {
		return repo.findById(email.toLowerCase()).map(this::toDto);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	@Transactional
	public Mono<UserQuotas> updateUserQuotas(String email, UserQuotas newQuotas) {
		return repo.findById(email.toLowerCase())
		.switchIfEmpty(Mono.error(new NotFoundException("user", email)))
		.flatMap(entity -> {
			entity.setCollectionsMax(newQuotas.getCollectionsMax());
			entity.setTrailsMax(newQuotas.getTrailsMax());
			entity.setTracksMax(newQuotas.getTracksMax());
			entity.setTracksSizeMax(newQuotas.getTracksSizeMax());
			entity.setPhotosMax(newQuotas.getPhotosMax());
			entity.setPhotosSizeMax(newQuotas.getPhotosSizeMax());
			entity.setTagsMax(newQuotas.getTagsMax());
			entity.setTrailTagsMax(newQuotas.getTrailTagsMax());
			entity.setSharesMax(newQuotas.getSharesMax());
			return repo.save(entity);
		})
		.map(this::toDto);
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
	
	@Scheduled(initialDelayString = "30m", fixedDelayString = "1d")
	public Mono<Void> computeQuotas() {
		return Mono.defer(() -> {
			log.info("Computing users' quotas");
			return r2dbc.getDatabaseClient().sql(UserQuotaInit.QUERY_COMPUTE_USAGE).fetch().rowsUpdated();			
		}).flatMap(nb  -> {
			log.info("Users quotas updated: {}", nb);
			return Mono.empty();
		});
	}
	
}
