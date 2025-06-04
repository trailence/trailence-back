package org.trailence.trail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.AccessibleByteArrayOutputStream;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.UuidAndOwner;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.db.ShareRecipientEntity;
import org.trailence.trail.db.TrackEntity;
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Track.Segment;
import org.trailence.trail.dto.Track.WayPoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private TrackService self;
	
	private static final long MAX_DATA_SIZE = 512L * 1024;
	
	public Mono<Track> createTrack(Track track, Authentication auth) {
		return Mono.fromCallable(() -> {
			validate(track);
			TrackEntity entity = new TrackEntity();
			entity.setUuid(UUID.fromString(track.getUuid()));
			entity.setOwner(auth.getPrincipal().toString());
			entity.setCreatedAt(System.currentTimeMillis());
			entity.setUpdatedAt(entity.getCreatedAt());
			StoredData data = new StoredData();
			data.s = track.getS();
			data.wp = track.getWp();
			entity.setData(compress(data));
			if (entity.getData().length > MAX_DATA_SIZE) throw new BadRequestException("track-too-large", "Track data max size exceeded (" + entity.getData().length + " > " + MAX_DATA_SIZE + ")");
			return entity;
		})
		.flatMap(self::createTrackWithQuota)
		.map(this::toDTO);
	}
	
	@Transactional
	public Mono<TrackEntity> createTrackWithQuota(TrackEntity entity) {
		return repo.findByUuidAndOwner(entity.getUuid(), entity.getOwner())
		.switchIfEmpty(Mono.defer(() ->
			r2dbc.insert(entity)
			.flatMap(e -> quotaService.addTrack(entity.getOwner(), entity.getData().length).thenReturn(e))
		));
	}
	
	private void validate(Track dto) {
		ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
	}
	
	@Transactional
	public Mono<Track> updateTrack(Track track, Authentication auth) {
		validate(track);
		return repo.findByUuidAndOwner(UUID.fromString(track.getUuid()), auth.getPrincipal().toString())
		.switchIfEmpty(Mono.error(new TrackNotFound(auth.getPrincipal().toString(), track.getUuid())))
		.flatMap(entity -> {
			if (track.getVersion() != entity.getVersion()) return Mono.just(entity);
			int previousDataSize = entity.getData().length;
			try {
				StoredData data = new StoredData();
				data.s = track.getS();
				data.wp = track.getWp();
				var newData = compress(data);
				if (newData.length > MAX_DATA_SIZE) throw new BadRequestException("track-too-large", "Track data max size exceeded (" + newData.length + " > " + MAX_DATA_SIZE + ")");
				if (Arrays.equals(newData, entity.getData())) return Mono.just(entity);
				entity.setData(newData);
			} catch (Exception e) {
				return Mono.error(e);
			}
			return DbUtils.updateByUuidAndOwner(r2dbc, entity).flatMap(nb -> nb == 0 ? Mono.just(entity) :
				quotaService.updateTrackSize(entity.getOwner(), entity.getData().length - previousDataSize)
				.then(repo.findByUuidAndOwner(entity.getUuid(), entity.getOwner()))
			);
		})
		.map(this::toDTO);
	}
	
	public Mono<Void> bulkDelete(Collection<String> uuids, Authentication auth) {
		return self.deleteTracksWithQuota(uuids.stream().map(UUID::fromString).collect(Collectors.toSet()), auth.getPrincipal().toString());
	}
	
	public Mono<Void> deleteTracksWithQuota(Set<UUID> uuids, String owner) {
		log.info("Deleting {} tracks for {}", uuids.size(), owner);
		return repo.findAllByUuidInAndOwner(uuids, owner)
		.flatMap(entity -> self.deleteTrackWithQuota(entity.getUuid(), owner, entity.getData().length), 1, 1)
		.then(Mono.fromRunnable(() -> log.info("Tracks deleted ({} for {})", uuids.size(), owner)));
	}
	
	@Transactional
	public Mono<Void> deleteTrackWithQuota(UUID uuid, String owner, int dataSize) {
		return repo.deleteByUuidAndOwner(uuid, owner)
		.flatMap(nb -> nb == 0 ? Mono.empty() : quotaService.tracksDeleted(owner, 1, dataSize));
	}
	
	@SuppressWarnings("java:S2445") // synchronized on a parameter
	public Mono<UpdateResponse<UuidAndOwner>> getUpdates(List<Versioned> known, Authentication auth) {
		List<UuidAndOwner> newItems = new LinkedList<>();
		List<UuidAndOwner> updatedItems = new LinkedList<>();
		List<Select> selectAccessible = buildSelectAccessibleTracks(auth.getPrincipal().toString());
		return Flux.concat(selectAccessible.stream().map(select -> r2dbc.query(DbUtils.select(select, null, r2dbc), row -> Tuples.of((UUID) row.get("uuid"), (String) row.get("owner"), (Long) row.get("version"))).all()).toList())
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
	
	private List<Select> buildSelectAccessibleTracks(String email) {
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

    	Select owned = Select.builder()
			.select(TrackEntity.COL_UUID, TrackEntity.COL_OWNER, TrackEntity.COL_VERSION)
			.from(TrackEntity.TABLE)
			.where(Conditions.isEqual(TrackEntity.COL_OWNER, SQL.literalOf(email)))
			.build();
    	
    	return List.of(owned, sharedWithMe);
	}
	
	public Mono<Track> getTrack(String uuid, String owner, Authentication auth) {
		String email = owner.toLowerCase();
		Mono<Track> getFromDB = repo.findByUuidAndOwner(UUID.fromString(uuid), email)
			.map(this::toDTO)
			.switchIfEmpty(Mono.error(new TrackNotFound(email, uuid)));
		String user = auth.getPrincipal().toString();
		if (email.equals(user)) return getFromDB;
		
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
			if (!isSharedWithMe.booleanValue()) return Mono.error(new TrackNotFound(email, uuid));
			return getFromDB;
		});
	}
	
	@SuppressWarnings("java:S112") // generic exception
	private Track toDTO(TrackEntity entity) {
		Track dto = new Track();
		dto.setUuid(entity.getUuid().toString());
		dto.setOwner(entity.getOwner());
		dto.setVersion(entity.getVersion());
		dto.setCreatedAt(entity.getCreatedAt());
		dto.setUpdatedAt(entity.getUpdatedAt());
		try {
			StoredData data = uncompress(entity.getData(), new TypeReference<StoredData>() {});
			dto.setS(data.s);
			dto.setWp(data.wp);
			dto.setSizeUsed(entity.getData().length);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return dto;
	}
	
	private byte[] compress(Object value) throws IOException {
		try (AccessibleByteArrayOutputStream bos = new AccessibleByteArrayOutputStream(8192);
			GZIPOutputStream gos = new GZIPOutputStream(bos)) {
			new ObjectMapper().writeValue(gos, value);
			byte[] result = bos.getData();
			int len = bos.getLength();
			if (result.length == len) return result;
			byte[] compressed = new byte[len];
			System.arraycopy(result, 0, compressed, 0, len);
			return compressed;
		}
	}
	
	private <T> T uncompress(byte[] compressed, TypeReference<T> type) throws IOException {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
			GZIPInputStream gis = new GZIPInputStream(bis)) {
			return new ObjectMapper().readValue(gis, type);
		}
	}
	
	@SuppressWarnings("java:S1104") // only used for serialization
	static class StoredData {
		public Segment[] s;
		public WayPoint[] wp;
	}
	
	@SuppressWarnings("java:S110") // more than 5 parents
	public static class TrackNotFound extends NotFoundException {
		private static final long serialVersionUID = 1L;

		public TrackNotFound(String owner, String uuid) {
			super("track", owner + "/" + uuid);
		}
	}
	
}
