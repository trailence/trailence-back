package org.trailence.trail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.ConflictException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.storage.FileService;
import org.trailence.trail.TrackService.StoredData;
import org.trailence.trail.db.PhotoEntity;
import org.trailence.trail.db.PhotoRepository;
import org.trailence.trail.db.TrackEntity;
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailLinkEntity;
import org.trailence.trail.db.TrailLinkRepository;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.dto.MyTrailLink;
import org.trailence.trail.dto.TrailLinkContent;
import org.trailence.trail.exceptions.TrailLinkNotFound;
import org.trailence.trail.exceptions.TrailNotFound;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;
import tools.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
public class TrailLinkService {
	
	private final R2dbcEntityTemplate r2dbc;
	private final TrailLinkRepository linkRepo;
	private final TrailRepository trailRepo;
	private final TrackRepository trackRepo;
	private final PhotoRepository photoRepo;
	private final FileService fileService;

	public Mono<List<MyTrailLink>> getMyLinks(Authentication auth) {
		String email = TrailenceUtils.email(auth);
		return linkRepo.findAllByAuthor(email).map(this::toMyTrailLink).collectList();
	}
	
	public Mono<MyTrailLink> createLink(String trailUuid, Authentication auth) {
		UUID trailId = UUID.fromString(trailUuid);
		String email = TrailenceUtils.email(auth);
		return linkRepo.findOneByAuthorAndAuthorUuid(email, trailId)
			.flatMap(_ -> Mono.error(() -> new ConflictException("trail-link-exists", "This trail already has a link")))
			.then(trailRepo.findByUuidAndOwner(trailId, email))
			.switchIfEmpty(Mono.error(() -> new TrailNotFound(trailUuid, email)))
			.then(Mono.defer(() -> {
				TrailLinkEntity entity = new TrailLinkEntity(
					UUID.randomUUID(),
					UUID.randomUUID(),
					UUID.randomUUID(),
					email,
					trailId,
					System.currentTimeMillis()
				);
				return r2dbc.insert(entity);
			}))
			.map(this::toMyTrailLink);
	}
	
	public Mono<Void> deleteLink(String trailUuid, Authentication auth) {
		UUID trailId = UUID.fromString(trailUuid);
		String email = TrailenceUtils.email(auth);
		return linkRepo.deleteAllByAuthorUuidInAndAuthor(List.of(trailId), email);
	}
	
	public Mono<TrailLinkContent> getTrailByLink(String link) {
		var ids = decodeLink(link);
		return linkRepo.findById(ids.getT1())
		.filter(entity -> entity.getLinkKey1().equals(ids.getT2()) && entity.getLinkKey2().equals(ids.getT3()))
		.switchIfEmpty(Mono.error(() -> new TrailLinkNotFound(link)))
		.flatMap(entity ->
			trailRepo.findByUuidAndOwner(entity.getAuthorUuid(), entity.getAuthor())
			.switchIfEmpty(Mono.error(() -> new TrailLinkNotFound(link)))
			.zipWhen(trail ->
				trackRepo.findByUuidAndOwner(trail.getCurrentTrackUuid(), trail.getOwner())
				.switchIfEmpty(Mono.error(() -> new TrailLinkNotFound(link)))
			)
			.zipWhen(_ ->
				photoRepo.findAllByTrailUuidInAndOwner(List.of(entity.getAuthorUuid()), entity.getAuthor()).collectList()
			)
		)
		.map(tuples -> toTrailLinkContent(link, tuples.getT1().getT1(), tuples.getT1().getT2(), tuples.getT2()));
	}
	
	public Mono<Flux<DataBuffer>> getPhoto(String link, String photoUuid) {
		var ids = decodeLink(link);
		return linkRepo.findById(ids.getT1())
		.filter(entity -> entity.getLinkKey1().equals(ids.getT2()) && entity.getLinkKey2().equals(ids.getT3()))
		.switchIfEmpty(Mono.error(() -> new TrailLinkNotFound(link)))
		.flatMap(linkEntity -> 
			photoRepo.findByUuidAndOwner(UUID.fromString(photoUuid), linkEntity.getAuthor())
			.filter(photoEntity -> photoEntity.getTrailUuid().equals(linkEntity.getAuthorUuid()))
			.switchIfEmpty(Mono.error(() -> new NotFoundException("photo", photoUuid)))
		)
		.flatMap(photoEntity -> Mono.just(fileService.getFileContent(photoEntity.getFileId())));
	}
	
	Mono<Void> trailsDeleted(Set<UUID> uuids, String owner) {
		return linkRepo.deleteAllByAuthorUuidInAndAuthor(uuids, owner);
	}
	
	private MyTrailLink toMyTrailLink(TrailLinkEntity entity) {
		return new MyTrailLink(
			toLink(entity),
			entity.getAuthorUuid().toString(),
			entity.getCreatedAt()
		);
	}
	
	private String toLink(TrailLinkEntity entity) {
		byte[] bytes = new byte[3 * 16];
		ByteBuffer b = ByteBuffer.wrap(bytes);
		b.putLong(entity.getUuid().getMostSignificantBits());
		b.putLong(entity.getUuid().getLeastSignificantBits());
		b.putLong(entity.getLinkKey1().getMostSignificantBits());
		b.putLong(entity.getLinkKey1().getLeastSignificantBits());
		b.putLong(entity.getLinkKey2().getMostSignificantBits());
		b.putLong(entity.getLinkKey2().getLeastSignificantBits());
		return Base64.getUrlEncoder().encodeToString(bytes);
	}
	
	private Tuple3<UUID, UUID, UUID> decodeLink(String link) {
		byte[] bytes;
		try { bytes = Base64.getUrlDecoder().decode(link); }
		catch (IllegalArgumentException _) { throw new TrailLinkNotFound(link); }
		if (bytes.length != 3 * 16) throw new TrailLinkNotFound(link);
		ByteBuffer b = ByteBuffer.wrap(bytes);
		UUID uuid1 = new UUID(b.getLong(), b.getLong());
		UUID uuid2 = new UUID(b.getLong(), b.getLong());
		UUID uuid3 = new UUID(b.getLong(), b.getLong());
		return Tuples.of(uuid1, uuid2, uuid3);
	}
	
	private TrailLinkContent toTrailLinkContent(String link, TrailEntity trail, TrackEntity track, List<PhotoEntity> photos) {
		StoredData trackData;
		try {
			trackData = TrackService.uncompress(track.getData(), new TypeReference<StoredData>() {});
		} catch (IOException _) {
			throw new  NotFoundException("trail-link", link);
		}
		return new TrailLinkContent(
			new TrailLinkContent.TrailLinkTrail(
				trail.getCreatedAt(),
				trail.getUpdatedAt(),
				trail.getName(),
				trail.getDescription(),
				trail.getLocation(),
				trail.getDate(),
				trail.getLoopType(),
				trail.getActivity()
			),
			new TrailLinkContent.TrailLinkTrack(trackData.s, trackData.wp),
			photos.stream()
				.map(photoEntity -> new TrailLinkContent.TrailLinkPhoto(
					photoEntity.getUuid().toString(),
					photoEntity.getCreatedAt(),
					photoEntity.getUpdatedAt(),
					photoEntity.getDescription(),
					photoEntity.getDateTaken(),
					photoEntity.getLatitude(),
					photoEntity.getLongitude(),
					photoEntity.isCover(),
					photoEntity.getIndex()
				))
				.toList()
		);
	}
	
}
