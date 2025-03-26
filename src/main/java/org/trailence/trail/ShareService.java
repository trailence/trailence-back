package org.trailence.trail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Delete;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Expressions;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoinCondition;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.StringLiteral;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.email.EmailService;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.exceptions.BadRequestException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.rest.TokenService;
import org.trailence.global.rest.TokenService.TokenData;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.ShareRecipientEntity;
import org.trailence.trail.db.ShareRecipientRepository;
import org.trailence.trail.db.ShareRepository;
import org.trailence.trail.db.TagRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.db.TrailTagEntity;
import org.trailence.trail.dto.CreateShareRequest;
import org.trailence.trail.dto.Share;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.UpdateShareRequest;
import org.trailence.user.db.UserEntity;
import org.trailence.user.db.UserRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class ShareService {
	
	private final ShareRepository shareRepo;
	private final ShareRecipientRepository shareRecipientRepo;
	private final TrailCollectionRepository collectionRepo;
	private final TagRepository tagRepo;
	private final TrailRepository trailRepo;
	private final UserRepository userRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final EmailService emailService;
	private final TokenService tokenService;
	private final QuotaService quotaService;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private ShareService self;
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_COMPLETE)
	public Mono<Share> createShare(CreateShareRequest request, Authentication auth) {
		// check all elements belongs to the caller
		List<UUID> elements = request.getElements().stream().map(UUID::fromString).toList();
		String user = auth.getPrincipal().toString();

		return checkCount(request, elements, user)
		.then(Mono.defer(() -> {
			ShareEntity share = new ShareEntity();
			share.setUuid(UUID.fromString(request.getId()));
			share.setOwner(user);
			share.setCreatedAt(System.currentTimeMillis());
			share.setUpdatedAt(share.getCreatedAt());
			share.setElementType(request.getType());
			share.setName(request.getName());
			share.setIncludePhotos(request.isIncludePhotos());
			List<ShareRecipientEntity> shareRecipients = new ArrayList<>(request.getRecipients().size());
			Set<String> recipients = new HashSet<>();
			for (String to : request.getRecipients()) {
				String recipient = to.toLowerCase();
				if (recipients.add(recipient))
					shareRecipients.add(new ShareRecipientEntity(share.getUuid(), share.getOwner(), recipient));
			}
			List<ShareElementEntity> shareElements = new ArrayList<>(elements.size());
			for (UUID uuid : elements) {
				shareElements.add(new ShareElementEntity(share.getUuid(), uuid, user));
			}
			return self.createShareWithQuota(share, shareElements, shareRecipients)
			.then(Mono.defer(() -> sendInvitationEmails(share.getUuid().toString(), user, new ArrayList<>(recipients), request.getMailLanguage())))
			.then(Mono.just(toDto(share, shareRecipients.stream().map(r -> r.getRecipient()).toList(), elements, null)))
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
	
	@Transactional
	public Mono<Void> createShareWithQuota(ShareEntity share, List<ShareElementEntity> elements, List<ShareRecipientEntity> recipients) {
		return quotaService.addShares(share.getOwner(), 1)
		.then(r2dbc.insert(share))
		.thenMany(
			Flux.fromIterable(elements).flatMap(r2dbc::insert, 3, 6)
			.onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
		)
		.thenMany(
			Flux.fromIterable(recipients).flatMap(r2dbc::insert, 3, 6)
			.onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
		)
		.then();
	}
	
	private Mono<Void> sendInvitationEmails(String uuid, String owner, List<String> recipients, String language) {
		if (recipients.isEmpty()) return Mono.empty();
		return userRepo.findAllByEmailIn(recipients)
		.collectList()
		.flatMapMany(existingUsers ->
			Flux.fromIterable(recipients)
			.flatMap(recipient ->
				sendInvitationEmail(uuid, owner, recipient, existingUsers.stream().filter(u -> u.getEmail().equals(recipient)).findAny(), language)
				, 2, 4
			)
		).then();
	}
	
	private Mono<Void> sendInvitationEmail(String uuid, String owner, String recipient, Optional<UserEntity> optUser, String language) {
		if (optUser.isEmpty() || optUser.get().getPassword() == null) {
			String token;
			try {
				token = tokenService.generate(new TokenData("share", recipient, uuid + "/" + owner));
			} catch (Exception e) {
				return Mono.empty();
			}
			return emailService.send(EmailService.SHARE_INVITE_PRIORITY, recipient, "invite_share", language, Map.of(
				"from", owner,
				"link", emailService.getLinkUrl(token + "?lang=" + language)
			));
		} else {
			return emailService.send(EmailService.SHARE_NEW_PRIORITY, recipient, "new_share", language, Map.of(
				"from", owner
			));
		}
	}
	
	private Mono<Share> getShare(String id, String owner) {
		return shareRepo.findOneByUuidAndOwner(UUID.fromString(id), owner)
		.flatMap(this::myShareWithElements);
	}
	
	public Flux<Share> getShares(Authentication auth) {
		String user = auth.getPrincipal().toString();
		Flux<Share> sharedByMe = shareRepo.findAllByOwner(user).flatMap(this::myShareWithElements);
		Flux<Share> sharedWithMe = shareRecipientRepo.findAllByRecipient(user).collectList()
			.flatMapMany(this::getSharesFromRecipients)
			.flatMap(share -> getTrails(share).map(trails -> toDto(share, List.of(user), null, trails)));
		return Flux.concat(sharedByMe, sharedWithMe);
	}
	
	private Flux<ShareEntity> getSharesFromRecipients(List<ShareRecipientEntity> recipients) {
		if (recipients.isEmpty()) return Flux.empty();
		Iterator<ShareRecipientEntity> it = recipients.iterator();
		Condition condition = conditionOnRecipient(it.next());
		while (it.hasNext()) condition = condition.or(conditionOnRecipient(it.next()));
		Select select = Select.builder()
			.select(Expressions.asterisk(ShareEntity.TABLE))
			.from(ShareEntity.TABLE)
			.where(condition)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), ShareEntity.class).all();
	}
	
	private Condition conditionOnRecipient(ShareRecipientEntity recipient) {
		return Conditions.isEqual(ShareEntity.COL_OWNER, SQL.literalOf(recipient.getOwner()))
		.and(Conditions.isEqual(ShareEntity.COL_UUID, SQL.literalOf(recipient.getUuid().toString())));
	}
	
	private Mono<Share> myShareWithElements(ShareEntity share) {
		Mono<List<String>> recipients = getRecipients(share.getUuid(), share.getOwner()).publishOn(Schedulers.parallel());
		Mono<List<UUID>> elements = getElements(share.getUuid(), share.getOwner()).publishOn(Schedulers.parallel());
		
		Mono<Tuple3<List<String>, List<UUID>, List<UUID>>> request;

		if (share.getElementType().equals(ShareElementType.TRAIL)) {
			request = Mono.zip(recipients, elements).map(t -> Tuples.of(t.getT1(), t.getT2(), t.getT2()));
		} else {
			request = Mono.zip(
				recipients,
				elements,
				getTrails(share).publishOn(Schedulers.parallel())
			);
		}
		return request.map(tuple -> toDto(share, tuple.getT1(), tuple.getT2(), tuple.getT3()));
	}
	
	private Mono<List<String>> getRecipients(UUID shareId, String owner) {
		Select select = Select.builder()
			.select(ShareRecipientEntity.COL_RECIPIENT)
			.from(ShareRecipientEntity.TABLE)
			.where(
				Conditions.isEqual(ShareRecipientEntity.COL_UUID, SQL.literalOf(shareId.toString()))
				.and(Conditions.isEqual(ShareRecipientEntity.COL_OWNER, SQL.literalOf(owner)))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), String.class).all().collectList();
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
		if (share.getElementType().equals(ShareElementType.TRAIL)) return getElements(share.getUuid(), share.getOwner());
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
				.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(share.getOwner())))
				.and(Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(share.getOwner())))
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
				.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(share.getOwner())))
				.and(Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(share.getOwner())))
			)
			.build();
		return r2dbc.query(DbUtils.select(select, null, r2dbc), UUID.class).all().collectList();
	}
	
	
	@Transactional
	public Mono<Void> deleteShare(String id, String from, Authentication auth) {
		String user = auth.getPrincipal().toString();
		String fromEmail = from.toLowerCase();
		if (user.equals(fromEmail)) {
			return deleteSharesWithQuota(List.of(UUID.fromString(id)), user, true, true);
		}
		Delete deleteRecipient = Delete.builder().from(ShareRecipientEntity.TABLE)
			.where(
				Conditions.isEqual(ShareRecipientEntity.COL_UUID, SQL.literalOf(id))
				.and(Conditions.isEqual(ShareRecipientEntity.COL_OWNER, SQL.literalOf(fromEmail)))
				.and(Conditions.isEqual(ShareRecipientEntity.COL_RECIPIENT, SQL.literalOf(user)))
			).build();
		return r2dbc.getDatabaseClient().sql(DbUtils.delete(deleteRecipient, null, r2dbc)).then()
			.then(Mono.defer(() -> shareRecipientRepo.countByUuidAndOwner(UUID.fromString(id), fromEmail)))
			.flatMap(remaining -> remaining.longValue() == 0L ? deleteSharesWithQuota(List.of(UUID.fromString(id)), fromEmail, true, false) : Mono.empty());
	}
	
	private Mono<Void> deleteSharesWithQuota(List<UUID> uuids, String owner, boolean withElements, boolean withRecipients) {
		List<StringLiteral> uuidsExpr = uuids.stream().map(uuid -> SQL.literalOf(uuid.toString())).toList();
		Mono<Void> deleteElements = !withElements ? Mono.empty() :
			r2dbc.getDatabaseClient().sql(DbUtils.delete(
				Delete.builder().from(ShareElementEntity.TABLE)
					.where(
						Conditions.isEqual(ShareElementEntity.COL_OWNER, SQL.literalOf(owner))
						.and(Conditions.in(ShareElementEntity.COL_SHARE_UUID, uuidsExpr))
					).build()
				, null, r2dbc
			)).then();
		Mono<Void> deleteRecipients = !withRecipients ? Mono.empty() :
			r2dbc.getDatabaseClient().sql(DbUtils.delete(
				Delete.builder().from(ShareRecipientEntity.TABLE)
					.where(
						Conditions.isEqual(ShareRecipientEntity.COL_OWNER, SQL.literalOf(owner))
						.and(Conditions.in(ShareRecipientEntity.COL_UUID, uuidsExpr))
					).build()
				, null, r2dbc
			)).then();
		return deleteElements.then(deleteRecipients)
		.then(shareRepo.deleteAllByUuidInAndOwner(uuids, owner).flatMap(removed -> quotaService.sharesDeleted(owner, removed)));
	}
	
	public Mono<Void> trailsDeleted(Collection<UUID> trailsUuids, String owner) {
		return self.deleteSharesWithQuota(ShareElementType.TRAIL, trailsUuids, owner);
	}
	
	public Mono<Void> tagsDeleted(Collection<UUID> tagsUuids, String owner) {
		return self.deleteSharesWithQuota(ShareElementType.TAG, tagsUuids, owner);
	}
	
	public Mono<Void> collectionsDeleted(Collection<UUID> collectionsUuids, String owner) {
		return self.deleteSharesWithQuota(ShareElementType.COLLECTION, collectionsUuids, owner);
	}
	
	@Transactional
	public Mono<Void> deleteSharesWithQuota(ShareElementType type, Collection<UUID> elements, String owner) {
		Select selectSharesIds = Select.builder()
			.select(ShareEntity.COL_UUID)
			.from(ShareElementEntity.TABLE)
			.join(ShareEntity.TABLE).on(
				Conditions.isEqual(ShareEntity.COL_UUID, ShareElementEntity.COL_SHARE_UUID)
				.and(Conditions.isEqual(ShareEntity.COL_OWNER, SQL.literalOf(owner)))
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
					return deleteSharesWithQuota(toDelete, owner, false, true);
				});
			}));
		});
	}
	
	private Share toDto(ShareEntity entity, List<String> recipients, List<UUID> elements, List<UUID> trails) {
		return new Share(
			entity.getUuid().toString(),
			entity.getOwner(),
			entity.getVersion(),
			entity.getCreatedAt(),
			entity.getUpdatedAt(),
			recipients,
			entity.getElementType(),
			entity.getName(),
			entity.isIncludePhotos(),
			elements != null ? elements.stream().map(UUID::toString).toList() : null,
			trails != null ? trails.stream().map(UUID::toString).toList() : null
		);
	}
	
	public Select selectSharedElementsWithMe(
		String recipient,
		Expression[] selectFields,
		Table joinTable,
		Condition joinFromTrailEntity,
		Condition additionalSelectCondition
	) {
		SelectFromAndJoinCondition select = Select.builder()
	    .select(selectFields)
	    .from(ShareRecipientEntity.TABLE)
	    .join(ShareEntity.TABLE).on(
	    	Conditions.isEqual(ShareRecipientEntity.COL_OWNER, ShareEntity.COL_OWNER)
	    	.and(Conditions.isEqual(ShareRecipientEntity.COL_UUID, ShareEntity.COL_UUID))
	    )
	    .join(ShareElementEntity.TABLE).on(
	    	Conditions.isEqual(ShareElementEntity.COL_SHARE_UUID, ShareEntity.COL_UUID)
	    	.and(Conditions.isEqual(ShareElementEntity.COL_OWNER, ShareEntity.COL_OWNER))
	    )
	    .leftOuterJoin(TrailTagEntity.TABLE).on(
	    	Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE,  SQL.literalOf(ShareElementType.TAG.name()))
	    	.and(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailTagEntity.COL_TAG_UUID))
	    	.and(Conditions.isEqual(ShareEntity.COL_OWNER, TrailTagEntity.COL_OWNER))
	    )
	    .join(TrailEntity.TABLE).on(
	    	Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.COLLECTION.name()))
	    	.and(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailEntity.COL_COLLECTION_UUID))
	    	.and(Conditions.isEqual(ShareEntity.COL_OWNER, TrailEntity.COL_OWNER))
	    	.or(
	    		Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TRAIL.name()))
	    		.and(Conditions.isEqual(ShareElementEntity.COL_ELEMENT_UUID, TrailEntity.COL_UUID))
	    		.and(Conditions.isEqual(ShareEntity.COL_OWNER, TrailEntity.COL_OWNER))
	    	)
	    	.or(
	    		Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.TAG.name()))
	    		.and(Conditions.isEqual(TrailTagEntity.COL_TRAIL_UUID, TrailEntity.COL_UUID))
	    		.and(Conditions.isEqual(ShareEntity.COL_OWNER, TrailEntity.COL_OWNER))
	    	)
	    );
		if (joinTable != null && joinFromTrailEntity != null) select = select.join(joinTable).on(joinFromTrailEntity);
		Condition condition = Conditions.isEqual(ShareRecipientEntity.COL_RECIPIENT, SQL.literalOf(recipient));
		if (additionalSelectCondition != null) condition = condition.and(additionalSelectCondition);
		return select
			.where(condition)
			.build();
	}
	
	public Mono<Share> updateShare(String uuid, UpdateShareRequest request, Authentication auth) {
		String user = auth.getPrincipal().toString();
		return self.updateShareAndRecipients(uuid, user, request)
		.flatMap(added -> sendInvitationEmails(uuid, user, added, request.getMailLanguage()))
		.then(Mono.defer(() -> getShare(uuid, user)));
	}
	
	@Transactional
	public Mono<List<String>> updateShareAndRecipients(String uuid, String owner, UpdateShareRequest request) {
		return shareRepo.findByUuidAndOwner(UUID.fromString(uuid), owner)
		.switchIfEmpty(Mono.error(new NotFoundException("share", uuid)))
		.flatMap(share -> {
			share.setName(request.getName());
			share.setIncludePhotos(request.isIncludePhotos());
			Mono<Long> updateEntity = DbUtils.updateByUuidAndOwner(r2dbc, share);
			return updateEntity.then(updateRecipients(share, uuid, owner, request))
			.flatMap(added ->
				shareRecipientRepo.countByUuidAndOwner(share.getUuid(), owner)
				.flatMap(newNb -> {
					if (newNb.longValue() == 0L) return deleteSharesWithQuota(List.of(share.getUuid()), owner, true, false).thenReturn(List.of());
					if (newNb.longValue() > 20L) return Mono.error(new BadRequestException("A share cannot exceed 20 recipients"));
					return Mono.just(added);
				})
			);
		});
	}
	
	private Mono<List<String>> updateRecipients(ShareEntity share, String uuid, String owner, UpdateShareRequest request) {
		return shareRecipientRepo.findAllByUuidAndOwner(share.getUuid(), share.getOwner()).collectList()
		.flatMap(existingRecipients -> {
			List<String> recipientsToDelete = new LinkedList<>(existingRecipients.stream().map(r -> r.getRecipient()).toList());
			List<String> recipientsToAdd = new LinkedList<>();
			for (String recipient : request.getRecipients()) {
				String s = recipient.toLowerCase().trim();
				if (!recipientsToDelete.removeIf(m -> m.toLowerCase().trim().equals(s)))
					recipientsToAdd.add(recipient.trim());
			}
			Mono<Void> deleteRecipients = deleteRecipients(uuid, owner, recipientsToDelete);
			Mono<List<String>> addRecipients = addRecipients(share.getUuid(), owner, recipientsToAdd);
			return deleteRecipients.then(addRecipients);
		});
	}
	
	private Mono<Void> deleteRecipients(String uuid, String owner, List<String> toDelete) {
		if (toDelete == null || toDelete.isEmpty()) return Mono.empty();
		return r2dbc.getDatabaseClient().sql(DbUtils.delete(
				Delete.builder().from(ShareRecipientEntity.TABLE)
				.where(
					Conditions.isEqual(ShareRecipientEntity.COL_OWNER, SQL.literalOf(owner))
					.and(Conditions.isEqual(ShareRecipientEntity.COL_UUID, SQL.literalOf(uuid)))
					.and(Conditions.in(ShareRecipientEntity.COL_RECIPIENT, toDelete.stream().map(r -> SQL.literalOf(r.toLowerCase())).toList()))
				).build()
			, null, r2dbc
		)).then();
	}
	
	private Mono<List<String>> addRecipients(UUID uuid, String owner, List<String> toAdd) {
		if (toAdd == null || toAdd.isEmpty()) return Mono.just(List.of());
		return shareRecipientRepo.findAllByUuidAndOwnerAndRecipientIn(uuid, owner, toAdd.stream().map(String::toLowerCase).toList())
		.collectList()
		.flatMap(existing -> {
			List<String> remaining = toAdd.stream()
				.map(String::toLowerCase)
				.filter(r -> existing.stream().noneMatch(e -> e.getRecipient().equals(r)))
				.distinct()
				.toList();
			if (remaining.isEmpty()) return Mono.empty();
			return Flux.fromIterable(remaining)
			.flatMap(recipient -> {
				ShareRecipientEntity entity = new ShareRecipientEntity(uuid, owner, recipient);
				return r2dbc.insert(entity);
			}, 2, 4)
			.then(Mono.just(remaining));
		}).switchIfEmpty(Mono.just(List.of()));
	}
	
}
