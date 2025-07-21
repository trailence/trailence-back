package org.trailence.notifications;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.notifications.db.NotificationEntity;
import org.trailence.notifications.db.NotificationsRepository;
import org.trailence.notifications.dto.Notification;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class NotificationsService {
	
	private final NotificationsRepository repo;
	private final R2dbcEntityTemplate r2dbc;

	public Mono<Void> create(String user, String text, List<String> textElements) {
		NotificationEntity entity = new NotificationEntity(
			UUID.randomUUID(),
			user,
			System.currentTimeMillis(),
			false,
			text,
			textElements
		);
		return r2dbc.insert(entity).then();
	}
	
	public Flux<Notification> getMyNotifications(int page, int size, Authentication auth) {
		if (page < 0) page = 0;
		if (size < 1) size = 1; else if (size > 200) size = 200;
		return repo.findAllByOwner(auth.getPrincipal().toString(), PageRequest.of(page, size, Direction.DESC, "date")).map(NotificationsService::toDto);
	}
	
	public Mono<Notification> updateNotification(String uuid, Notification dto, Authentication auth) {
		return repo.findById(UUID.fromString(uuid))
		.filter(e -> e.getOwner().equals(auth.getPrincipal().toString()))
		.switchIfEmpty(Mono.error(new NotFoundException("notification", uuid)))
		.flatMap(e -> {
			if (e.isRead() == dto.isRead()) return Mono.just(e);
			e.setRead(dto.isRead());
			return repo.save(e);
		})
		.map(NotificationsService::toDto);
	}
	
	@Scheduled(fixedRate = 24, timeUnit = TimeUnit.HOURS, initialDelay = 1)
	public void clean() {
		repo.deleteByDateLessThan(System.currentTimeMillis() - 200L * 24 * 60 * 60 * 1000).subscribe();
	}
	
	private static Notification toDto(NotificationEntity entity) {
		return new Notification(
			entity.getUuid().toString(),
			entity.getDate(),
			entity.isRead(),
			entity.getText(),
			entity.getTextElements()
		);
	}
	
}
