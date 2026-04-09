package org.trailence.trail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.notifications.NotificationsService;
import org.trailence.preferences.db.UserAvatarRepository;
import org.trailence.storage.FileService;
import org.trailence.trail.db.ModerationMessageEntity;
import org.trailence.trail.db.ModerationMessageRepository;
import org.trailence.trail.db.PhotoRepository;
import org.trailence.trail.db.PublicTrailFeedbackReplyRepository;
import org.trailence.trail.db.PublicTrailFeedbackRepository;
import org.trailence.trail.db.PublicTrailFeedbackRepository.UuidAndTrailUuid;
import org.trailence.trail.db.PublicTrailRepository;
import org.trailence.trail.db.TrackRepository;
import org.trailence.trail.db.TrailCollectionRepository;
import org.trailence.trail.db.TrailRepository;
import org.trailence.trail.dto.FeedbackToReview;
import org.trailence.trail.dto.ModerationCounts;
import org.trailence.trail.dto.Photo;
import org.trailence.trail.dto.PublicTrailRemoveRequest;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Trail;
import org.trailence.trail.dto.TrailAndPhotos;
import org.trailence.trail.dto.TrailCollectionType;
import org.trailence.trail.exceptions.TrailNotFound;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ModerationService {
	
	private final TrailRepository trailRepo;
	private final TrackRepository trackRepo;
	private final PhotoRepository photoRepo;
	private final TrailCollectionRepository collectionRepo;
	private final ModerationMessageRepository messageRepo;
	private final PublicTrailFeedbackRepository feedbackRepo;
	private final PublicTrailFeedbackReplyRepository feedbackReplyRepo;
	private final PublicTrailRepository publicTrailRepo;
	private final UserAvatarRepository avatarRepo;
	private final TrailService trailService;
	private final TrackService trackService;
	private final PhotoService photoService;
	private final FileService fileService;
	private final NotificationsService notifService;
	private final PublicTrailService publicTrailService;
	private final FeedbackService feedbackService;
	
	public Flux<TrailAndPhotos> getTrailsToReview(int size, Authentication auth) {
		return trailRepo.findTrailsToReview(TrailenceUtils.isAdmin(auth) ? "" : TrailenceUtils.email(auth), size)
		.flatMap(trail ->
			photoRepo.findAllByTrailUuidInAndOwner(List.of(trail.getUuid()), trail.getOwner())
			.map(photoService::toDto)
			.collectList()
			.map(photos -> new TrailAndPhotos(trailService.toDTO(trail), photos))
		, 2, 1)
		.flatMap(this::addMessages, 1, 1);
	}
	
	public Mono<TrailAndPhotos> getTrailToReview(String trailUuid, String owner, Authentication auth) {
		owner = owner.toLowerCase();
		if (owner.equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return trailRepo.findTrailToReview(UUID.fromString(trailUuid), owner)
		.switchIfEmpty(Mono.error(new TrailNotFound(trailUuid, owner)))
		.flatMap(trail ->
			photoRepo.findAllByTrailUuidInAndOwner(List.of(trail.getUuid()), trail.getOwner())
			.map(photoService::toDto)
			.collectList()
			.map(photos -> new TrailAndPhotos(trailService.toDTO(trail), photos))
		)
		.flatMap(this::addMessages);
	}
	
	private Mono<TrailAndPhotos> addMessages(TrailAndPhotos trail) {
		return messageRepo.findOneByUuidAndOwnerAndMessageType(UUID.fromString(trail.getTrail().getUuid()), trail.getTrail().getOwner(), ModerationMessageEntity.TYPE_PUBLISH)
		.map(message -> {
			trail.getTrail().setPublicationMessageFromAuthor(message.getAuthorMessage());
			trail.getTrail().setPublicationMessageFromModerator(message.getModeratorMessage());
			return message;
		})
		.thenReturn(trail);
	}
	
	public Mono<Track> getTrackFromReview(String trailUuid, String trailOwner, String trackUuid, Authentication auth) {
		if (trailOwner.toLowerCase().equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return trackRepo.findTrackForReview(UUID.fromString(trailUuid), UUID.fromString(trackUuid), trailOwner.toLowerCase())
		.map(trackService::toDTO)
		.switchIfEmpty(Mono.error(new NotFoundException("track", trackUuid)));
	}
	
	public Mono<Flux<DataBuffer>> getPhotoFileContentFromReview(String photoOwner, String photoUuid, Authentication auth) {
		if (photoOwner.toLowerCase().equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return photoRepo.findByUuidAndOwnerFromReview(UUID.fromString(photoUuid), photoOwner.toLowerCase())
		.switchIfEmpty(Mono.error(new NotFoundException("photo", photoUuid + '-' + photoOwner)))
		.flatMap(photo -> Mono.just(fileService.getFileContent(photo.getFileId())));
	}
	
	public Mono<Trail> updateTrailForReview(Trail trail, Authentication auth) {
		String owner = trail.getOwner().toLowerCase();
		if (owner.equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return trailRepo.findByUuidAndOwner(UUID.fromString(trail.getUuid()), owner)
		.switchIfEmpty(Mono.error(new TrailNotFound(trail.getUuid(), owner)))				
		.flatMap(entity -> trailService.updateTrailAsModerator(entity, trail, false));
	}
	
	public Mono<Trail> reject(Trail trail, Authentication auth) {
		String owner = trail.getOwner().toLowerCase();
		if (owner.equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return trailRepo.findByUuidAndOwner(UUID.fromString(trail.getUuid()), owner)
		.switchIfEmpty(Mono.error(new TrailNotFound(trail.getUuid(), owner)))
		.flatMap(entity -> trailService.updateTrailAsModerator(entity, trail, true))
		.flatMap(result -> notifService.create(owner, "publications.rejected", List.of(trail.getName(), trail.getUuid(), owner)).thenReturn(result));
	}
	
	public Mono<Photo> updatePhoto(Photo photo, Authentication auth) {
		String owner = photo.getOwner().toLowerCase();
		if (owner.equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return photoRepo.findByUuidAndOwner(UUID.fromString(photo.getUuid()), owner)
		.switchIfEmpty(Mono.error(new NotFoundException("photo", photo.getUuid() + '-' + owner)))
		.flatMap(entity ->
			trailRepo.findByUuidAndOwner(entity.getTrailUuid(), owner)
			.switchIfEmpty(Mono.error(new TrailNotFound(entity.getTrailUuid().toString(), owner)))
			.flatMap(trail ->
				collectionRepo.findByUuidAndOwner(trail.getCollectionUuid(), owner)
				.switchIfEmpty(Mono.error(new NotFoundException("collection", "PUB_SUBMIT-" + owner)))
				.flatMap(collection -> {
					if (!TrailCollectionType.PUB_SUBMIT.equals(collection.getType()))
						return Mono.error(new TrailNotFound(entity.getTrailUuid().toString(), owner));
					return photoService.updatePhotoAsSuperUser(entity, photo);
				})
			)
		);
	}
	
	public Mono<Void> deletePhoto(String uuid, String owner, Authentication auth) {
		String email = owner.toLowerCase();
		if (email.equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return photoRepo.findByUuidAndOwner(UUID.fromString(uuid), email)
		.flatMap(entity ->
			trailRepo.findByUuidAndOwner(entity.getTrailUuid(), email)
			.flatMap(trail ->
				collectionRepo.findByUuidAndOwner(trail.getCollectionUuid(), email)
				.flatMap(collection -> {
					if (!TrailCollectionType.PUB_SUBMIT.equals(collection.getType())) return Mono.empty();
					return photoService.deletePhotoWithFileAndQuota(entity).then();
				})
			)
		);
	}
	
	public Mono<Photo> createPhoto(
		String photoUuid, String owner, String trailUuid,
		String description, Long dateTaken, Long latitude, Long longitude, boolean isCover, int index,
		Flux<DataBuffer> content, long size,
		Authentication auth
	) {
		String email = owner.toLowerCase();
		if (email.equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		ValidationUtils.field("photoUuid", photoUuid).notNull().isUuid();
		ValidationUtils.field("trailUuid", trailUuid).notNull().isUuid();
		ValidationUtils.field("description", description).nullable().maxLength(5000);
		return trailRepo.findByUuidAndOwner(UUID.fromString(trailUuid), email)
			.flatMap(trail ->
				collectionRepo.findByUuidAndOwner(trail.getCollectionUuid(), email)
				.flatMap(collection -> {
					if (!TrailCollectionType.PUB_SUBMIT.equals(collection.getType())) return Mono.empty();
					return photoService.createPhotoWithQuota(photoUuid, owner, trailUuid, description, dateTaken, latitude, longitude, isCover, index, content, size);
				})
			)
			.switchIfEmpty(Mono.error(new TrailNotFound(trailUuid, email)));
	}
	
	public Mono<Trail> updateTrailTrack(String trailUuid, String trailOwner, Track track, Authentication auth) {
		String owner = trailOwner.toLowerCase();
		if (owner.equals(TrailenceUtils.email(auth)) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		track.setOwner(owner);
		return trailRepo.findTrailToReview(UUID.fromString(trailUuid), owner)
		.switchIfEmpty(Mono.error(new TrailNotFound(trailUuid, owner)))
		.flatMap(trail ->
			trackService.createTrackAsSuperUser(track)
			.flatMap(newTrack -> {
				var newDto = trailService.toDTO(trail);
				newDto.setCurrentTrackUuid(newTrack.getUuid());
				return trailService.updateTrailAsModerator(trail, newDto, false);
			})
		);
	}
	
	public Mono<List<FeedbackToReview>> getFeedbackToReview(Authentication auth) {
		return getFeedbackUuidWithTrailToReview(TrailenceUtils.isAdmin(auth) ? null : TrailenceUtils.email(auth))
		.flatMap(feedbackUuids -> {
			if (feedbackUuids.isEmpty()) return Mono.just(List.<FeedbackToReview>of());
			Set<UUID> trailsUuids = new HashSet<>();
			for (var f : feedbackUuids) trailsUuids.add(f.getPublicTrailUuid());
			return publicTrailRepo.getTrailsNameAndDescription(trailsUuids)
			.flatMap(trail -> {
				FeedbackToReview result = new FeedbackToReview(trail.getUuid().toString(), trail.getName(), trail.getDescription(), new LinkedList<>());
				return feedbackService.fetchFeedbacks(trail.getUuid().toString(), (sql, _) -> 
					sql.append(" AND public_trail_feedback.uuid IN (")
					.append(String.join(",",feedbackUuids.stream().filter(f -> f.getPublicTrailUuid().equals(trail.getUuid())).map(u -> "'" + u.getUuid() + "'").toList()))
					.append(')')
				, auth)
				.map(feedbacks -> {
					result.getFeedbacks().addAll(feedbacks);
					return result;
				});
			}, 1, 1)
			.collectList();
		});
	}
	
	private Mono<List<UuidAndTrailUuid>> getFeedbackUuidWithTrailToReview(String emailToExclude) {
		return (emailToExclude == null ? feedbackRepo.getToReview() : feedbackRepo.getToReview(emailToExclude)).collectList()
		.flatMap(uuids -> {
			Set<UUID> notIn = new HashSet<>();
			for (var u : uuids) notIn.add(u.getUuid());
			if (notIn.isEmpty()) notIn.add(UUID.randomUUID());
			return (emailToExclude == null ? feedbackReplyRepo.getToReview(notIn) : feedbackReplyRepo.getToReview(notIn, emailToExclude)).collectList()
			.map(moreUuids -> {
				if (moreUuids.isEmpty()) return uuids;
				List<UuidAndTrailUuid> all = new ArrayList<>(uuids.size() + moreUuids.size());
				all.addAll(uuids);
				for (var m : moreUuids) if (!all.contains(m)) all.add(m);
				return all;
			});
		});
	}
	
	public Mono<Void> feedbackValidated(String feedbackUuid, Authentication auth) {
		return feedbackRepo.findById(UUID.fromString(feedbackUuid))
		.flatMap(entity -> {
			if (!TrailenceUtils.isAdmin(auth) && TrailenceUtils.email(auth).equals(entity.getEmail()))
				return Mono.error(new ForbiddenException());
			entity.setReviewed(true);
			return feedbackRepo.save(entity);
		})
		.then();
	}

	public Mono<Void> feedbackReplyValidated(String replyUuid, Authentication auth) {
		return feedbackReplyRepo.findById(UUID.fromString(replyUuid))
		.flatMap(entity -> {
			if (!TrailenceUtils.isAdmin(auth) && TrailenceUtils.email(auth).equals(entity.getEmail()))
				return Mono.error(new ForbiddenException());
			entity.setReviewed(true);
			return feedbackReplyRepo.save(entity);
		})
		.then();
	}
	
	public Mono<List<PublicTrailRemoveRequest>> getRemoveRequests(Authentication auth) {
		if (auth == null) return Mono.error(new ForbiddenException());
		Flux<ModerationMessageEntity> messages;
		if (TrailenceUtils.isAdmin(auth)) messages = messageRepo.getRemoveRequests(ModerationMessageEntity.TYPE_REMOVE);
		else if (TrailenceUtils.hasRole(auth, TrailenceUtils.ROLE_MODERATOR)) messages = messageRepo.getRemoveRequestsNotFrom(ModerationMessageEntity.TYPE_REMOVE, TrailenceUtils.email(auth));
		else return Mono.error(new ForbiddenException());
		return messages.map(entity -> new PublicTrailRemoveRequest(entity.getUuid().toString(), entity.getOwner(), entity.getAuthorMessage())).collectList();
	}
	
	public Mono<Void> declineRemoveRequests(List<String> uuids, Authentication auth) {
		if (auth == null) return Mono.error(new ForbiddenException());
		List<UUID> toRemove = uuids.stream().map(UUID::fromString).toList();
		return messageRepo.findAllByUuidInAndMessageType(toRemove, ModerationMessageEntity.TYPE_REMOVE).collectList()
		.flatMap(entities -> {
			if (entities.size() != toRemove.size()) return Mono.error(new NotFoundException("remove-request", uuids.toString()));
			if (!TrailenceUtils.isAdmin(auth) && entities.stream().anyMatch(e -> e.getOwner().equals(TrailenceUtils.email(auth)))) return Mono.error(new ForbiddenException());
			return messageRepo.deleteAllByUuidInAndMessageType(toRemove, ModerationMessageEntity.TYPE_REMOVE);
		});
	}
	
	public Mono<Void> acceptRemoveRequests(List<String> uuids, Authentication auth) {
		if (auth == null) return Mono.error(new ForbiddenException());
		List<UUID> toRemove = uuids.stream().map(UUID::fromString).toList();
		return messageRepo.findAllByUuidInAndMessageType(toRemove, ModerationMessageEntity.TYPE_REMOVE).collectList()
		.flatMap(entities -> {
			if (entities.size() != toRemove.size()) return Mono.error(new NotFoundException("remove-request", uuids.toString()));
			if (!TrailenceUtils.isAdmin(auth) && entities.stream().anyMatch(e -> e.getOwner().equals(TrailenceUtils.email(auth)))) return Mono.error(new ForbiddenException());
			return Flux.fromIterable(entities)
			.flatMap(entity -> publicTrailService.deletePublicTrailAsModerator(entity.getUuid().toString()), 1, 1)
			.then();
		});
	}
	
	public Mono<Long> getNumberOfTrailsToReview() {
		return trailRepo.countTrailsToReview();
	}
	
	public Mono<Long> getNumberOfCommentsToReview() {
		return feedbackRepo.countToReview();
	}
	
	public Mono<Long> getNumberOfCommentRepliesToReview() {
		return feedbackReplyRepo.countToReview();
	}
	
	public Mono<Long> getNumberOfRemovalRequestsToReview() {
		return messageRepo.countRemoveRequests(ModerationMessageEntity.TYPE_REMOVE);
	}
	
	public Mono<ModerationCounts> getCounters(Authentication auth) {
		boolean admin = TrailenceUtils.isAdmin(auth);
		String email = TrailenceUtils.email(auth);
		Mono<Long> trails = admin ? trailRepo.countTrailsToReview() : trailRepo.countTrailsToReview(email);
		Mono<Long> comments = admin ? feedbackRepo.countToReview() : feedbackRepo.countToReview(email);
		Mono<Long> commentReplies = admin ? feedbackReplyRepo.countToReview() : feedbackReplyRepo.countToReview(email);
		Mono<Long> removeRequests = admin ? messageRepo.countRemoveRequests(ModerationMessageEntity.TYPE_REMOVE) : messageRepo.countRemoveRequests(ModerationMessageEntity.TYPE_REMOVE, email);
		Mono<Long> avatars = admin ? avatarRepo.countAvatarToReview() : avatarRepo.countAvatarToReview(email);
		return Mono.zip(trails, comments, commentReplies, removeRequests, avatars)
		.map(t -> new ModerationCounts(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5()));
	}
}
