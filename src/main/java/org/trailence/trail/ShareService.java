package org.trailence.trail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Delete;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.email.EmailService;
import org.trailence.global.db.DbUtils;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.rest.TokenService;
import org.trailence.global.rest.TokenService.TokenData;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.ShareRepository;
import org.trailence.trail.db.TagRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.dto.CreateShareRequest;
import org.trailence.trail.dto.Share;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.user.db.UserRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class ShareService {
	
	private final ShareRepository shareRepo;
	private final TrailCollectionRepository collectionRepo;
	private final TagRepository tagRepo;
	private final TrailRepository trailRepo;
	private final UserRepository userRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final EmailService emailService;
	private final TokenService tokenService;
	
	public Mono<Share> createShare(CreateShareRequest request, Authentication auth) {
		if (request.getElements().isEmpty()) return Mono.empty();
		
		// check all elements belongs to the caller
		List<UUID> elements = request.getElements().stream().map(UUID::fromString).toList();
		String user = auth.getPrincipal().toString();

		return checkCount(request, elements, user)
		.then(Mono.defer(() -> {
			ShareEntity share = new ShareEntity();
			share.setUuid(UUID.fromString(request.getId()));
			share.setFromEmail(user);
			share.setToEmail(request.getTo().toLowerCase());
			share.setName(request.getName());
			share.setElementType(request.getType());
			share.setCreatedAt(System.currentTimeMillis());
			share.setIncludePhotos(request.isIncludePhotos());
			List<ShareElementEntity> entities = new ArrayList<>(elements.size());
			for (UUID uuid : elements) {
				entities.add(new ShareElementEntity(share.getUuid(), uuid, user));
			}
			return r2dbc.insert(share)
			.thenMany(
				Flux.fromIterable(entities).flatMap(r2dbc::insert, 3, 6)
				.onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
			)
			.then(Mono.defer(() ->
				userRepo.findByEmail(request.getTo().toLowerCase())
				.singleOptional()
				.flatMap(optUser -> {
					if (optUser.isEmpty() || optUser.get().getPassword() == null) {
						String token;
						try {
							token = tokenService.generate(new TokenData("share", share.getToEmail(), share.getUuid().toString() + "/" + user));
						} catch (Exception e) {
							return Mono.empty();
						}
						return emailService.send(share.getToEmail(), "invite_share", request.getToLanguage(), Map.of(
							"from", user,
							"link", emailService.getLinkUrl(token + "?lang=" + request.getToLanguage())
						));
					} else {
						return emailService.send(share.getToEmail(), "new_share", request.getToLanguage(), Map.of(
							"from", user
						));
					}
				})
			))
			.then(Mono.just(toDto(share, elements, null)))
			.onErrorResume(DuplicateKeyException.class, e -> getShare(request.getId(), user));
		}));
	}
	
	private Mono<Void> checkCount(CreateShareRequest request, List<UUID> elements, String user) {
		Mono<Long> count;
		switch (request.getType()) {
		case COLLECTION: count = collectionRepo.findAllByUuidInAndOwner(elements, user).count(); break;
		case TAG: count = tagRepo.findAllByUuidInAndOwner(elements, user).count(); break;
		case TRAIL: count = trailRepo.findAllByUuidInAndOwner(elements, user).count(); break;
		default: return Mono.error(new BadRequestException("Invalid element type"));
		}

		return count.flatMap(nb -> {
			if (nb.longValue() != elements.size()) return Mono.error(new BadRequestException("Some elements are invalid or not found"));
			return Mono.empty();
		});
	}
	
	private Mono<Share> getShare(String id, String owner) {
		return shareRepo.findOneByUuidAndFromEmail(UUID.fromString(id), owner)
		.flatMap(this::myShareWithElements);
	}
	
	public Flux<Share> getShares(Authentication auth) {
		String user = auth.getPrincipal().toString();
		return shareRepo.findAllByFromEmailOrToEmail(user, user)
		.flatMap(share -> {
			if (share.getFromEmail().equals(user)) {
				return myShareWithElements(share);
			}
			return getTrails(share).map(trails -> toDto(share, null, trails));
		});
	}
	
	private Mono<Share> myShareWithElements(ShareEntity share) {
		if (share.getElementType().equals(ShareElementType.TRAIL)) {
			return getElements(share.getUuid(), share.getFromEmail()).map(elements -> toDto(share, elements, elements));
		}
		return Mono.zip(
			getElements(share.getUuid(), share.getFromEmail()).publishOn(Schedulers.parallel()),
			getTrails(share).publishOn(Schedulers.parallel())
		).map(tuple -> toDto(share, tuple.getT1(), tuple.getT2()));
	}
	
	private Mono<List<UUID>> getElements(UUID shareId, String owner) {
		Select select = Select.builder()
			.select(ShareElementEntity.COL_ELEMENT_UUID)
			.from(ShareElementEntity.TABLE)
			.where(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, SQL.literalOf(shareId.toString()))
				.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(owner)))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).all().collectList();
	}
	
	private Mono<List<UUID>> getTrails(ShareEntity share) {
		if (share.getElementType().equals(ShareElementType.TRAIL)) return getElements(share.getUuid(), share.getFromEmail());
		if (share.getElementType().equals(ShareElementType.TAG)) return getTrailsFromTags(share);
		return getTrailsFromCollections(share);
	}
	
	private Mono<List<UUID>> getTrailsFromTags(ShareEntity share) {
		Select select = Select.builder()
			.select(TrailEntity.COL_UUID)
			.from(ShareElementEntity.TABLE)
			.join(TrailTagEntity.TABLE).on(Conditions.isEqual(TrailTagEntity.COL_TAG_UUID, ShareElementEntity.COL_ELEMENT_UUID))
			.join(TrailEntity.TABLE).on(Conditions.isEqual(TrailEntity.COL_UUID, TrailTagEntity.COL_TRAIL_UUID))
			.where(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, SQL.literalOf(share.getUuid().toString()))
				.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(share.getFromEmail())))
				.and(Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(share.getFromEmail())))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).all().collectList();
	}

	private Mono<List<UUID>> getTrailsFromCollections(ShareEntity share) {
		Select select = Select.builder()
			.select(TrailEntity.COL_UUID)
			.from(ShareElementEntity.TABLE)
			.join(TrailEntity.TABLE).on(Conditions.isEqual(TrailEntity.COL_COLLECTION_UUID, ShareElementEntity.COL_ELEMENT_UUID))
			.where(
				Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, SQL.literalOf(share.getUuid().toString()))
				.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(share.getFromEmail())))
				.and(Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(share.getFromEmail())))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).all().collectList();
	}
	
	
	public Mono<Void> deleteShare(String id, String from, Authentication auth) {
		String user = auth.getPrincipal().toString();
		return shareRepo.findOneByUuidAndFromEmail(UUID.fromString(id), from)
		.flatMap(entity -> {
			if (!entity.getFromEmail().equals(user) && !entity.getToEmail().equals(user)) return Mono.empty();
			Delete deleteElements = Delete.builder().from(ShareElementEntity.TABLE).where(Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, SQL.literalOf(id)).and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(from)))).build();
			return r2dbc.getDatabaseClient().sql(DbUtils.delete(deleteElements, null, r2dbc)).then()
			.then(shareRepo.deleteAllByUuidInAndFromEmail(List.of(UUID.fromString(id)), from));
		});
	}
	
	public Mono<Void> trailsDeleted(Collection<UUID> trailsUuids, String owner) {
		return deleteShares(ShareElementType.TRAIL, trailsUuids, owner);
	}
	
	public Mono<Void> tagsDeleted(Collection<UUID> tagsUuids, String owner) {
		return deleteShares(ShareElementType.TAG, tagsUuids, owner);
	}
	
	public Mono<Void> collectionsDeleted(Collection<UUID> collectionsUuids, String owner) {
		return deleteShares(ShareElementType.COLLECTION, collectionsUuids, owner);
	}
	
	private Mono<Void> deleteShares(ShareElementType type, Collection<UUID> elements, String owner) {
		Select selectSharesIds = Select.builder()
			.select(ShareEntity.COL_UUID)
			.from(ShareElementEntity.TABLE)
			.join(ShareEntity.TABLE).on(
				Conditions.isEqual(ShareEntity.COL_UUID, ShareElementEntity.COL_SHARE_UUID)
				.and(Conditions.isEqual(ShareEntity.COL_FROM_EMAIL, SQL.literalOf(owner)))
				.and(Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(type.name())))
			)
			.where(
				Conditions.in(ShareElementEntity.COL_ELEMENT_UUID, elements.stream().map(uuid -> SQL.literalOf(uuid.toString())).toList())
			)
			.build();
		return r2dbc.query(DbUtils.select(selectSharesIds, null, r2dbc), UUID.class).all().collectList()
		.flatMap(sharesIds -> {
			if (sharesIds.isEmpty()) return Mono.empty();
			Delete deleteElements = Delete.builder()
				.from(ShareElementEntity.TABLE)
				.where(
					Conditions.in(ShareElementEntity.COL_ELEMENT_UUID, elements.stream().map(uuid -> SQL.literalOf(uuid.toString())).toList())
					.and(Conditions.in(ShareElementEntity.COL_SHARE_UUID, sharesIds.stream().map(uuid -> SQL.literalOf(uuid.toString())).toList()))
					.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(owner)))
				)
				.build();
			return r2dbc.getDatabaseClient().sql(DbUtils.delete(deleteElements, null, r2dbc)).then()
			.then(Mono.defer(() -> {
				Select selectNonEmptySharesIds = Select.builder()
					.select(SimpleFunction.create("DISTINCT", List.of(ShareElementEntity.COL_SHARE_UUID)))
					.from(ShareElementEntity.TABLE)
					.where(
						Conditions.in(ShareElementEntity.COL_SHARE_UUID, sharesIds.stream().map(uuid -> SQL.literalOf(uuid.toString())).toList())
						.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(owner)))
					)
					.build();
				return r2dbc.query(DbUtils.select(selectNonEmptySharesIds, null, r2dbc), UUID.class).all().collectList()
				.flatMap(existingUuids -> {
					List<UUID> toDelete = sharesIds.stream().filter(uuid -> !existingUuids.contains(uuid)).toList();
					if (toDelete.isEmpty()) return Mono.empty();
					return shareRepo.deleteAllByUuidInAndFromEmail(toDelete, owner);
				});
			}));
		});
	}
	
	private Share toDto(ShareEntity entity, List<UUID> elements, List<UUID> trails) {
		return new Share(
			entity.getUuid().toString(),
			entity.getName(),
			entity.getFromEmail(),
			entity.getToEmail(),
			entity.getElementType(),
			entity.getCreatedAt(),
			elements != null ? elements.stream().map(UUID::toString).toList() : null,
			trails != null ? trails.stream().map(UUID::toString).toList() : null,
			entity.isIncludePhotos()
		);
	}
	
}
