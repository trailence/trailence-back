package org.trailence.trail;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.notifications.NotificationsService;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.ShareRecipientEntity;
import org.trailence.trail.db.TagRepository;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.db.TrailTagRepository;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.TrailTag;
import org.trailence.user.db.UserEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrailTagService {

	private final TrailTagRepository repo;
	private final TrailRepository trailRepo;
	private final TagRepository tagRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final QuotaService quotaService;
	private final NotificationsService notifService;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private TrailTagService self;
	
	public Flux<TrailTag> getAll(Authentication auth) {
		return repo.findAllByOwner(auth.getPrincipal().toString()).map(this::toDto);
	}
	
	public Mono<List<TrailTag>> bulkCreate(Collection<TrailTag> dtos, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		Set<UUID> tagsUuids = new HashSet<>();
		Set<UUID> trailsUuids = new HashSet<>();
		Set<Tuple2<UUID, UUID>> pairs = new HashSet<>();
		List<Throwable> errors = new LinkedList<>();
		dtos.forEach(dto -> {
			try {
				ValidationUtils.field("tagUuid", dto.getTagUuid()).notNull().isUuid();
				ValidationUtils.field("trailUuid", dto.getTrailUuid()).notNull().isUuid();
				var tagUuid = UUID.fromString(dto.getTagUuid());
				tagsUuids.add(tagUuid);
				var trailUuid = UUID.fromString(dto.getTrailUuid());
				trailsUuids.add(trailUuid);
				pairs.add(Tuples.of(tagUuid, trailUuid));
			} catch (Exception e) {
				errors.add(e);
			}
		});
		if (pairs.isEmpty()) {
			if (errors.isEmpty()) return Mono.just(List.of());
			return Mono.error(errors.getFirst());
		}
		Flux<TrailTagEntity> entities = Mono.zip(
			trailRepo.findAllByUuidInAndOwner(trailsUuids, owner).collectList().publishOn(Schedulers.parallel()),
			tagRepo.findAllByUuidInAndOwner(tagsUuids, owner).collectList().publishOn(Schedulers.parallel())
		).flatMapMany(existing -> {
			var valid = pairs.stream().filter(tuple -> {
				var trail = existing.getT1().stream().filter(t -> t.getUuid().equals(tuple.getT2())).findAny();
				if (trail.isEmpty()) return false;
				var tag = existing.getT2().stream().filter(t -> t.getUuid().equals(tuple.getT1())).findAny();
				if (tag.isEmpty()) return false;
				return trail.get().getCollectionUuid().equals(tag.get().getCollectionUuid());
			}).toList();
			if (valid.isEmpty()) return Flux.error(new BadRequestException("invalid-input", "trailUuid or tagUuid not found, or they do not belong to the same collection"));
			return Flux.fromIterable(valid);
		}).map(tuple -> {
			var dto = dtos.stream().filter(d -> tuple.getT1().toString().equals(d.getTagUuid()) && tuple.getT2().toString().equals(d.getTrailUuid())).findAny();
			return toEntity(dto.get(), owner);
		});
		return BulkUtils.handleOperationsResult(
			BulkUtils.parallelSingleOperations(
				entities,
				entity -> self.createWithQuota(entity).onErrorResume(DuplicateKeyException.class, e -> Mono.just(entity))
			),
			List.<TrailTagEntity>of(),
			errors
		)
		.doOnNext(list -> handleNotificationsForNewTrailTags(list, owner))
		.map(list -> list.stream().map(this::toDto).toList());
	}
	
	@Transactional
	public Mono<TrailTagEntity> createWithQuota(TrailTagEntity entity) {
		return quotaService.addTrailTags(entity.getOwner(), 1)
		.flatMap(nb -> r2dbc.insert(entity));
	}
	
	@Transactional
	public Mono<Void> bulkDelete(Collection<TrailTag> dtos, Authentication auth) {
		String owner = auth.getPrincipal().toString();
		log.info("Deleting {} trail tags for {}", dtos.size(), owner);
		return Flux.fromIterable(new HashSet<>(dtos.stream().map(dto -> Tuples.of(UUID.fromString(dto.getTagUuid()), UUID.fromString(dto.getTrailUuid()))).toList()))
		.flatMap(tuple -> repo.deleteByTagUuidAndTrailUuidAndOwner(tuple.getT1(), tuple.getT2(), owner), 3, 6)
		.reduce(0L, (p, n) -> p + n)
		.flatMap(removed -> quotaService.trailTagsDeleted(owner, removed))
		.then(Mono.fromRunnable(() -> log.info("Trail tags deleted ({} for {})", dtos.size(), owner)));
	}
	
	@Transactional
	public Mono<Void> trailsDeleted(Set<UUID> trailsUuids, String owner) {
		log.info("Deleting trail tags of {} trails for {}", trailsUuids.size(), owner);
		return repo.deleteAllByTrailUuidInAndOwner(trailsUuids, owner)
		.flatMap(removed -> quotaService.trailTagsDeleted(owner, removed))
		.then(Mono.fromRunnable(() -> log.info("Trail tags deleted ({} trails for {})", trailsUuids.size(), owner)));
	}
	
	@Transactional
	public Mono<Void> tagsDeleted(Set<UUID> tagsUuids, String owner) {
		log.info("Deleting trail tags of {} tags for {}", tagsUuids.size(), owner);
		return repo.deleteAllByTagUuidInAndOwner(tagsUuids, owner)
		.flatMap(removed -> quotaService.trailTagsDeleted(owner, removed))
		.then(Mono.fromRunnable(() -> log.info("Trail tags deleted ({} tags for {})", tagsUuids.size(), owner)));
	}
	
	private TrailTag toDto(TrailTagEntity entity) {
		return new TrailTag(entity.getTagUuid().toString(), entity.getTrailUuid().toString(), entity.getCreatedAt());
	}
	
	private TrailTagEntity toEntity(TrailTag dto, String owner) {
		return new TrailTagEntity(UUID.fromString(dto.getTagUuid()), UUID.fromString(dto.getTrailUuid()), owner, System.currentTimeMillis());
	}
	
    private void handleNotificationsForNewTrailTags(List<TrailTagEntity> trailTags, String owner) {
    	if (trailTags.isEmpty()) return;
    	// notifications for new trails in a share => can only be a share of a collection
    	Set<UUID> tags = trailTags.stream().map(t -> t.getTagUuid()).collect(Collectors.toSet());
    	String sql = new SqlBuilder()
    	.select(
    		ShareElementEntity.COL_ELEMENT_UUID,
    		UserEntity.COL_EMAIL,
    		ShareEntity.COL_NAME,
    		ShareEntity.COL_UUID
    	)
    	.from(ShareEntity.TABLE)
    	.leftJoinTable(
    		ShareElementEntity.TABLE,
    		Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID)
			.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_OWNER)),
			null
		)
    	.leftJoinTable(
    		ShareRecipientEntity.TABLE,
    		Conditions.isEqual(ShareRecipientEntity.COL_UUID, ShareEntity.COL_UUID)
			.and(Conditions.isEqual(ShareRecipientEntity.COL_OWNER, ShareEntity.COL_OWNER)),
			null
    	)
    	.leftJoinTable(UserEntity.TABLE, Conditions.isEqual(ShareRecipientEntity.COL_RECIPIENT, UserEntity.COL_EMAIL), null)
    	.where(
    		Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TAG.name()))
    		.and(Conditions.isEqual(ShareEntity.COL_OWNER, SQL.literalOf(owner)))
    		.and(Conditions.in(ShareElementEntity.COL_ELEMENT_UUID, tags.stream().map(uuid -> SQL.literalOf(uuid.toString())).toList()))
    		.and(Conditions.not(Conditions.isNull(UserEntity.COL_PASSWORD)))
    	)
    	.build();
    	
    	r2dbc.query(
    		DbUtils.operation(sql, null),
    		row -> Tuples.of(
    			row.get(ShareElementEntity.COL_ELEMENT_UUID.getName().toString(), UUID.class),
    			row.get(UserEntity.COL_EMAIL.getName().toString(), String.class),
    			row.get(ShareEntity.COL_NAME.getName().toString(), String.class),
    			row.get(ShareEntity.COL_UUID.getName().toString(), UUID.class)
    		)
    	).all()
    	.flatMap(share -> notifService.create(share.getT2(), "shares.new_trails_in_share", List.of(
    		owner,
    		Long.toString(trailTags.stream().filter(t -> t.getTagUuid().equals(share.getT1())).count()),
    		share.getT4().toString(),
    		share.getT3()
    	))).subscribe();
    }

}
