package org.trailence.contact;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.AsteriskFromTable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.captcha.CaptchaService;
import org.trailence.contact.db.ContactMessageEntity;
import org.trailence.contact.db.ContactMessageRepository;
import org.trailence.contact.dto.ContactMessage;
import org.trailence.contact.dto.CreateMessageRequest;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.db.SqlBuilder;
import org.trailence.global.dto.PageResult;
import org.trailence.global.exceptions.ForbiddenException;

import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class ContactService {
	
	private final ContactMessageRepository repo;
	private final R2dbcEntityTemplate r2dbc;
	private final CaptchaService captcha;

	public Mono<Void> createMessage(CreateMessageRequest request, Authentication auth) {
		return checkCreateRequest(request, auth).flatMap(email -> Mono.defer(() -> {
			ContactMessageEntity entity = new ContactMessageEntity(
				UUID.randomUUID(),
				email,
				request.getType(),
				request.getMessage(),
				System.currentTimeMillis(),
				false
			);
			return r2dbc.insert(entity).then();
		}));
	}
	
	private Mono<String> checkCreateRequest(CreateMessageRequest request, Authentication auth) {
		String email = auth != null ? auth.getPrincipal().toString() : request.getEmail().toLowerCase();
		if (captcha.isActivated()) {
			Mono<Long> count;
			if (auth == null) count = Mono.just(Long.MAX_VALUE);
			else count = repo.countByEmailAndSentAtGreaterThan(email, System.currentTimeMillis() - 60L * 60 * 1000);
			return count.flatMap(nb -> {
				if (nb < 3) return Mono.empty();
				if (request.getCaptcha() == null) return Mono.error(new ForbiddenException("captcha-needed"));
				return captcha.validate(request.getCaptcha()).flatMap(ok -> ok.booleanValue() ? Mono.empty() : Mono.error(new ForbiddenException()));
			}).thenReturn(email);
		}
		return Mono.just(email);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<Long> getUnreadCount() {
		return repo.countByIsRead(false);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	@Transactional
	public Mono<Void> markAsRead(List<String> uuids, boolean read) {
		return repo.findAllById(uuids.stream().map(UUID::fromString).toList())
		.flatMap(entity -> {
			entity.setRead(read);
			return repo.save(entity);
		})
		.then();
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<Void> deleteMessages(List<String> uuids) {
		return repo.deleteAllById(uuids.stream().map(UUID::fromString).toList());
	}
	
	private static final Map<String, Object> messageDtoFieldMapping = new HashMap<>();
	
	static {
		messageDtoFieldMapping.put("uuid", ContactMessageEntity.COL_UUID);
		messageDtoFieldMapping.put("email", ContactMessageEntity.COL_EMAIL);
		messageDtoFieldMapping.put("type", ContactMessageEntity.COL_MESSAGE_TYPE);
		messageDtoFieldMapping.put("message", ContactMessageEntity.COL_MESSAGE_TEXT);
		messageDtoFieldMapping.put("sentAt", ContactMessageEntity.COL_SENT_AT);
		messageDtoFieldMapping.put("read", ContactMessageEntity.COL_IS_READ);
	}
	
	@PreAuthorize(TrailenceUtils.PREAUTHORIZE_ADMIN)
	public Mono<PageResult<ContactMessage>> getMessages(Pageable pageable) {
		String sql = new SqlBuilder()
		.select(AsteriskFromTable.create(ContactMessageEntity.TABLE))
		.from(ContactMessageEntity.TABLE)
		.pageable(pageable, messageDtoFieldMapping)
		.build();
		return Mono.zip(
			r2dbc.query(DbUtils.operation(sql, null), this::toMessageDto).all().collectList().publishOn(Schedulers.parallel()),
			repo.count().publishOn(Schedulers.parallel())
		).map(result -> new PageResult<ContactMessage>(pageable, result.getT1(), result.getT2()));
	}
	
	private ContactMessage toMessageDto(Row row) {
		return new ContactMessage(
			row.get(ContactMessageEntity.COL_UUID.getName().toString(), UUID.class).toString(),
			row.get(ContactMessageEntity.COL_EMAIL.getName().toString(), String.class),
			row.get(ContactMessageEntity.COL_MESSAGE_TYPE.getName().toString(), String.class),
			row.get(ContactMessageEntity.COL_MESSAGE_TEXT.getName().toString(), String.class),
			row.get(ContactMessageEntity.COL_SENT_AT.getName().toString(), Long.class),
			row.get(ContactMessageEntity.COL_IS_READ.getName().toString(), Boolean.class)
		);
	}
	
}
