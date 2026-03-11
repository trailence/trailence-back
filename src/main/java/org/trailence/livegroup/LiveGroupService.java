package org.trailence.livegroup;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.UnauthorizedException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.livegroup.db.LiveGroupEntity;
import org.trailence.livegroup.db.LiveGroupMemberEntity;
import org.trailence.livegroup.db.LiveGroupMemberRepository;
import org.trailence.livegroup.db.LiveGroupRepository;
import org.trailence.livegroup.dto.LiveGroup;
import org.trailence.livegroup.dto.LiveGroupRequest;
import org.trailence.livegroup.dto.UpdateMyPositionRequest;
import org.trailence.stats.EventType;
import org.trailence.stats.StatsService;
import org.trailence.trail.ShareService;
import org.trailence.trail.TrailLinkService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class LiveGroupService {
	
	private final LiveGroupRepository groupRepo;
	private final LiveGroupMemberRepository memberRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final ShareService shareService;
	private final TrailLinkService linkService;
	private final StatsService stats;
	@Lazy @Autowired @SuppressWarnings("java:S6813")
	private LiveGroupService self;
	
	@Value("${trailence.live-group.expiration:7d}")
	private Duration maxDuration;
	
	private static final String FIELD_GROUP_NAME = "groupName";
	private static final String FIELD_MY_NAME = "myName";
	
	public Mono<List<LiveGroup>> getGroups(String anonymousMemberId, Authentication auth) {
		if (anonymousMemberId != null && anonymousMemberId.indexOf('@') >= 0) return Mono.error(new BadRequestException(""));
		String memberId = Optional.ofNullable(auth).flatMap(a -> Optional.ofNullable(a.getPrincipal())).map(Object::toString).orElse(anonymousMemberId);
		if (memberId == null) return Mono.error(new BadRequestException(""));
		return groupRepo.findAllForMemberId(memberId)
		.collectList()
		.flatMap(groups -> this.fetchDtos(groups, memberId));
	}
	
	public Mono<List<LiveGroup>> updateMyPosition(UpdateMyPositionRequest request, Authentication auth) {
		if (request.getMemberId() != null && request.getMemberId().indexOf('@') >= 0) return Mono.error(new BadRequestException(""));
		String memberId = Optional.ofNullable(auth).flatMap(a -> Optional.ofNullable(a.getPrincipal())).map(Object::toString).orElse(request.getMemberId());
		if (memberId == null) return Mono.error(new BadRequestException(""));
		Mono<Void> update;
		if (request.getPosition() != null) update = memberRepo.updatePosition(request.getPosition().getLat(), request.getPosition().getLng(), request.getPositionAt(), memberId);
		else update = Mono.empty();
		return update.then(getGroups(request.getMemberId(), auth));
	}
	
	public Mono<LiveGroup> createGroup(LiveGroupRequest request, Authentication auth) {
		if (auth == null) return Mono.error(new UnauthorizedException());
		ValidationUtils.field(FIELD_GROUP_NAME, request.getGroupName()).notNull().notBlank().maxLength(30);
		ValidationUtils.field(FIELD_MY_NAME, request.getMyName()).notNull().notBlank().maxLength(25);
		String owner = TrailenceUtils.email(auth);
		String slug = RandomStringUtils.secure().nextAlphanumeric(128);
		long now = System.currentTimeMillis();
		LiveGroupEntity groupEntity = new LiveGroupEntity(
			UUID.randomUUID(),
			owner,
			slug,
			request.getGroupName(),
			now,
			request.getTrailOwner(),
			request.getTrailUuid(),
			request.getTrailShared() != null && request.getTrailShared().booleanValue()
		);
		LiveGroupMemberEntity memberEntity = new LiveGroupMemberEntity(
			UUID.randomUUID(),
			groupEntity.getUuid(),
			owner,
			request.getMyName(),
			now,
			null, null, null // position
		);
		return this.self.createGroup(groupEntity, memberEntity)
		.flatMap(tuple -> toDto(tuple.getT1(), Stream.of(tuple.getT2()), owner))
		.flatMap(dto -> stats.addEvent(EventType.NEW_LIVE_GROUP, Map.of()).thenReturn(dto));
	}
	
	@Transactional
	public Mono<Tuple2<LiveGroupEntity, LiveGroupMemberEntity>> createGroup(LiveGroupEntity groupEntity, LiveGroupMemberEntity memberEntity) {
		return groupRepo.countByOwner(groupEntity.getOwner())
		.flatMap(count -> {
			if (count.longValue() >= 10) return Mono.error(new ForbiddenException("max-live-group-exceeded"));
			return r2dbc.insert(groupEntity).zipWith(r2dbc.insert(memberEntity));
		});
	}
	
	public Mono<LiveGroup> updateGroup(String slug, LiveGroupRequest request, Authentication auth) {
		if (auth == null) return Mono.error(new UnauthorizedException());
		ValidationUtils.field(FIELD_GROUP_NAME, request.getGroupName()).notNull().notBlank().maxLength(30);
		ValidationUtils.field(FIELD_MY_NAME, request.getMyName()).nullable().notBlank().maxLength(25);
		String owner = TrailenceUtils.email(auth);
		return groupRepo.findOneBySlugAndOwner(slug, owner)
		.switchIfEmpty(Mono.error(new LiveGroupNotFound(slug)))
		.flatMap(groupEntity -> {
			if (!updateGroupEntity(groupEntity, request)) return Mono.just(groupEntity);
			return groupRepo.save(groupEntity);
		})
		.flatMap(groupEntity -> {
			if (request.getMyName() == null) return Mono.just(groupEntity);
			return memberRepo.findOneByGroupUuidAndMemberId(groupEntity.getUuid(), owner)
			.switchIfEmpty(Mono.error(new LiveGroupNotFound(slug)))
			.flatMap(memberEntity -> {
				if (memberEntity.getMemberName().equals(request.getMyName())) return Mono.just(groupEntity);
				memberEntity.setMemberName(request.getMyName());
				return memberRepo.save(memberEntity).thenReturn(groupEntity);
			});
		})
		.flatMap(groupEntity -> fetchDtos(List.of(groupEntity), owner))
		.map(list -> list.getFirst());
	}
	
	private boolean updateGroupEntity(LiveGroupEntity groupEntity, LiveGroupRequest request) {
		boolean updated = false;
		if (!request.getGroupName().equals(groupEntity.getName())) {
			groupEntity.setName(request.getGroupName());
			updated = true;
		}
		if (!Objects.equals(request.getTrailOwner(), groupEntity.getTrailOwner())) {
			groupEntity.setTrailOwner(request.getTrailOwner());
			updated = true;
		}
		if (!Objects.equals(request.getTrailUuid(), groupEntity.getTrailUuid())) {
			groupEntity.setTrailUuid(request.getTrailUuid());
			updated = true;
		}
		boolean shared = request.getTrailShared() != null && request.getTrailShared().booleanValue();
		if (shared != groupEntity.isTrailShared()) {
			groupEntity.setTrailShared(shared);
			updated = true;
		}
		return updated;
	}
	
	public Mono<LiveGroup> joinGroup(String slug, String myName, String anonymousMemberId, Authentication auth) {
		if (anonymousMemberId != null && anonymousMemberId.indexOf('@') >= 0) return Mono.error(new BadRequestException(""));
		String memberId = Optional.ofNullable(auth).flatMap(a -> Optional.ofNullable(a.getPrincipal())).map(Object::toString).orElse(anonymousMemberId);
		if (memberId == null) return Mono.error(new BadRequestException(""));
		ValidationUtils.field(FIELD_MY_NAME, myName).notNull().notBlank().maxLength(25);
		return groupRepo.findOneBySlug(slug)
		.switchIfEmpty(Mono.error(new LiveGroupNotFound(slug)))
		.flatMap(groupEntity ->
			memberRepo.findOneByGroupUuidAndMemberId(groupEntity.getUuid(), memberId)
			.flatMap(memberEntity -> {
				if (memberEntity.getMemberName().equals(myName)) return Mono.just(memberEntity);
				memberEntity.setMemberName(myName);
				return memberRepo.save(memberEntity);
			})
			.switchIfEmpty(Mono.defer(() -> {
				LiveGroupMemberEntity memberEntity = new LiveGroupMemberEntity(UUID.randomUUID(), groupEntity.getUuid(), memberId, myName, System.currentTimeMillis(), null, null, null);
				return r2dbc.insert(memberEntity);
			}))
			.then(fetchDtos(List.of(groupEntity), memberId))
			.map(list -> list.getFirst())
		);
	}
	
	public Mono<Void> deleteGroup(String slug, Authentication auth) {
		if (auth == null) return Mono.error(new UnauthorizedException());
		String owner = TrailenceUtils.email(auth);
		return groupRepo.findOneBySlugAndOwner(slug, owner)
		.switchIfEmpty(Mono.error(new LiveGroupNotFound(slug)))
		.flatMap(this::removeGroup);
	}
	
	public Mono<Void> leaveGroup(String slug, String anonymousMemberId, Authentication auth) {
		if (anonymousMemberId != null && anonymousMemberId.indexOf('@') >= 0) return Mono.error(new BadRequestException(""));
		String memberId = Optional.ofNullable(auth).flatMap(a -> Optional.ofNullable(a.getPrincipal())).map(Object::toString).orElse(anonymousMemberId);
		if (memberId == null) return Mono.error(new BadRequestException(""));
		return groupRepo.findOneBySlug(slug)
		.switchIfEmpty(Mono.error(new LiveGroupNotFound(slug)))
		.flatMap(groupEntity -> {
			if (groupEntity.getOwner().equals(memberId)) return removeGroup(groupEntity);
			return memberRepo.findOneByGroupUuidAndMemberId(groupEntity.getUuid(), memberId)
			.switchIfEmpty(Mono.error(new LiveGroupNotFound(slug)))
			.flatMap(memberRepo::delete);
		});
	}
	
	
	private Mono<List<LiveGroup>> fetchDtos(List<LiveGroupEntity> groupEntities, String myMemberId) {
		if (groupEntities.isEmpty()) return Mono.just(List.of());
		var groupUuids = groupEntities.stream().map(LiveGroupEntity::getUuid).distinct().toList();
		return memberRepo.findAllByGroupUuidIn(groupUuids).collectList()
		.flatMap(members ->
			Flux.fromIterable(groupEntities)
			.flatMap(group -> toDto(group, members.stream().filter(m -> m.getGroupUuid().equals(group.getUuid())), myMemberId), 1, 1)
			.collectList()
		);
	}
	
	private Mono<LiveGroup> toDto(LiveGroupEntity groupEntity, Stream<LiveGroupMemberEntity> membersEntities, String myMemberId) {
		Mono<Tuple2<Boolean, Optional<String>>> hasAccess;
		if (groupEntity.getOwner().equals(myMemberId))
			hasAccess = Mono.just(Tuples.of(true, Optional.empty()));
		else if (groupEntity.getTrailOwner() == null || !groupEntity.isTrailShared())
			hasAccess = Mono.just(Tuples.of(false, Optional.empty()));
		else
			hasAccess =
				(myMemberId.indexOf('@') < 0 ?
					Mono.just(false) :
					shareService.hasAccessThroughShare(myMemberId, groupEntity.getTrailOwner(), groupEntity.getTrailUuid()))
				.flatMap(throughShare -> {
					if (throughShare.booleanValue()) return Mono.just(Tuples.of(true, Optional.empty()));
					return linkService.getTrailLink(groupEntity.getTrailOwner(), groupEntity.getTrailUuid())
						.map(l -> Tuples.of(true, Optional.of(l)))
						.switchIfEmpty(Mono.just(Tuples.of(false, Optional.empty())));
				});
		return hasAccess.map(share -> new LiveGroup(
			groupEntity.getSlug(),
			groupEntity.getName(),
			groupEntity.getStartedAt(),
			Instant.ofEpochMilli(groupEntity.getStartedAt()).plus(maxDuration).toEpochMilli(),
			share.getT1().booleanValue() ? share.getT2().map(_ -> "link").orElse(groupEntity.getTrailOwner()) : null,
			share.getT1().booleanValue() ? share.getT2().orElse(groupEntity.getTrailUuid()) : null,
			share.getT1().booleanValue() && groupEntity.isTrailShared(),
			toDto(membersEntities, myMemberId, groupEntity.getOwner())
		));
	}
	
	private List<LiveGroup.Member> toDto(Stream<LiveGroupMemberEntity> membersEntities, String myMemberId, String owner) {
		return membersEntities.map(m -> toDto(m, myMemberId, owner)).toList();
	}
	
	private LiveGroup.Member toDto(LiveGroupMemberEntity entity, String myMemberId, String owner) {
		LiveGroup.Position position;
		if (entity.getLastPositionLat() != null && entity.getLastPositionLon() != null)
			position = new LiveGroup.Position(entity.getLastPositionLat(), entity.getLastPositionLon());
		else
			position = null;
		return new LiveGroup.Member(entity.getUuid().toString(), entity.getMemberName(), position, entity.getLastPositionAt(), entity.getMemberId().equals(myMemberId), entity.getMemberId().equals(owner));
	}

	private Mono<Void> removeGroup(LiveGroupEntity group) {
		return memberRepo.deleteAllByGroupUuid(group.getUuid()).then(groupRepo.delete(group));
	}

	@Scheduled(fixedRate = 120, timeUnit = TimeUnit.MINUTES, initialDelay = 10)
	public void cleanup() {
		var maxTime = Instant.now().minus(maxDuration).toEpochMilli();
		groupRepo.findExpired(maxTime)
		.collectList()
		.flatMap(list -> {
			if (list.isEmpty()) return Mono.empty();
			return memberRepo.deleteAllByGroupUuidIn(list)
			.then(groupRepo.deleteAllById(list))
			.then(Mono.fromRunnable(this::cleanup));
		}).subscribe();
	}
	
}
