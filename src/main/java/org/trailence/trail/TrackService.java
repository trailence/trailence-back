package org.trailence.trail;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.UuidAndOwner;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.global.rest.AuthDetails;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.TrackStorage.V1.StoredData;
import org.trailence.trail.db.ShareRecipientEntity;
import org.trailence.trail.db.SharedCollectionEntity;
import org.trailence.trail.db.SharedCollectionMemberEntity;
import org.trailence.trail.db.SharedCollectionMemberRepository;
import org.trailence.trail.db.SharedCollectionRepository;
import org.trailence.trail.db.TrackEntity;
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.dto.Track;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackService {

	private final TrackRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	private final QuotaService quotaService;
	private final ShareService shareService;
	private final SharedCollectionRepository sharedCollectionRepo;
	private final SharedCollectionMemberRepository sharedCollectionMemberRepo;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private TrackService self;
	
	private static final long MAX_DATA_SIZE = 512L * 1024;
	
	public Mono<Track> createTrack(Track track, Authentication auth) {
		String owner = track.getOwner();
		String user = TrailenceUtils.email(auth);
		Mono<Optional<SharedCollectionEntity>> sharedCollection;
		if (SharedCollectionUtils.isSharedCollectionOwner(owner))
			sharedCollection = sharedCollectionRepo.getSharedCollectionHavingMember(SharedCollectionUtils.getSharedCollectionUuid(owner), user)
				.map(Optional::of).switchIfEmpty(Mono.error(new NotFoundException("shared_collection", owner)));
		else
			sharedCollection = Mono.just(Optional.empty());
		
		return sharedCollection
		.flatMap(colOpt -> {
			var col = colOpt.orElse(null);
			validate(track);
			TrackEntity entity = new TrackEntity();
			entity.setUuid(UUID.fromString(track.getUuid()));
			entity.setOwner(col == null ? user : SharedCollectionUtils.SHARED_OWNER_PREFIX + col.getUuid());
			entity.setCreatedAt(System.currentTimeMillis());
			entity.setUpdatedAt(entity.getCreatedAt());
			try {
				entity.setData(TrackStorage.V1V2Bridge.v1DtoToV2(new StoredData(track.getS(), track.getWp())));
			} catch (Exception e) {
				return Mono.error(e);
			}
			if (entity.getData().length > MAX_DATA_SIZE) throw new BadRequestException("track-too-large", "Track data max size exceeded (" + entity.getData().length + " > " + MAX_DATA_SIZE + ")");
			return self.createTrackWithQuota(entity, col != null ? col.getOwner() : user)
				.map(e -> toDTO(e, col != null ? owner : null));
		});
	}
	
	public Mono<Track> createTrackAsSuperUser(Track track) {
		return Mono.fromCallable(() -> {
			validate(track);
			TrackEntity entity = new TrackEntity();
			entity.setUuid(UUID.fromString(track.getUuid()));
			entity.setOwner(track.getOwner());
			entity.setCreatedAt(System.currentTimeMillis());
			entity.setUpdatedAt(entity.getCreatedAt());
			entity.setData(TrackStorage.V1V2Bridge.v1DtoToV2(new StoredData(track.getS(), track.getWp())));
			if (entity.getData().length > MAX_DATA_SIZE) throw new BadRequestException("track-too-large", "Track data max size exceeded (" + entity.getData().length + " > " + MAX_DATA_SIZE + ")");
			return entity;
		})
		.flatMap(entity -> self.createTrackWithQuota(entity, track.getOwner()))
		.map(e -> toDTO(e, null));
	}
	
	@Transactional
	public Mono<TrackEntity> createTrackWithQuota(TrackEntity entity, String quotaUser) {
		return repo.findByUuidAndOwner(entity.getUuid(), entity.getOwner())
		.switchIfEmpty(Mono.defer(() ->
			r2dbc.insert(entity)
			.flatMap(e -> quotaService.addTrack(quotaUser, entity.getData().length).thenReturn(e))
		));
	}
	
	private void validate(Track dto) {
		ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
	}
	
	@Transactional
	public Mono<Track> updateTrack(Track track, Authentication auth) {
		validate(track);
		String owner = track.getOwner();
		String user = TrailenceUtils.email(auth);
		Mono<Optional<SharedCollectionEntity>> sharedCollection;
		if (SharedCollectionUtils.isSharedCollectionOwner(owner))
			sharedCollection = sharedCollectionRepo.getSharedCollectionHavingMember(SharedCollectionUtils.getSharedCollectionUuid(owner), user)
				.map(Optional::of).switchIfEmpty(Mono.error(new NotFoundException("shared_collection", owner)));
		else
			sharedCollection = Mono.just(Optional.empty());
		return sharedCollection
		.flatMap(colOpt -> {
			var col = colOpt.orElse(null);
			String trackOwner = col == null ? user : SharedCollectionUtils.SHARED_OWNER_PREFIX + col.getUuid();
			return repo.findByUuidAndOwner(UUID.fromString(track.getUuid()), trackOwner)
			.switchIfEmpty(Mono.error(new TrackNotFound(owner, track.getUuid())))
			.flatMap(entity -> {
				if (track.getVersion() != entity.getVersion()) return Mono.just(entity);
				int previousDataSize = entity.getData().length;
				try {
					var newData = TrackStorage.V1V2Bridge.v1DtoToV2(new StoredData(track.getS(), track.getWp()));
					if (newData.length > MAX_DATA_SIZE) throw new BadRequestException("track-too-large", "Track data max size exceeded (" + newData.length + " > " + MAX_DATA_SIZE + ")");
					if (Arrays.equals(newData, entity.getData())) return Mono.just(entity);
					entity.setData(newData);
				} catch (Exception e) {
					return Mono.error(e);
				}
				String quotaUser = col != null ? col.getOwner() : user;
				return DbUtils.updateByUuidAndOwner(r2dbc, entity).flatMap(nb -> nb == 0 ? Mono.just(entity) :
					quotaService.updateTrackSize(quotaUser, entity.getData().length - previousDataSize)
					.then(repo.findByUuidAndOwner(entity.getUuid(), trackOwner))
				);
			})
			.map(e -> toDTO(e, col != null ? owner : null));
		});
	}
	
	public Mono<Void> bulkDelete(Collection<String> uuids, Authentication auth) {
		var user = TrailenceUtils.email(auth);
		return self.deleteTracksWithQuota(uuids.stream().map(UUID::fromString).collect(Collectors.toSet()), user, user);
	}
	
	public Mono<Void> bulkDelete(String shareId, List<String> uuids, Authentication auth) {
    	String caller = TrailenceUtils.email(auth);
    	UUID sharedCollectionUuidForCaller = SharedCollectionUtils.getSharedCollectionUuid(shareId);
    	return sharedCollectionRepo.getSharedCollectionHavingMember(sharedCollectionUuidForCaller, caller)
    	.flatMap(col -> {
    		var trackOwner = SharedCollectionUtils.SHARED_OWNER_PREFIX + col.getUuid();
    		var quotaOwner = col.getOwner();
    		return self.deleteTracksWithQuota(uuids.stream().map(UUID::fromString).collect(Collectors.toSet()), trackOwner, quotaOwner);
    	}); 
    }
	
	public Mono<Void> deleteTracksWithQuota(Set<UUID> uuids, String owner, String quotaOwner) {
		log.info("Deleting {} tracks for {}", uuids.size(), owner);
		return repo.findAllByUuidInAndOwner(uuids, owner)
		.flatMap(entity -> self.deleteTrackWithQuota(entity.getUuid(), owner, quotaOwner, entity.getData().length), 1, 1)
		.then(Mono.fromRunnable(() -> log.info("Tracks deleted ({} for {})", uuids.size(), owner)));
	}
	
	@Transactional
	public Mono<Void> deleteTrackWithQuota(UUID uuid, String owner, String quotaOwner, int dataSize) {
		return repo.deleteByUuidAndOwner(uuid, owner)
		.flatMap(nb -> nb == 0 ? Mono.empty() : quotaService.tracksDeleted(quotaOwner, 1, dataSize));
	}
	
	@SuppressWarnings("java:S2445") // synchronized on a parameter
	public Mono<UpdateResponse<UuidAndOwner>> getUpdates(List<Versioned> known, Authentication auth) {
		List<UuidAndOwner> newItems = new LinkedList<>();
		List<UuidAndOwner> updatedItems = new LinkedList<>();
		List<Select> selectAccessible = buildSelectAccessibleTracks(auth);
		return Flux.concat(
			selectAccessible.stream()
			.map(select -> r2dbc.query(DbUtils.select(select, null, r2dbc), row -> Tuples.of((UUID) row.get("uuid"), (String) row.get("owner"), (Long) row.get("version"))).all())
			.toList()
		)
		.distinct()
		.doOnNext(version -> {
			Optional<Versioned> knownOpt;
			synchronized (known) {
				knownOpt = known.stream().filter(v -> v.getUuid().equals(version.getT1().toString()) && v.getOwner().toLowerCase().equals(version.getT2())).findAny();
			}
			if (knownOpt.isEmpty()) {
				synchronized (newItems) {
					newItems.add(new UuidAndOwner(version.getT1().toString(), version.getT2()));
				}
			} else {
				Versioned v = knownOpt.get();
				synchronized (known) {
					known.remove(v);
				}
				if (v.getVersion() < version.getT3()) {
					synchronized (updatedItems) {
						updatedItems.add(new UuidAndOwner(version.getT1().toString(), version.getT2()));
					}
				}
			}
		})
		.then(Mono.fromSupplier(() -> {
			UpdateResponse<UuidAndOwner> response = new UpdateResponse<>();
			response.setDeleted(known.stream().map(v -> new UuidAndOwner(v.getUuid(), v.getOwner())).toList());
			response.setCreated(newItems);
			response.setUpdated(updatedItems);
			return response;
		}));
	}
	
	private List<Select> buildSelectAccessibleTracks(Authentication auth) {
		List<Select> selects = new LinkedList<>();
		String email = TrailenceUtils.email(auth);
		
		Select sharedWithMe = shareService.selectSharedElementsWithMe(
			email,
			new Expression[] { TrackEntity.COL_UUID, TrackEntity.COL_OWNER, TrackEntity.COL_VERSION },
			TrackEntity.TABLE,
			Conditions.isEqual(TrailEntity.COL_OWNER, TrackEntity.COL_OWNER)
			.and(
				Conditions.isEqual(TrailEntity.COL_ORIGINAL_TRACK_UUID, TrackEntity.COL_UUID)
				.or(Conditions.isEqual(TrailEntity.COL_CURRENT_TRACK_UUID, TrackEntity.COL_UUID))
			),
			null
		);
		selects.add(sharedWithMe);

    	Select owned = Select.builder()
			.select(TrackEntity.COL_UUID, TrackEntity.COL_OWNER, TrackEntity.COL_VERSION)
			.from(TrackEntity.TABLE)
			.where(Conditions.isEqual(TrackEntity.COL_OWNER, SQL.literalOf(email)))
			.build();
    	selects.add(owned);
    	
    	if (AuthDetails.getVersion(auth) >= SharedCollectionUtils.MIN_VERSION_FOR_SHARED)
    		selects.add(
    			Select.builder()
    			.select(
    				TrackEntity.COL_UUID,
    				SimpleFunction.create("CONCAT", List.of(SQL.literalOf(SharedCollectionUtils.SHARED_OWNER_PREFIX), SharedCollectionMemberEntity.COL_UUID)).as(TrackEntity.COL_OWNER.getName()),
    				TrackEntity.COL_VERSION
    			)
    			.from(SharedCollectionMemberEntity.TABLE)
    			.join(TrackEntity.TABLE)
    				.on(Conditions.isEqual(
    		        	TrackEntity.COL_OWNER,
    		        	SimpleFunction.create("CONCAT", List.of(SQL.literalOf(SharedCollectionUtils.SHARED_OWNER_PREFIX), SharedCollectionMemberEntity.COL_SHARED_COLLECTION_UUID))
    		        ))
    			.where(Conditions.isEqual(SharedCollectionMemberEntity.COL_OWNER, SQL.literalOf(email)))
    			.build()
    		);
    	
    	return selects;
	}
	
	public Mono<Track> getTrack(String uuid, String requestOwner, Authentication auth) {
		String owner = requestOwner.toLowerCase();
		Mono<Track> getFromDB = repo.findByUuidAndOwner(UUID.fromString(uuid), owner)
			.map(e -> toDTO(e, null))
			.switchIfEmpty(Mono.error(new TrackNotFound(owner, uuid)));
		String user = TrailenceUtils.email(auth);
		if (owner.equals(user)) return getFromDB;
		
		if (SharedCollectionUtils.isSharedCollectionOwner(owner))
			return sharedCollectionMemberRepo.findByUuidAndOwner(SharedCollectionUtils.getSharedCollectionUuid(owner), user)
			.flatMap(member -> repo.findByUuidAndOwner(UUID.fromString(uuid), SharedCollectionUtils.SHARED_OWNER_PREFIX + member.getSharedCollectionUuid()))
			.map(e -> toDTO(e, owner))
			.switchIfEmpty(Mono.error(new TrackNotFound(owner, uuid)));
		
		Select sharedWithMe = shareService.selectSharedElementsWithMe(
			user,
			new Expression[] { TrackEntity.COL_UUID },
			TrackEntity.TABLE,
			Conditions.isEqual(TrailEntity.COL_OWNER, TrackEntity.COL_OWNER)
			.and(
				Conditions.isEqual(TrailEntity.COL_ORIGINAL_TRACK_UUID, TrackEntity.COL_UUID)
				.or(Conditions.isEqual(TrailEntity.COL_CURRENT_TRACK_UUID, TrackEntity.COL_UUID))
			),
			Conditions.isEqual(TrackEntity.COL_UUID, SQL.literalOf(uuid))
			.and(Conditions.isEqual(ShareRecipientEntity.COL_OWNER, SQL.literalOf(owner)))
		);
		return r2dbc.query(DbUtils.select(sharedWithMe, null, r2dbc), UUID.class).first().hasElement()
		.flatMap(isSharedWithMe -> {
			if (!isSharedWithMe.booleanValue()) return Mono.error(new TrackNotFound(owner, uuid));
			return getFromDB;
		});
	}
	
	@SuppressWarnings("java:S112") // generic exception
	public Track toDTO(TrackEntity entity, String ownerForCaller) {
		Track dto = new Track();
		dto.setUuid(entity.getUuid().toString());
		dto.setOwner(ownerForCaller != null ? ownerForCaller : entity.getOwner());
		dto.setVersion(entity.getVersion());
		dto.setCreatedAt(entity.getCreatedAt());
		dto.setUpdatedAt(entity.getUpdatedAt());
		try {
			var data = TrackStorage.V1V2Bridge.v2ToV1Dto(entity.getData());
			dto.setS(data.s);
			dto.setWp(data.wp);
			dto.setSizeUsed(entity.getData().length);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return dto;
	}
	
	@SuppressWarnings("java:S110") // more than 5 parents
	public static class TrackNotFound extends NotFoundException {
		private static final long serialVersionUID = 1L;

		public TrackNotFound(String owner, String uuid) {
			super("track", owner + "/" + uuid);
		}
	}
	
}
