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
import org.trailence.notifications.NotificationsService;
import org.trailence.storage.FileService;
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
import org.trailence.trail.dto.Photo;
import org.trailence.trail.dto.Track;
import org.trailence.trail.dto.Trail;
import org.trailence.trail.dto.TrailAndPhotos;
import org.trailence.trail.dto.TrailCollectionType;

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
	private final TrailService trailService;
	private final TrackService trackService;
	private final PhotoService photoService;
	private final FileService fileService;
	private final NotificationsService notifService;
	private final PublicTrailService publicTrailService;

	public Flux<TrailAndPhotos> getTrailsToReview(Authentication auth) {
		return trailRepo.findTrailsToReview(TrailenceUtils.isAdmin(auth) ? "" : auth.getPrincipal().toString())
		.flatMap(trail ->
			photoRepo.findAllByTrailUuidInAndOwner(List.of(trail.getUuid()), trail.getOwner())
			.map(photoService::toDto)
			.collectList()
			.map(photos -> new TrailAndPhotos(trailService.toDTO(trail), photos))
		, 2, 1)
		.flatMap(this::addMessages);
	}
	
	public Mono<TrailAndPhotos> getTrailToReview(String trailUuid, String owner, Authentication auth) {
		owner = owner.toLowerCase();
		if (owner.equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return trailRepo.findTrailToReview(UUID.fromString(trailUuid), owner)
		.switchIfEmpty(Mono.error(new NotFoundException("trail", trailUuid + '-' + owner)))
		.flatMap(trail ->
			photoRepo.findAllByTrailUuidInAndOwner(List.of(trail.getUuid()), trail.getOwner())
			.map(photoService::toDto)
			.collectList()
			.map(photos -> new TrailAndPhotos(trailService.toDTO(trail), photos))
		)
		.flatMap(this::addMessages);
	}
	
	private Mono<TrailAndPhotos> addMessages(TrailAndPhotos trail) {
		return messageRepo.findOneByUuidAndOwner(UUID.fromString(trail.getTrail().getUuid()), trail.getTrail().getOwner())
		.map(message -> {
			trail.getTrail().setPublicationMessageFromAuthor(message.getAuthorMessage());
			trail.getTrail().setPublicationMessageFromModerator(message.getModeratorMessage());
			return message;
		})
		.thenReturn(trail);
	}
	
	public Mono<Track> getTrackFromReview(String trailUuid, String trailOwner, String trackUuid, Authentication auth) {
		if (trailOwner.toLowerCase().equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return trackRepo.findTrackForReview(UUID.fromString(trailUuid), UUID.fromString(trackUuid), trailOwner.toLowerCase())
		.map(trackService::toDTO)
		.switchIfEmpty(Mono.error(new NotFoundException("track", trackUuid)));
	}
	
	public Mono<Flux<DataBuffer>> getPhotoFileContentFromReview(String photoOwner, String photoUuid, Authentication auth) {
		if (photoOwner.toLowerCase().equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return photoRepo.findByUuidAndOwnerFromReview(UUID.fromString(photoUuid), photoOwner.toLowerCase())
		.switchIfEmpty(Mono.error(new NotFoundException("photo", photoUuid + '-' + photoOwner)))
		.flatMap(photo -> Mono.just(fileService.getFileContent(photo.getFileId())));
	}
	
	public Mono<Trail> updateTrailForReview(Trail trail, Authentication auth) {
		String owner = trail.getOwner().toLowerCase();
		if (owner.equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return trailRepo.findByUuidAndOwner(UUID.fromString(trail.getUuid()), owner)
		.switchIfEmpty(Mono.error(new NotFoundException("trail", trail.getUuid() + '-' + owner)))				
		.flatMap(entity -> trailService.updateTrailAsModerator(entity, trail, false));
	}
	
	public Mono<Trail> reject(Trail trail, Authentication auth) {
		String owner = trail.getOwner().toLowerCase();
		if (owner.equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return trailRepo.findByUuidAndOwner(UUID.fromString(trail.getUuid()), owner)
		.switchIfEmpty(Mono.error(new NotFoundException("trail", trail.getUuid() + '-' + owner)))
		.flatMap(entity -> trailService.updateTrailAsModerator(entity, trail, true))
		.flatMap(result -> notifService.create(owner, "publications.rejected", List.of(trail.getName(), trail.getUuid(), owner)).thenReturn(result));
	}
	
	public Mono<Photo> updatePhoto(Photo photo, Authentication auth) {
		String owner = photo.getOwner().toLowerCase();
		if (owner.equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		return photoRepo.findByUuidAndOwner(UUID.fromString(photo.getUuid()), owner)
		.switchIfEmpty(Mono.error(new NotFoundException("photo", photo.getUuid() + '-' + owner)))
		.flatMap(entity ->
			trailRepo.findByUuidAndOwner(entity.getTrailUuid(), owner)
			.switchIfEmpty(Mono.error(new NotFoundException("trail", entity.getTrailUuid().toString() + '-' + owner)))
			.flatMap(trail ->
				collectionRepo.findByUuidAndOwner(trail.getCollectionUuid(), owner)
				.switchIfEmpty(Mono.error(new NotFoundException("collection", "PUB_SUBMIT-" + owner)))
				.flatMap(collection -> {
					if (!TrailCollectionType.PUB_SUBMIT.equals(collection.getType()))
						return Mono.error(new NotFoundException("trail", entity.getTrailUuid().toString() + '-' + owner));
					return photoService.updatePhotoAsSuperUser(entity, photo);
				})
			)
		);
	}
	
	public Mono<Void> deletePhoto(String uuid, String owner, Authentication auth) {
		String email = owner.toLowerCase();
		if (email.equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
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
	
	public Mono<Trail> updateTrailTrack(String trailUuid, String trailOwner, Track track, Authentication auth) {
		String owner = trailOwner.toLowerCase();
		if (owner.equals(auth.getPrincipal().toString()) && !TrailenceUtils.isAdmin(auth)) return Mono.error(new ForbiddenException());
		track.setOwner(owner);
		return trailRepo.findTrailToReview(UUID.fromString(trailUuid), owner)
		.switchIfEmpty(Mono.error(new NotFoundException("trail", trailUuid + '-' + owner)))
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
		return feedbackRepo.getToReview().collectList()
		.flatMap(uuids -> {
			Set<UUID> notIn = new HashSet<>();
			for (var u : uuids) notIn.add(u.getUuid());
			if (notIn.isEmpty()) notIn.add(UUID.randomUUID());
			return feedbackReplyRepo.getToReview(notIn).collectList()
			.map(moreUuids -> {
				if (moreUuids.isEmpty()) return uuids;
				List<UuidAndTrailUuid> all = new ArrayList<>(uuids.size() + moreUuids.size());
				all.addAll(uuids);
				for (var m : moreUuids) if (!all.contains(m)) all.add(m);
				return all;
			});
		})
		.flatMap(feedbackUuids -> {
			if (feedbackUuids.isEmpty()) return Mono.just(List.<FeedbackToReview>of());
			Set<UUID> trailsUuids = new HashSet<>();
			for (var f : feedbackUuids) trailsUuids.add(f.getPublicTrailUuid());
			return publicTrailRepo.getTrailsNameAndDescription(trailsUuids)
			.flatMap(trail -> {
				FeedbackToReview result = new FeedbackToReview(trail.getUuid().toString(), trail.getName(), trail.getDescription(), new LinkedList<>());
				return publicTrailService.fetchFeedbacks(trail.getUuid().toString(), sql -> {
					sql.append(" AND public_trail_feedback.uuid IN (")
					.append(String.join(",",feedbackUuids.stream().filter(f -> f.getPublicTrailUuid().equals(trail.getUuid())).map(u -> "'" + u.getUuid() + "'").toList()))
					.append(')');
				}, auth)
				.map(feedbacks -> {
					result.getFeedbacks().addAll(feedbacks);
					return result;
				});
			})
			.collectList();
		});
	}
	
	public Mono<Void> feedbackValidated(String feedbackUuid) {
		return feedbackRepo.findById(UUID.fromString(feedbackUuid))
		.flatMap(entity -> {
			entity.setReviewed(true);
			return feedbackRepo.save(entity);
		})
		.then();
	}

	public Mono<Void> feedbackReplyValidated(String replyUuid) {
		return feedbackReplyRepo.findById(UUID.fromString(replyUuid))
		.flatMap(entity -> {
			entity.setReviewed(true);
			return feedbackReplyRepo.save(entity);
		})
		.then();
	}
	
}
