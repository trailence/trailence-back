package org.trailence.trail;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.global.rest.AuthDetails;
import org.trailence.notifications.NotificationsService;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.ShareRecipientEntity;
import org.trailence.trail.db.SharedCollectionMemberRepository;
import org.trailence.trail.db.SharedCollectionMemberRepository.SharedCollectionInfo;
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
import reactor.util.function.Tuple3;
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
	private final SharedCollectionMemberRepository sharedCollectionMemberRepo;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private TrailTagService self;
	
	public Flux<TrailTag> getAll(Authentication auth) {
		String caller = TrailenceUtils.email(auth);
		Flux<TrailTag> owned = repo.findAllByOwner(caller).map(e -> toDto(e, null));
		Flux<TrailTag> fromSharedCollections = AuthDetails.getVersion(auth) < SharedCollectionUtils.MIN_VERSION_FOR_SHARED ? Flux.empty() :
			sharedCollectionMemberRepo.findAllByOwner(caller)
			.flatMap(member ->
				repo.findAllByOwner(SharedCollectionUtils.SHARED_OWNER_PREFIX + member.getSharedCollectionUuid())
				.map(e -> toDto(e, member.getUuid()))
			, 1, 1);
		return Flux.concat(owned, fromSharedCollections);
	}
	
	public Mono<List<TrailTag>> bulkCreate(Collection<TrailTag> dtos, Authentication auth) {
		String caller = TrailenceUtils.email(auth);
		Map<String, Tuple3<Set<UUID>, Set<UUID>, Set<Tuple2<UUID, UUID>>>> uuidsByOwner = new HashMap<>();
		List<Throwable> errors = new LinkedList<>();
		for (var dto : dtos) {
			try {
				ValidationUtils.field("tagUuid", dto.getTagUuid()).notNull().isUuid();
				ValidationUtils.field("trailUuid", dto.getTrailUuid()).notNull().isUuid();
				var tagUuid = UUID.fromString(dto.getTagUuid());
				var trailUuid = UUID.fromString(dto.getTrailUuid());
				if (!SharedCollectionUtils.isSharedCollectionOwner(dto.getOwner()))
					dto.setOwner(caller);
				var tuple = uuidsByOwner.computeIfAbsent(dto.getOwner(), _ -> Tuples.of(new HashSet<>(), new HashSet<>(), new HashSet<>()));
				tuple.getT1().add(tagUuid);
				tuple.getT2().add(trailUuid);
				tuple.getT3().add(Tuples.of(tagUuid, trailUuid));
			} catch (Exception e) {
				errors.add(e);
			}
		}
		if (uuidsByOwner.isEmpty()) {
			if (errors.isEmpty()) return Mono.just(List.of());
			return Mono.error(errors.getFirst());
		}
		Flux<Tuple3<TrailTagEntity, Optional<SharedCollectionInfo>, String>> entities = Flux.fromIterable(uuidsByOwner.entrySet())
		.flatMap(entry -> {
			var tagsUuids = entry.getValue().getT1();
			var trailsUuids = entry.getValue().getT2();
			var pairs = entry.getValue().getT3();
			var ownerForCaller = entry.getKey();
			Mono<Tuple3<String, Optional<SharedCollectionInfo>, String>> getOwner = SharedCollectionUtils.isSharedCollectionOwner(ownerForCaller) ?
				sharedCollectionMemberRepo.getInfoByCallerAndUuid(caller, SharedCollectionUtils.getSharedCollectionUuid(ownerForCaller))
				.map(sharedInfo -> Tuples.of(SharedCollectionUtils.SHARED_OWNER_PREFIX + sharedInfo.getColUuid(), Optional.of(sharedInfo), sharedInfo.getColOwner()))
				: Mono.just(Tuples.of(caller, Optional.empty(), caller));
			return getOwner
			.flatMapMany(ownerTuple ->
				Mono.zip(
					trailRepo.findAllByUuidInAndOwner(trailsUuids, ownerTuple.getT1()).collectList().publishOn(Schedulers.parallel()),
					tagRepo.findAllByUuidInAndOwner(tagsUuids, ownerTuple.getT1()).collectList().publishOn(Schedulers.parallel())
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
					var dto = dtos.stream().filter(d -> d.getOwner().equals(ownerForCaller) && tuple.getT1().toString().equals(d.getTagUuid()) && tuple.getT2().toString().equals(d.getTrailUuid())).findAny();
					return Tuples.of(toEntity(dto.get(), ownerTuple.getT1()), ownerTuple.getT2(), ownerTuple.getT3());
				})
			);
		}, 1, 1);
		return BulkUtils.handleOperationsResult(
			BulkUtils.parallelSingleOperations(
				entities,
				tuple -> self.createWithQuota(tuple.getT1(), tuple.getT3())
					.onErrorResume(DuplicateKeyException.class, _ -> Mono.just(tuple.getT1()))
					.map(newEntity -> Tuples.of(newEntity, tuple.getT2()))
			),
			List.<Tuple2<TrailTagEntity, Optional<SharedCollectionInfo>>>of(),
			errors
		)
		.doOnNext(list -> handleNotificationsForNewTrailTags(list.stream().filter(t -> t.getT2().isEmpty()).map(Tuple2::getT1).toList(), caller))
		.map(list -> list.stream().map(t -> toDto(t.getT1(), t.getT2().map(s -> s.getMemberUuid()).orElse(null))).toList());
	}
	
	@Transactional
	public Mono<TrailTagEntity> createWithQuota(TrailTagEntity entity, String quotaOwner) {
		return quotaService.addTrailTags(quotaOwner, 1)
		.flatMap(_ -> r2dbc.insert(entity));
	}
	
	@Transactional
	public Mono<Void> bulkDelete(Collection<TrailTag> dtos, Authentication auth) {
		String caller = TrailenceUtils.email(auth);
		Map<String, Set<Tuple2<UUID, UUID>>> uuidsByOwner = new HashMap<>();
		for (var dto : dtos) {
			var owner = SharedCollectionUtils.isSharedCollectionOwner(dto.getOwner()) ? dto.getOwner() : caller;
			uuidsByOwner.computeIfAbsent(owner, _ -> new HashSet<>())
			.add(Tuples.of(UUID.fromString(dto.getTagUuid()), UUID.fromString(dto.getTrailUuid())));
		}
		return Flux.fromIterable(uuidsByOwner.entrySet())
		.flatMap(entry ->
			entry.getKey().equals(caller) ?
				Mono.just(Tuples.of(caller, caller, entry.getValue()))
				: sharedCollectionMemberRepo.getInfoByCallerAndUuid(caller, SharedCollectionUtils.getSharedCollectionUuid(entry.getKey()))
					.map(sharedInfo -> Tuples.of(SharedCollectionUtils.SHARED_OWNER_PREFIX + sharedInfo.getColUuid(), sharedInfo.getColOwner(), entry.getValue()))
		, 1, 1)
		.flatMap(ownerTuple -> {
			var ownerInDb = ownerTuple.getT1();
			var quotaOwner = ownerTuple.getT2();
			var uuids = ownerTuple.getT3();
			log.info("Deleting {} trail tags for {}", uuids.size(), ownerInDb);
			return Flux.fromIterable(uuids)
			.flatMap(tuple -> repo.deleteByTagUuidAndTrailUuidAndOwner(tuple.getT1(), tuple.getT2(), ownerInDb), 2, 4)
			.reduce(0L, (p, n) -> p + n)
			.flatMap(removed -> quotaService.trailTagsDeleted(quotaOwner, removed))
			.then(Mono.fromRunnable(() -> log.info("Trail tags deleted ({} for {})", uuids.size(), ownerInDb)));
		})
		.then();
	}
	
	@Transactional
	public Mono<Void> trailsDeleted(Set<UUID> trailsUuids, String owner, String quotaOwner) {
		log.info("Deleting trail tags of {} trails for {}", trailsUuids.size(), owner);
		return repo.deleteAllByTrailUuidInAndOwner(trailsUuids, owner)
		.flatMap(removed -> quotaService.trailTagsDeleted(quotaOwner, removed))
		.then(Mono.fromRunnable(() -> log.info("Trail tags deleted ({} trails for {})", trailsUuids.size(), owner)));
	}
	
	@Transactional
	public Mono<Void> tagsDeleted(Set<UUID> tagsUuids, String owner, String quotaOwner) {
		log.info("Deleting trail tags of {} tags for {}", tagsUuids.size(), owner);
		return repo.deleteAllByTagUuidInAndOwner(tagsUuids, owner)
		.flatMap(removed -> quotaService.trailTagsDeleted(quotaOwner, removed))
		.then(Mono.fromRunnable(() -> log.info("Trail tags deleted ({} tags for {})", tagsUuids.size(), owner)));
	}
	
	private TrailTag toDto(TrailTagEntity entity, UUID memberUuid) {
		return new TrailTag(
				memberUuid != null ? SharedCollectionUtils.SHARED_OWNER_PREFIX + memberUuid : entity.getOwner(),
			entity.getTagUuid().toString(),
			entity.getTrailUuid().toString(),
			entity.getCreatedAt()
		);
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
    	)), 1, 1).subscribe();
    }

}
