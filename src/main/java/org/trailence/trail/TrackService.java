package org.trailence.trail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.global.AccessibleByteArrayOutputStream;
import org.trailence.global.db.DbUtils;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.UuidAndOwner;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.TrackEntity;
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Track.Segment;
import org.trailence.trail.dto.Track.WayPoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class TrackService {

	private final TrackRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	
	private static final long MAX_DATA_SIZE = 512L * 1024;
	
	public Mono<Track> createTrack(Track track, Authentication auth) {
		return repo.findByUuidAndOwner(UUID.fromString(track.getUuid()), auth.getPrincipal().toString())
		.map(this::toDTO)
		.switchIfEmpty(
			Mono.fromCallable(() -> {
				TrackEntity entity = new TrackEntity();
				entity.setUuid(UUID.fromString(track.getUuid()));
				entity.setOwner(auth.getPrincipal().toString());
				entity.setCreatedAt(System.currentTimeMillis());
				entity.setUpdatedAt(entity.getCreatedAt());
				StoredData data = new StoredData();
				data.s = track.getS();
				data.wp = track.getWp();
				entity.setData(compress(data));
				if (entity.getData().length > MAX_DATA_SIZE) throw new BadRequestException("Track data max size exceeded");
				return entity;
			})
			.flatMap(r2dbc::insert)
			.map(entity -> {
				track.setVersion(entity.getVersion());
				track.setOwner(entity.getOwner());
				return track;
			})
		);
	}
	
	public Mono<Track> updateTrack(Track track, Authentication auth) {
		return repo.findByUuidAndOwner(UUID.fromString(track.getUuid()), auth.getPrincipal().toString())
		.switchIfEmpty(Mono.error(new NotFoundException("track", track.getUuid())))
		.flatMap(entity -> {
			try {
				StoredData data = new StoredData();
				data.s = track.getS();
				data.wp = track.getWp();
				entity.setData(compress(data));
				if (entity.getData().length > MAX_DATA_SIZE) throw new BadRequestException("Track data max size exceeded");
			} catch (Exception e) {
				return Mono.error(e);
			}
			return DbUtils.updateByUuidAndOwner(r2dbc, entity)
					.flatMap(nb -> nb == 0 ? Mono.just(entity) : repo.findByUuidAndOwner(entity.getUuid(), entity.getOwner()));
		})
		.map(this::toDTO);
	}
	
	public Mono<Void> bulkDelete(Collection<String> uuids, Authentication auth) {
		return repo.deleteAllByUuidInAndOwner(uuids.stream().map(UUID::fromString).toList(), auth.getPrincipal().toString());
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
				knownOpt = known.stream().filter(v -> v.getUuid().equals(version.getT1().toString()) && v.getOwner().equals(version.getT2())).findAny();
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
		Condition trailToTrackCondition = 
			Conditions.isEqual(TrailEntity.COL_ORIGINAL_TRACK_UUID, TrackEntity.COL_UUID)
			.or(Conditions.isEqual(TrailEntity.COL_CURRENT_TRACK_UUID, TrackEntity.COL_UUID))
			.and(Conditions.isEqual(TrailEntity.COL_OWNER, TrackEntity.COL_OWNER));

        Select sharedCollections = Select.builder()
        	.select(TrackEntity.COL_UUID, TrackEntity.COL_OWNER, TrackEntity.COL_VERSION)
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailEntity.TABLE).on(Conditions.isEqual(TrailEntity.COL_COLLECTION_UUID, ShareElementEntity.COL_ELEMENT_UUID).and(Conditions.isEqual(TrailEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrackEntity.TABLE).on(trailToTrackCondition)
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.COLLECTION.name())))
    		)
    		.build();
    	
    	Select sharedTags = Select.builder()
    		.select(TrackEntity.COL_UUID, TrackEntity.COL_OWNER, TrackEntity.COL_VERSION)
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailTagEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TAG_UUID, ShareElementEntity.COL_ELEMENT_UUID).and(Conditions.isEqual(TrailTagEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(TrailEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrackEntity.TABLE).on(trailToTrackCondition)
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TAG.name())))
    		)
    		.build();
    	
    	Select sharedTrails = Select.builder()
    		.select(TrackEntity.COL_UUID, TrackEntity.COL_OWNER, TrackEntity.COL_VERSION)
    		.from(ShareEntity.TABLE)
    		.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrailEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(TrailEntity.COL_OWNER, ShareEntity.COL_FROM_EMAIL)))
    		.join(TrackEntity.TABLE).on(trailToTrackCondition)
    		.where(
    			Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(email))
    			.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TRAIL.name())))
    		)
    		.build();

    	Select owned = Select.builder()
			.select(TrackEntity.COL_UUID, TrackEntity.COL_OWNER, TrackEntity.COL_VERSION)
			.from(TrackEntity.TABLE)
			.where(Conditions.isEqual(TrackEntity.COL_OWNER, SQL.literalOf(email)))
			.build();
    	
    	return List.of(owned, sharedCollections, sharedTags, sharedTrails);
	}
	
	private Mono<Boolean> isFromSharedTrail(String trackUuid, String trackOwner, String user) {
		var select = Select.builder()
			.select(TrailEntity.COL_UUID)
			.from(TrailEntity.TABLE)
			.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailEntity.COL_UUID).and(Conditions.isEqual(TrailEntity.COL_OWNER, ShareElementEntity.COL_OWNER)))
			.join(ShareEntity.TABLE).on(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID)
				.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TRAIL.name())))
				.and(Conditions.isEqual(ShareEntity.COL_FROM_EMAIL, SQL.literalOf(trackOwner)))
				.and(Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(trackOwner))
				.and(Conditions.isEqual(TrailEntity.COL_ORIGINAL_TRACK_UUID, SQL.literalOf(trackUuid)).or(Conditions.isEqual(TrailEntity.COL_CURRENT_TRACK_UUID, SQL.literalOf(trackUuid))))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}
	
	private Mono<Boolean> isFromSharedTag(String trackUuid, String trackOwner, String user) {
		var select = Select.builder()
			.select(TrailEntity.COL_UUID)
			.from(TrailEntity.TABLE)
			.join(TrailTagEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID))
			.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailTagEntity.COL_TAG_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, TrailEntity.COL_OWNER)))
			.join(ShareEntity.TABLE).on(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID)
				.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TAG.name())))
				.and(Conditions.isEqual(ShareEntity.COL_FROM_EMAIL, SQL.literalOf(trackOwner)))
				.and(Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(trackOwner))
				.and(Conditions.isEqual(TrailEntity.COL_ORIGINAL_TRACK_UUID, SQL.literalOf(trackUuid)).or(Conditions.isEqual(TrailEntity.COL_CURRENT_TRACK_UUID, SQL.literalOf(trackUuid))))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}
	
	private Mono<Boolean> isFromSharedCollection(String trackUuid, String trackOwner, String user) {
		var select = Select.builder()
			.select(TrailEntity.COL_UUID)
			.from(TrailEntity.TABLE)
			.join(ShareElementEntity.TABLE).on(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailEntity.COL_COLLECTION_UUID).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(trackOwner))))
			.join(ShareEntity.TABLE).on(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID)
				.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.COLLECTION.name())))
				.and(Conditions.isEqual(ShareEntity.COL_FROM_EMAIL, SQL.literalOf(trackOwner)))
				.and(Conditions.isEqual(ShareEntity.COL_TO_EMAIL, SQL.literalOf(user)))
			)
			.limit(1)
			.where(
				Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(trackOwner))
				.and(Conditions.isEqual(TrailEntity.COL_ORIGINAL_TRACK_UUID, SQL.literalOf(trackUuid)).or(Conditions.isEqual(TrailEntity.COL_CURRENT_TRACK_UUID, SQL.literalOf(trackUuid))))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).first().hasElement();
	}

	public Mono<Track> getTrack(String uuid, String owner, Authentication auth) {
		Mono<Track> getFromDB = repo.findByUuidAndOwner(UUID.fromString(uuid), owner).map(this::toDTO);
		if (owner.equals(auth.getPrincipal().toString())) return getFromDB;
		String user = auth.getPrincipal().toString();
		return isFromSharedTrail(uuid, owner, user)
		.flatMap(sharedTrail -> {
			if (sharedTrail.booleanValue()) return getFromDB;
			return isFromSharedTag(uuid, owner, user)
			.flatMap(sharedTag -> {
				if (sharedTag.booleanValue()) return getFromDB;
				return isFromSharedCollection(uuid, owner, user)
				.flatMap(sharedCollection -> {
					if (sharedCollection.booleanValue()) return getFromDB;
					return Mono.error(new NotFoundException("track", uuid));
				});
			});
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
	
}
