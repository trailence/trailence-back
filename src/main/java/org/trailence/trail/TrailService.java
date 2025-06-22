package org.trailence.trail;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.SQL;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.BulkGetUpdates;
import org.trailence.global.db.BulkUtils;
import org.trailence.global.db.BulkUtils.ChecksAndActions;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.dto.UpdateResponse;
import org.trailence.global.dto.Versioned;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.notifications.NotificationsService;
import org.trailence.quotas.QuotaService;
import org.trailence.trail.TrackService.TrackNotFound;
import org.trailence.trail.db.ModerationMessageEntity;
import org.trailence.trail.db.ModerationMessageRepository;
import org.trailence.trail.db.PublicTrailFeedbackEntity;
import org.trailence.trail.db.PublicTrailRepository;
import org.trailence.trail.db.ShareElementEntity;
import org.trailence.trail.db.ShareEntity;
import org.trailence.trail.db.ShareRecipientEntity;
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailCollectionEntity;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.db.TrailEntity;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.dto.ShareElementType;
import org.trailence.trail.dto.Trail;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.user.db.UserEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrailService {

    private final TrailRepository repo;
    private final TrackRepository trackRepo;
    private final TrailCollectionRepository collectionRepo;
    private final ModerationMessageRepository moderationMessageRepo;
    private final PublicTrailRepository publicTrailRepo;
    private final R2dbcEntityTemplate r2dbc;
    private final ShareService shareService;
    private final PhotoService photoService;
    private final QuotaService quotaService;
    private final TrailTagService trailTagService;
    private final TrackService trackService;
    private final NotificationsService notifService;
    
    @Autowired @Lazy @SuppressWarnings("java:S6813")
    private TrailService self;
    
	@Value("${trailence.hostname:trailence.org}")
	private String hostname;
	@Value("${trailence.protocol:https}")
	private String protocol;

    private static final Set<TrailCollectionType> ALLOWED_CREATE_COLLECTION_TYPES = new HashSet<>();
    private static final Set<TrailCollectionType> ALLOWED_OWNER_UPDATE_COLLECTION_TYPES = new HashSet<>();
    private static final Set<TrailCollectionType> ALLOWED_MODERATOR_UPDATE_COLLECTION_TYPES = new HashSet<>();
    private static final Set<TrailCollectionType> ALLOWED_OWNER_MOVE_TO_COLLECTION_TYPES = new HashSet<>();
    private static final Set<TrailCollectionType> ALLOWED_MODERATOR_MOVE_TO_COLLECTION_TYPES = new HashSet<>();
    static {
    	ALLOWED_CREATE_COLLECTION_TYPES.add(TrailCollectionType.MY_TRAILS);
    	ALLOWED_CREATE_COLLECTION_TYPES.add(TrailCollectionType.CUSTOM);
    	ALLOWED_CREATE_COLLECTION_TYPES.add(TrailCollectionType.PUB_DRAFT);
    	ALLOWED_CREATE_COLLECTION_TYPES.add(TrailCollectionType.PUB_SUBMIT);

    	ALLOWED_OWNER_UPDATE_COLLECTION_TYPES.add(TrailCollectionType.MY_TRAILS);
    	ALLOWED_OWNER_UPDATE_COLLECTION_TYPES.add(TrailCollectionType.CUSTOM);
    	ALLOWED_OWNER_UPDATE_COLLECTION_TYPES.add(TrailCollectionType.PUB_DRAFT);
    	ALLOWED_OWNER_UPDATE_COLLECTION_TYPES.add(TrailCollectionType.PUB_REJECT);
    	
    	ALLOWED_MODERATOR_UPDATE_COLLECTION_TYPES.add(TrailCollectionType.PUB_SUBMIT);
    	
    	ALLOWED_OWNER_MOVE_TO_COLLECTION_TYPES.add(TrailCollectionType.MY_TRAILS);
    	ALLOWED_OWNER_MOVE_TO_COLLECTION_TYPES.add(TrailCollectionType.CUSTOM);
    	ALLOWED_OWNER_MOVE_TO_COLLECTION_TYPES.add(TrailCollectionType.PUB_DRAFT);
    	ALLOWED_OWNER_MOVE_TO_COLLECTION_TYPES.add(TrailCollectionType.PUB_SUBMIT);
    	
    	ALLOWED_MODERATOR_MOVE_TO_COLLECTION_TYPES.add(TrailCollectionType.PUB_REJECT);
    }

    public Mono<List<Trail>> bulkCreate(List<Trail> dtos, Authentication auth) {
    	String owner = auth.getPrincipal().toString();
    	return BulkUtils.bulkCreate(
    		dtos, owner,
    		this::validateCreate,
    		dto -> {
    			TrailEntity entity = new TrailEntity();
                entity.setUuid(UUID.fromString(dto.getUuid()));
                entity.setOwner(owner);
                entity.setName(dto.getName());
                entity.setDescription(dto.getDescription());
                entity.setLocation(dto.getLocation());
                entity.setDate(dto.getDate());
                entity.setLoopType(dto.getLoopType());
                entity.setActivity(dto.getActivity());
                entity.setSourceType(dto.getSourceType());
                entity.setSource(dto.getSource());
                entity.setSourceDate(dto.getSourceDate());
                if (dto.getFollowedUuid() != null)
                	entity.setFollowedUuid(UUID.fromString(dto.getFollowedUuid()));
                entity.setFollowedOwner(dto.getFollowedOwner());
                entity.setFollowedUrl(dto.getFollowedUrl());
                entity.setCollectionUuid(UUID.fromString(dto.getCollectionUuid()));
                entity.setOriginalTrackUuid(UUID.fromString(dto.getOriginalTrackUuid()));
                entity.setCurrentTrackUuid(UUID.fromString(dto.getCurrentTrackUuid()));
                if (dto.getPublishedFromUuid() != null)
                	entity.setPublishedFromUuid(UUID.fromString(dto.getPublishedFromUuid()));
                entity.setCreatedAt(dto.getCreatedAt());
                entity.setUpdatedAt(entity.getCreatedAt());
                return entity;
    		},
    		entities -> self.createTrailsWithQuota(entities, owner, dtos),
    		repo
    	)
    	.doOnNext(trails -> handleFollowedTrails(trails, owner))
    	.doOnNext(trails -> handleNotificationsForNewTrails(trails, owner))
    	.map(list -> list.stream().map(this::toDTO).toList());
    }
    
    @Transactional
    public Mono<List<TrailEntity>> createTrailsWithQuota(List<TrailEntity> entities, String owner, List<Trail> dtos) {
    	Set<UUID> collectionsUuids = new HashSet<>();
    	Set<UUID> tracksUuids = new HashSet<>();
    	entities.forEach(entity -> {
    		collectionsUuids.add(entity.getCollectionUuid());
    		tracksUuids.add(entity.getOriginalTrackUuid());
    		tracksUuids.add(entity.getCurrentTrackUuid());
    	});
    	
    	return Mono.zip(
    		collectionRepo.findAllByUuidInAndOwner(collectionsUuids, owner).collectList().publishOn(Schedulers.parallel()),
    		trackRepo.findExistingUuids(tracksUuids, owner).collectList().publishOn(Schedulers.parallel())
    	).flatMap(tuple -> {
    		List<TrailCollectionEntity> existingCollections = tuple.getT1();
    		List<UUID> existingTracksUuids = tuple.getT2();
    		List<Throwable> errors = new LinkedList<>();
    		List<TrailEntity> toCreate = new LinkedList<>();
    		List<Mono<Void>> actions = new LinkedList<>();
    		for (var entity : entities) {
    			var collectionOpt = existingCollections.stream().filter(c -> c.getUuid().equals(entity.getCollectionUuid())).findAny();
    			if (collectionOpt.isEmpty()) {
    				errors.add(new NotFoundException("collection", entity.getCollectionUuid().toString()));
    				continue;
    			}
    			var collection = collectionOpt.get();
    			if (!TrailCollectionType.PUBLICATION_TYPES.contains(collection.getType()))
    				entity.setPublishedFromUuid(null);
    			if (TrailCollectionType.PUB_SUBMIT.equals(collection.getType())) {
    				var dto = dtos.stream().filter(d -> d.getUuid().equals(entity.getUuid().toString())).findAny().get();
    				if (dto.getPublicationMessageFromAuthor() != null && !dto.getPublicationMessageFromAuthor().isBlank())
    					actions.add(r2dbc.insert(new ModerationMessageEntity(entity.getUuid(), entity.getOwner(), dto.getPublicationMessageFromAuthor(), null)).then());
    			}
    			if (!ALLOWED_CREATE_COLLECTION_TYPES.contains(collection.getType()))
    				errors.add(new ForbiddenException("Cannot create a trail in this type of collection"));
    			else if (!existingTracksUuids.contains(entity.getOriginalTrackUuid()))
    				errors.add(new TrackNotFound(owner, entity.getOriginalTrackUuid().toString()));
    			else if (!existingTracksUuids.contains(entity.getCurrentTrackUuid()))
    				errors.add(new TrackNotFound(owner, entity.getCurrentTrackUuid().toString()));
    			else
    				toCreate.add(entity);
    		}
    		if (toCreate.isEmpty()) return Mono.error(errors.getFirst());
    		return (actions.isEmpty() ? Mono.empty() : Flux.fromIterable(actions).flatMap(a -> a).then())
    		.then(quotaService.addTrails(owner, toCreate.size()))
	    	.flatMap(nb -> {
	    		var toCreate2 = nb == toCreate.size() ? toCreate : toCreate.subList(0, nb);
	    		return DbUtils.insertMany(r2dbc, toCreate2);
	    	});
    	});
    }
    
    private void validateCreate(Trail dto) {
    	validate(dto);
    	ValidationUtils.field("originalTrackUuid", dto.getOriginalTrackUuid()).notNull().isUuid();
    	ValidationUtils.field("source", dto.getSource()).nullable().maxLength(2000);
    	ValidationUtils.field("followedUuid", dto.getFollowedUuid()).nullable().isUuid();
    	ValidationUtils.field("followedOwner", dto.getFollowedOwner()).nullable().maxLength(250);
    	ValidationUtils.field("followedUrl", dto.getFollowedUrl()).nullable().maxLength(2000);
    }
    
    private void validate(Trail dto) {
    	ValidationUtils.field("uuid", dto.getUuid()).notNull().isUuid();
    	ValidationUtils.field("name", dto.getName()).nullable().maxLength(200);
    	ValidationUtils.field("description", dto.getDescription()).nullable().maxLength(50000);
    	ValidationUtils.field("location", dto.getLocation()).nullable().maxLength(100);
    	ValidationUtils.field("loopType", dto.getLoopType()).nullable().maxLength(2);
    	ValidationUtils.field("activity", dto.getActivity()).nullable().maxLength(20);
    	ValidationUtils.field("currentTrackUuid", dto.getCurrentTrackUuid()).notNull().isUuid();
    	ValidationUtils.field("collectionUuid", dto.getCollectionUuid()).notNull().isUuid();
    }

    public Flux<Trail> bulkUpdate(List<Trail> dtos, Authentication auth) {
    	String owner = auth.getPrincipal().toString();
    	return BulkUtils.bulkUpdate(
    		dtos, owner,
    		this::validate,
    		(entity, dto, checksAndActions) -> this.updateEntity(entity, dto, checksAndActions, owner, true),
    		repo, r2dbc
    	).map(this::toDTO);
    }
    
    public Mono<Trail> updateTrailAsModerator(TrailEntity entity, Trail dto, boolean isReject) {
    	ChecksAndActions checksAndActions = new ChecksAndActions();
    	Mono<Void> before = Mono.empty();
    	if (isReject)
    		before = collectionRepo.findOneByTypeAndOwner(TrailCollectionType.PUB_REJECT.name(), entity.getOwner())
	    		.switchIfEmpty(Mono.defer(() -> {
	    			TrailCollectionEntity col = new TrailCollectionEntity();
	    			col.setUuid(UUID.randomUUID());
	    			col.setOwner(entity.getOwner());
	    			col.setType(TrailCollectionType.PUB_REJECT);
	    			col.setName("");
	    			col.setCreatedAt(System.currentTimeMillis());
	    			col.setUpdatedAt(col.getCreatedAt());
	    			col.setVersion(1);
	    			return r2dbc.insert(col);
	    		}))
	    		.doOnNext(col -> dto.setCollectionUuid(col.getUuid().toString()))
	    		.then();
    	return before.then(Mono.defer(() -> {
    		boolean updated = this.updateEntity(entity, dto, checksAndActions, entity.getOwner(), false);
    		if (!updated) return Mono.just(dto);
    		return checksAndActions.execute().then(DbUtils.updateByUuidAndOwner(r2dbc, entity))
            .flatMap(nb -> nb == 0 ? Mono.empty() : repo.findByUuidAndOwner(entity.getUuid(), entity.getOwner()))
            .map(this::toDTO)
            .flatMap(response ->
            	moderationMessageRepo.findOneByUuidAndOwner(entity.getUuid(), entity.getOwner())
            	.doOnNext(messageEntity -> {
            		response.setPublicationMessageFromAuthor(messageEntity.getAuthorMessage());
        			response.setPublicationMessageFromModerator(messageEntity.getModeratorMessage());
            	}).thenReturn(response)
            );
    	}));
    }
    
    @SuppressWarnings("java:S3776")
    private boolean updateEntity(TrailEntity entity, Trail dto, ChecksAndActions checksAndActions, String owner, boolean fromOwner) {
    	var allowedCollectionFrom = fromOwner ? ALLOWED_OWNER_UPDATE_COLLECTION_TYPES : ALLOWED_MODERATOR_UPDATE_COLLECTION_TYPES;
    	checksAndActions.addCheck(
    		collectionRepo.findByUuidAndOwner(entity.getCollectionUuid(), owner)
    		.map(col -> {
    			if (allowedCollectionFrom.contains(col.getType())) return Optional.<Throwable>empty();
    			return Optional.<Throwable>of(new ForbiddenException());
    		})
    		.switchIfEmpty(Mono.just(Optional.<Throwable>of(new NotFoundException("collection", entity.getCollectionUuid().toString()))))
    	);
    	
        var changed = false;
        if (dto.getCollectionUuid() != null && !dto.getCollectionUuid().equals(entity.getCollectionUuid().toString())) {
        	var newUuid = UUID.fromString(dto.getCollectionUuid());
        	var allowedTargetTypes = fromOwner ? ALLOWED_OWNER_MOVE_TO_COLLECTION_TYPES : ALLOWED_MODERATOR_MOVE_TO_COLLECTION_TYPES;
        	checksAndActions.addCheck(
        		collectionRepo.findByUuidAndOwner(newUuid, owner)
        		.flatMap(col -> {
        			Mono<Void> action = Mono.empty();
        			if (fromOwner && TrailCollectionType.PUB_SUBMIT.equals(col.getType())) {
        				action = moderationMessageRepo.findOneByUuidAndOwner(entity.getUuid(), owner)
        				.flatMap(messages -> {
        					messages.setAuthorMessage(dto.getPublicationMessageFromAuthor());
        					return DbUtils.updateByUuidAndOwner(r2dbc, messages);
        				})
        				.switchIfEmpty(Mono.defer(() -> {
        					if (dto.getPublicationMessageFromAuthor() != null && !dto.getPublicationMessageFromAuthor().isBlank())
        						return r2dbc.insert(new ModerationMessageEntity(entity.getUuid(), owner, dto.getPublicationMessageFromAuthor(), null)).thenReturn(1L);
        					return Mono.empty();
        				})).then();
        			} else if (!fromOwner && TrailCollectionType.PUB_REJECT.equals(col.getType())) {
        				action = moderationMessageRepo.findOneByUuidAndOwner(entity.getUuid(), owner)
        				.flatMap(messages -> {
        					messages.setModeratorMessage(dto.getPublicationMessageFromModerator());
        					return DbUtils.updateByUuidAndOwner(r2dbc, messages);
        				})
        				.switchIfEmpty(Mono.defer(() -> {
        					if (dto.getPublicationMessageFromModerator() != null && !dto.getPublicationMessageFromModerator().isBlank())
        						return r2dbc.insert(new ModerationMessageEntity(entity.getUuid(), owner, null, dto.getPublicationMessageFromModerator())).thenReturn(1L);
        					return Mono.empty();
        				})).then();
        			}
        			Optional<Throwable> result = allowedTargetTypes.contains(col.getType()) ? Optional.empty() : Optional.of(new ForbiddenException());
        			return action.thenReturn(result);
        		})
        		.switchIfEmpty(Mono.just(Optional.<Throwable>of(new NotFoundException("collection", newUuid.toString()))))
        	);
        	if (fromOwner) {
	        	checksAndActions.addAction(
	        		trailTagService.trailsDeleted(Set.of(entity.getUuid()), owner)
	        	);
        	}
        	entity.setCollectionUuid(newUuid);
        	changed = true;
        }
        if (dto.getCurrentTrackUuid() != null && !dto.getCurrentTrackUuid().equals(entity.getCurrentTrackUuid().toString())) {
        	var newUuid = UUID.fromString(dto.getCurrentTrackUuid());
        	checksAndActions.addCheck(
        		trackRepo.existsByUuidAndOwner(newUuid, owner)
        		.map(exists -> exists.booleanValue() ? Optional.empty() : Optional.of(new TrackNotFound(owner, newUuid.toString())))
        	);
        	if (!entity.getCurrentTrackUuid().equals(entity.getOriginalTrackUuid()))
        		checksAndActions.addAction(trackService.deleteTracksWithQuota(Set.of(entity.getCurrentTrackUuid()), owner));
            entity.setCurrentTrackUuid(newUuid);
            changed = true;
        }
        if (!Objects.equals(entity.getName(), dto.getName())) {
            entity.setName(dto.getName());
            changed = true;
        }
        if (!Objects.equals(entity.getDescription(), dto.getDescription())) {
            entity.setDescription(dto.getDescription());
            changed = true;
        }
        if (!Objects.equals(entity.getLocation(), dto.getLocation())) {
        	entity.setLocation(dto.getLocation());
        	changed = true;
        }
        if (!Objects.equals(entity.getDate(), dto.getDate())) {
        	entity.setDate(dto.getDate());
        	changed = true;
        }
        if (!Objects.equals(entity.getLoopType(), dto.getLoopType())) {
        	entity.setLoopType(dto.getLoopType());
        	changed = true;
        }
        if (!Objects.equals(entity.getActivity(), dto.getActivity())) {
        	entity.setActivity(dto.getActivity());
        	changed = true;
        }
        return changed;
    }

    public Mono<Void> bulkDelete(List<String> uuids, Authentication auth) {
        String owner = auth.getPrincipal().toString();
        return delete(repo.findAllByUuidInAndOwner(uuids.stream().map(UUID::fromString).toList(), owner), owner);
    }

    public Mono<Void> deleteAllFromCollections(Set<UUID> collections, String owner) {
    	return delete(repo.findAllByCollectionUuidInAndOwner(collections, owner), owner);
    }
    
    public Mono<Void> delete(Flux<TrailEntity> toDelete, String owner) {
    	return toDelete.collectList()
		.flatMap(entities -> {
			Set<UUID> trailsUuids = new HashSet<>();
			Set<UUID> tracksUuids = new HashSet<>();
			entities.forEach(entity -> {
				trailsUuids.add(entity.getUuid());
				tracksUuids.add(entity.getOriginalTrackUuid());
				tracksUuids.add(entity.getCurrentTrackUuid());
			});
			return trailTagService.trailsDeleted(trailsUuids, owner)
			.then(trackService.deleteTracksWithQuota(tracksUuids, owner))
			.then(shareService.trailsDeleted(trailsUuids, owner))
			.then(photoService.trailsDeleted(trailsUuids, owner))
			.then(self.deleteTrailsWithQuota(trailsUuids, owner));
		});
    }
    
    @Transactional
    public Mono<Void> deleteTrailsWithQuota(Set<UUID> uuids, String owner) {
    	log.info("Deleting {} trails for {}", uuids.size(), owner);
    	return repo.deleteAllByUuidInAndOwner(uuids, owner)
    	.flatMap(nb -> quotaService.trailsDeleted(owner, nb))
    	.then(moderationMessageRepo.deleteAllByUuidInAndOwner(uuids, owner))
    	.then(Mono.fromRunnable(() -> log.info("Trails deleted ({} for {})", uuids.size(), owner)));
    }

    public Mono<UpdateResponse<Trail>> getUpdates(List<Versioned> known, Authentication auth) {
    	String user = auth.getPrincipal().toString();
    	Flux<TrailEntity> ownedTrails =
    		r2dbc.query(DbUtils.select(
    			Select.builder()
		        .select(AsteriskFromTable.create(TrailEntity.TABLE))
		        .from(TrailEntity.TABLE)
		        .where(Conditions.isEqual(TrailEntity.COL_OWNER, SQL.literalOf(user)))
		        .build(),
		    null, r2dbc), TrailEntity.class).all();

    	Flux<TrailEntity> sharedWithMe = r2dbc.query(
    		DbUtils.select(shareService.selectSharedElementsWithMe(user, new Expression[] { AsteriskFromTable.create(TrailEntity.TABLE) }, null, null, null), null, r2dbc),
    		TrailEntity.class
    	).all()
    	.collectList()
    	// hide source of shared trails if the source is an email not among the friends, or from a file
    	.map(list -> {
    		if (list.isEmpty()) return list;
    		var allFriends = list.stream().map(t -> t.getOwner()).distinct().toList();
    		list.forEach(t -> {
    			if (t.getSource() != null && (
    				(t.getSource().indexOf('@') > 0 && !allFriends.contains(t.getSource())) ||
    				("file".equals(t.getSourceType()))
    			)) {
   					t.setSource(null);
    			}
    		});
    		return list;
    	})
    	.flatMapMany(Flux::fromIterable);
    	    	
    	return BulkGetUpdates.bulkGetUpdates(
    		Flux.concat(ownedTrails, sharedWithMe).distinct(trail -> trail.getOwner() + " " + trail.getUuid().toString()),
    		known,
    		this::toDTO
    	)
    	.flatMap(response -> {
    		var uuids = Stream.concat(response.getCreated().stream(), response.getUpdated().stream()).map(t -> UUID.fromString(t.getUuid())).toList();
    		return moderationMessageRepo.findAllByUuidInAndOwner(uuids, user)
    		.doOnNext(messageEntity -> {
    			var dtoOpt = Stream.concat(response.getCreated().stream(), response.getUpdated().stream()).filter(d -> d.getUuid().equals(messageEntity.getUuid().toString())).findAny();
    			if (dtoOpt.isPresent()) {
    				var dto = dtoOpt.get();
    				dto.setPublicationMessageFromAuthor(messageEntity.getAuthorMessage());
    				dto.setPublicationMessageFromModerator(messageEntity.getModeratorMessage());
    			}
    		})
    		.then().thenReturn(response);
    	});
    }

    public Trail toDTO(TrailEntity entity) {
        return new Trail(
            entity.getUuid().toString(),
            entity.getOwner(),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getName(),
            entity.getDescription(),
            entity.getLocation(),
            entity.getDate(),            entity.getLoopType(),
            entity.getActivity(),
            entity.getSourceType(),
            entity.getSource(),
            entity.getSourceDate(),
            entity.getFollowedUuid() != null ? entity.getFollowedUuid().toString() : null,
            entity.getFollowedOwner(),
            entity.getFollowedUrl(),
            entity.getOriginalTrackUuid().toString(),
            entity.getCurrentTrackUuid().toString(),
            entity.getCollectionUuid().toString(),
            entity.getPublishedFromUuid() != null ? entity.getPublishedFromUuid().toString() : null,
            null, null
        );
    }
    
    private void handleFollowedTrails(List<TrailEntity> trails, String owner) {
    	List<TrailEntity> eligibles = trails.stream().filter(trail -> trail.getFollowedUrl() != null && trail.getFollowedUrl().startsWith(protocol + "://" + hostname + "/trail/trailence/")).toList();
    	if (eligibles.isEmpty()) return;
    	Flux.fromIterable(eligibles)
    	.flatMap(trail -> {
    		String uuidOrSlug = trail.getFollowedUrl().substring(protocol.length() + 3 + hostname.length() + 17);
    		var uuidOpt = TrailenceUtils.ifUuid(uuidOrSlug);
    		return (uuidOpt.isPresent() ? publicTrailRepo.findById(uuidOpt.get()) : publicTrailRepo.findOneBySlug(uuidOrSlug))
    		.flatMap(publicTrail -> {
    			PublicTrailFeedbackEntity feedback = new PublicTrailFeedbackEntity();
    			feedback.setUuid(UUID.randomUUID());
    			feedback.setEmail(owner);
    			feedback.setDate(trail.getCreatedAt());
    			feedback.setPublicTrailUuid(publicTrail.getUuid());
    			return r2dbc.insert(feedback);
    		});
    	}).subscribe();
    }
    
    private void handleNotificationsForNewTrails(List<TrailEntity> trails, String owner) {
    	if (trails.isEmpty()) return;
    	// notifications for new trails in a share => can only be a share of a collection
    	Set<UUID> collections = trails.stream().map(trail -> trail.getCollectionUuid()).collect(Collectors.toSet());
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
    		Conditions.isEqual(ShareEntity.COL_ELEMENT_TYPE, SQL.literalOf(ShareElementType.COLLECTION.name()))
    		.and(Conditions.isEqual(ShareEntity.COL_OWNER, SQL.literalOf(owner)))
    		.and(Conditions.in(ShareElementEntity.COL_ELEMENT_UUID, collections.stream().map(uuid -> SQL.literalOf(uuid.toString())).toList()))
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
    		Long.toString(trails.stream().filter(trail -> trail.getCollectionUuid().equals(share.getT1())).count()),
    		share.getT4().toString(),
    		share.getT3()
    	))).subscribe();
    }

}
