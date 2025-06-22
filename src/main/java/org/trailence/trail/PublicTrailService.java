package org.trailence.trail;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.geo.Box;
import org.springframework.data.geo.Point;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expressions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.storage.FileService;
import org.trailence.trail.TrackService.StoredData;
import org.trailence.trail.db.PublicPhotoEntity;
import org.trailence.trail.db.PublicPhotoRepository;
import org.trailence.trail.db.PublicTrackEntity;
import org.trailence.trail.db.PublicTrackRepository;
import org.trailence.trail.db.PublicTrailEntity;
import org.trailence.trail.db.PublicTrailRepository;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.dto.CreatePublicTrailRequest;
import org.trailence.trail.dto.PublicTrack;
import org.trailence.trail.dto.PublicTrail;
import org.trailence.trail.dto.PublicTrailSearch;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByBoundsResponse;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileRequest;
import org.trailence.trail.dto.PublicTrailSearch.SearchByTileResponse;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PublicTrailService {
	
	private final PublicTrailRepository publicTrailRepo;
	private final PublicPhotoRepository publicPhotoRepo;
	private final PublicTrackRepository publicTrackRepo;
	private final TrailRepository trailRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final PhotoService photoService;
	private final TrailService trailService;
	private final FileService fileService;
	
	@Transactional
	public Mono<String> create(CreatePublicTrailRequest request, Authentication auth) {
		UUID uuid = UUID.randomUUID();
		String author = request.getAuthor().toLowerCase();
		if (author.equals(auth.getPrincipal().toString())) return Mono.error(new ForbiddenException());
		return trailRepo.findTrailToReview(UUID.fromString(request.getTrailUuid()), author)
		.switchIfEmpty(Mono.error(new NotFoundException("trail", request.getTrailUuid() + "-" + author)))
		.flatMap(fromTrail ->
			r2dbc.insert(toTrackEntity(uuid, request))
			.then(Flux.fromIterable(request.getPhotos()).flatMap(p -> photoService.transferToPublic(UUID.fromString(p.getUuid()), author, uuid, p), 1, 1).then())
			.then(generateSlug(request.getName()).flatMap(slug -> r2dbc.insert(toTrailEntity(uuid, slug, request))))
			.then(trailService.delete(Flux.just(fromTrail), author))
		).thenReturn(uuid.toString());
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
	
	private static PublicTrailEntity toTrailEntity(UUID uuid, String slug, CreatePublicTrailRequest request) {
		long now = System.currentTimeMillis();
		return new PublicTrailEntity(
			uuid,
			request.getAuthor().toLowerCase(),
			now,
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
			request.getSimplifiedPath().stream().map(v -> Double.valueOf(Math.floor(v * 1000000)).intValue()).toArray(size -> new Integer[size])
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
		String sql = new SqlBuilder()
		.select(
			Expressions.just("count(*) as nb_trails"),
			zoomColumn.as("tile")
		)
		.from(PublicTrailEntity.TABLE)
		.where(Conditions.in(zoomColumn, request.getTiles().stream().map(SQL::literalOf).toList()))
		.groupBy(zoomColumn)
		.build();

		return r2dbc.query(DbUtils.operation(sql, null), row -> new PublicTrailSearch.NbTrailsByTile(row.get("tile", Integer.class), row.get("nb_trails", Long.class)))
			.all().collectList().map(SearchByTileResponse::new);
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
	
	public Mono<PublicTrail> getById(String uuid) {
		return publicTrailRepo.findById(UUID.fromString(uuid))
		.flatMap(trail ->
			publicPhotoRepo.findAllByTrailUuid(trail.getUuid()).collectList()
			.map(photos -> this.toPublicTrailDto(trail, photos.stream()))
		);
	}
	
	public Mono<PublicTrail> getBySlug(String slug) {
		return publicTrailRepo.findOneBySlug(slug)
		.flatMap(trail ->
			publicPhotoRepo.findAllByTrailUuid(trail.getUuid()).collectList()
			.map(photos -> this.toPublicTrailDto(trail, photos.stream()))
		);
	}
	
	public Mono<List<PublicTrail>> getByIds(List<String> uuids) {
		if (uuids.size() > 200) return Mono.error(new BadRequestException("too-many-trails", "Maximum 200 trails"));
		return publicTrailRepo.findAllById(uuids.stream().map(UUID::fromString).toList())
		.collectList()
		.flatMap(trails ->
			publicPhotoRepo.findAllByTrailUuidIn(trails.stream().map(PublicTrailEntity::getUuid).toList())
			.collectList()
			.map(photos ->
				trails.stream().map(trail -> this.toPublicTrailDto(trail, photos.stream().filter(p -> p.getTrailUuid().equals(trail.getUuid())))).toList()
			)
		);
	}
	
	private PublicTrail toPublicTrailDto(PublicTrailEntity entity, Stream<PublicPhotoEntity> photos) {
		return new PublicTrail(
			entity.getUuid().toString(),
			entity.getSlug(),
			entity.getCreatedAt(),
			entity.getUpdatedAt(),
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
			Arrays.stream(entity.getSimplifiedPath()).map(i -> i.doubleValue() / 1000000).toList(),
			photos.map(pe -> new PublicTrail.Photo(
				pe.getUuid().toString(),
				pe.getDescription(),
				pe.getDate(),
				pe.getLatitude(),
				pe.getLongitude(),
				pe.getIndex()
			)).toList()
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
	
}
