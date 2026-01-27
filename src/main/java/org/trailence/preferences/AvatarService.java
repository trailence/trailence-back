package org.trailence.preferences;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.global.exceptions.ValidationUtils;
import org.trailence.notifications.NotificationsService;
import org.trailence.preferences.db.UserAvatarEntity;
import org.trailence.preferences.db.UserAvatarRepository;
import org.trailence.preferences.dto.AvatarDto;
import org.trailence.storage.FileService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class AvatarService {
	
	private final UserAvatarRepository repo;
	private final FileService fileService;
	private final NotificationsService notifService;
	
	@Autowired @Lazy @SuppressWarnings("java:S6813")
	private AvatarService self;

	public Mono<AvatarDto> getAvatarInfo(String email) {
		return repo.findById(email)
		.map(this::toDto)
		.switchIfEmpty(Mono.fromSupplier(() -> new AvatarDto(0, false, false, false, false)));
	}
	
	public Mono<AvatarDto> getMyAvatarInfo(Authentication auth) {
		return getAvatarInfo(TrailenceUtils.email(auth));
	}
	
	private AvatarDto toDto(UserAvatarEntity entity) {
		return new AvatarDto(entity.getVersion(), entity.getCurrentFileId() != null, entity.isCurrentPublic(), entity.getNewFileId() != null, entity.isNewPublic());
	}
	
	public Mono<Flux<DataBuffer>> getMyCurrentAvatarFile(Authentication auth) {
		String email = TrailenceUtils.email(auth);
		return repo.findById(email)
		.filter(entity -> entity.getCurrentFileId() != null)
		.map(UserAvatarEntity::getCurrentFileId)
		.flatMap(fileId -> Mono.just(fileService.getFileContent(fileId)))
		.switchIfEmpty(Mono.error(new NotFoundException("avatar", "current")));
	}
	
	public Mono<Flux<DataBuffer>> getMyPendingAvatarFile(Authentication auth) {
		String email = TrailenceUtils.email(auth);
		return repo.findById(email)
		.filter(entity -> entity.getNewFileId() != null)
		.map(UserAvatarEntity::getNewFileId)
		.flatMap(fileId -> Mono.just(fileService.getFileContent(fileId)))
		.switchIfEmpty(Mono.error(new NotFoundException("avatar", "pending")));
	}

	public Mono<Flux<DataBuffer>> getPublicAvatarFile(String uuid, Authentication auth) {
		ValidationUtils.field("uuid", uuid).notNull().isUuid();
		UUID publicUuid = UUID.fromString(uuid);
		return repo.findByPublicUuidAndCurrentPublicTrue(publicUuid)
		.map(UserAvatarEntity::getCurrentFileId)
		.flatMap(fileId -> Mono.just(fileService.getFileContent(fileId)))
		.switchIfEmpty(Mono.error(new NotFoundException("avatar", uuid)));
	}
	
	public Mono<AvatarDto> storeNewAvatar(boolean isPublic, Flux<DataBuffer> data, long size, Authentication auth) {
		String email = TrailenceUtils.email(auth);
		return fileService.storeFile(size, data)
		.flatMap(newFileId ->
			self.storeNewAvatarFile(isPublic, newFileId, email)
			.onErrorResume(error -> fileService.deleteFile(newFileId).then(Mono.error(error)))
		)
		.map(tuple -> {
			Flux.fromIterable(tuple.getT2()).flatMap(fileService::deleteFile).subscribe();
			return toDto(tuple.getT1());
		});
	}
	
	@Transactional
	public Mono<Tuple2<UserAvatarEntity, List<Long>>> storeNewAvatarFile(boolean isPublic, long newFileId, String email) {
		return repo.findById(email)
		.flatMap(entity -> {
			List<Long> filesToDelete = new LinkedList<>();
			if (!isPublic) {
				if (entity.getNewFileId() != null) {
					filesToDelete.add(entity.getNewFileId());
					entity.setNewFileId(null);
					entity.setNewPublic(false);
					entity.setNewFileSubmittedAt(null);
				}
				if (entity.getCurrentFileId() != null) {
					filesToDelete.add(entity.getCurrentFileId());
				}
				entity.setCurrentFileId(newFileId);
				entity.setCurrentPublic(false);
			} else {
				if (entity.getNewFileId() != null) {
					filesToDelete.add(entity.getNewFileId());
				}
				entity.setNewFileId(newFileId);
				entity.setNewPublic(true);
				entity.setNewFileSubmittedAt(System.currentTimeMillis());
			}
			return repo.save(entity).map(e -> Tuples.of(e, filesToDelete));
		})
		.switchIfEmpty(Mono.defer(() -> {
			UserAvatarEntity entity = new UserAvatarEntity();
			entity.setEmail(email);
			entity.setPublicUuid(UUID.randomUUID());
			if (!isPublic) {
				entity.setCurrentFileId(newFileId);
				entity.setCurrentPublic(false);
			} else {
				entity.setNewFileId(newFileId);
				entity.setNewPublic(true);
				entity.setNewFileSubmittedAt(System.currentTimeMillis());
			}
			return repo.save(entity).map(e -> Tuples.of(e, List.of()));
		}))
		;
	}
	
	public Mono<AvatarDto> deleteMyCurrent(Authentication auth) {
		String email = TrailenceUtils.email(auth);
		return repo.findById(email)
		.flatMap(entity -> {
			Long fileId = entity.getCurrentFileId();
			if (fileId == null) return Mono.just(entity);
			entity.setCurrentFileId(null);
			entity.setCurrentPublic(false);
			return repo.save(entity)
			.flatMap(saved -> fileService.deleteFile(fileId).thenReturn(saved));
		})
		.map(this::toDto)
		.switchIfEmpty(Mono.fromSupplier(AvatarDto::new));
	}
	
	public Mono<AvatarDto> deleteMyPending(Authentication auth) {
		String email = TrailenceUtils.email(auth);
		return repo.findById(email)
		.flatMap(entity -> {
			Long fileId = entity.getNewFileId();
			if (fileId == null) return Mono.just(entity);
			entity.setNewFileId(null);
			entity.setNewPublic(false);
			entity.setNewFileSubmittedAt(null);
			return repo.save(entity)
			.flatMap(saved -> fileService.deleteFile(fileId).thenReturn(saved));
		})
		.map(this::toDto)
		.switchIfEmpty(Mono.fromSupplier(AvatarDto::new));
	}
	
	public Mono<Optional<String>> getUserPublicAvatarUuid(String email) {
		return repo.findById(email)
		.filter(entity -> entity.getCurrentFileId() != null && entity.isCurrentPublic())
		.map(entity -> Optional.of(entity.getPublicUuid().toString()))
		.switchIfEmpty(Mono.fromSupplier(Optional::empty));
	}
	
	public Mono<Long> getNumberOfAvatarToReview() {
		return repo.countAvatarToReview();
	}
	
	public Mono<List<String>> getAvatarsToReview(Authentication auth) {
		if (TrailenceUtils.isAdmin(auth)) return repo.getAvatarsToReview().collectList();
		if (TrailenceUtils.hasRole(auth, TrailenceUtils.ROLE_MODERATOR)) return repo.getAvatarsToReviewExcept(TrailenceUtils.email(auth)).collectList();
		return Mono.error(new ForbiddenException());
	}
	
	public Mono<Flux<DataBuffer>> getAvatarFileToReview(String email, Authentication auth) {
		if (!TrailenceUtils.isAdmin(auth) && (!TrailenceUtils.hasRole(auth, TrailenceUtils.ROLE_MODERATOR) || email.toLowerCase().equals(TrailenceUtils.email(auth))))
			return Mono.error(new ForbiddenException());
		return repo.findById(email)
		.filter(entity -> entity.getNewFileId() != null)
		.map(UserAvatarEntity::getNewFileId)
		.flatMap(fileId -> Mono.just(fileService.getFileContent(fileId)))
		.switchIfEmpty(Mono.error(new NotFoundException("avatar", "pending")));
	}
	
	public Mono<Void> avatarModeration(String email, boolean accepted, Authentication auth) {
		if (!TrailenceUtils.isAdmin(auth) && (!TrailenceUtils.hasRole(auth, TrailenceUtils.ROLE_MODERATOR) || email.toLowerCase().equals(TrailenceUtils.email(auth))))
			return Mono.error(new ForbiddenException());
		return repo.findById(email)
		.flatMap(entity -> {
			if (entity.getNewFileId() == null) return Mono.error(new NotFoundException("avatar", "pending"));
			Long toDelete;
			if (accepted) {
				toDelete = entity.getCurrentFileId();
				entity.setCurrentFileId(entity.getNewFileId());
				entity.setCurrentPublic(entity.isNewPublic());
			} else {
				toDelete = entity.getNewFileId();
			}
			entity.setNewFileId(null);
			entity.setNewPublic(false);
			entity.setNewFileSubmittedAt(null);
			return repo.save(entity)
			.then(Mono.defer(() -> {
				if (toDelete != null) return fileService.deleteFile(toDelete);
				return Mono.empty();
			}))
			.then(notifService.create(email, "avatar." + (accepted ? "accepted" : "rejected"), List.of()));
		}).then();
	}
	
}
