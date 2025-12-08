package org.trailence.admin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.trailence.auth.AuthService;
import org.trailence.contact.ContactService;
import org.trailence.contact.db.ContactMessageEntity;
import org.trailence.email.EmailJob;
import org.trailence.email.EmailJob.Email;
import org.trailence.trail.ModerationService;
import org.trailence.user.UserService;
import org.trailence.user.db.UserEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminEmailService {
	
	private final ContactService contactService;
	private final UserService userService;
	private final AuthService authService;
	private final ModerationService moderationService;
	private final EmailJob emailJob;

	@Value("${trailence.hostname:trailence.org}")
	private String hostname;

	private long latestEmail = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
	
	private static final int MAX_CONTACTS = 10;
	private static final int MAX_NEW_USERS = 25;
	private static final int MAX_CONNECTED_USERS = 25;

	@Scheduled(initialDelayString = "15m", fixedDelayString = "24h")
	public void newsEmail() {
		long previousEmail = latestEmail;
		latestEmail = System.currentTimeMillis();
		
		Mono<Optional<String>> unreadContacts =
			contactService.getUnreadMessagesSince(previousEmail, MAX_CONTACTS)
			.map(this::getEmailPartFromUnreadContactMessages)
			.map(Optional::of)
			.switchIfEmpty(Mono.just(Optional.empty()));
		
		Mono<Optional<String>> newUsers =
			userService.getNewUsersSince(previousEmail, MAX_NEW_USERS)
			.map(this::getEmailPartFromNewUsers)
			.map(Optional::of)
			.switchIfEmpty(Mono.just(Optional.empty()));
		
		Mono<Optional<String>> connectedUsers =
			authService.getConnectedUsersSince(previousEmail, MAX_CONNECTED_USERS)
			.map(this::getEmailPartFromConnectedUsers)
			.map(Optional::of)
			.switchIfEmpty(Mono.just(Optional.empty()));
		
		Mono<Optional<String>> moderation =
			Mono.zip(
				moderationService.getNumberOfTrailsToReview(),
				moderationService.getNumberOfCommentsToReview(),
				moderationService.getNumberOfCommentRepliesToReview(),
				moderationService.getNumberOfRemovalRequestsToReview()
			)
			.map(this::getEmailPartFromModerationCounts);
		
		Flux.concat(unreadContacts, newUsers, connectedUsers, moderation)
		.collectList()
		.flatMap(emailParts -> {
			List<String> parts = emailParts.stream().filter(Optional::isPresent).map(Optional::get).toList();
			if (parts.isEmpty()) return Mono.empty();
			StringBuilder html = new StringBuilder(8192);
			html.append("News since ").append(Instant.ofEpochMilli(previousEmail).toString()).append(": <ul>");
			for (var part : parts) html.append("<li>").append(part).append("</li>");
			html.append("</ul>");
			return emailJob.send(new Email(emailJob.getFromTrailenceEmail(), "Trailence Report - " + hostname, "News", html.toString()), 99);
		}).subscribe();
	}
	
	@SuppressWarnings("deprecation")
	private String getEmailPartFromUnreadContactMessages(List<ContactMessageEntity> messages) {
		log.info("Unread messages: {}", messages.size());
		String nb = messages.size() >= MAX_CONTACTS ? MAX_CONTACTS + "+" : "" + messages.size();
		StringBuilder html = new StringBuilder(64 + messages.size() * 512);
		html.append(nb).append(" new contact message(s):<ul>");
		for (var m : messages) {
			html.append("<li>")
				.append(m.getEmail())
				.append(": [")
				.append(m.getMessageType())
				.append("] <span style=\"font-style:italic; color: #606060\">")
				.append(StringEscapeUtils.escapeHtml4(m.getMessageText().substring(0, Math.min(m.getMessageText().length(), 500))))
				.append("</span></li>");
		}
		html.append("</ul>");
		return html.toString();
	}
	
	private String getEmailPartFromNewUsers(List<UserEntity> users) {
		log.info("New users: {}", users.size());
		String nb = users.size() >= MAX_NEW_USERS ? MAX_NEW_USERS + "+" : "" + users.size();
		StringBuilder html = new StringBuilder(64 + users.size() * 80);
		html.append(nb).append(" new user(s):<ul>");
		for (var u : users) {
			html.append("<li>")
				.append(u.getEmail())
				.append("</li>");
		}
		html.append("</ul>");
		return html.toString();
	}
	
	private String getEmailPartFromConnectedUsers(List<String> users) {
		log.info("Connected users: {}", users.size());
		String nb = users.size() >= MAX_CONNECTED_USERS ? MAX_CONNECTED_USERS + "+" : "" + users.size();
		StringBuilder html = new StringBuilder(64 + users.size() * 80);
		html.append(nb).append(" user connection(s):<ul>");
		for (var u : users) {
			html.append("<li>")
				.append(u)
				.append("</li>");
		}
		html.append("</ul>");
		return html.toString();
	}
	
	private Optional<String> getEmailPartFromModerationCounts(Tuple4<Long, Long, Long, Long> counts) {
		log.info("Moderation: {} trails, {} comments, {} replies, {} removal requests", counts.getT1(), counts.getT2(), counts.getT3(), counts.getT4());
		if (counts.getT1().longValue() == 0L && counts.getT2().longValue() == 0L && counts.getT3().longValue() == 0L && counts.getT4().longValue() == 0L) return Optional.empty();
		StringBuilder html = new StringBuilder(512);
		html.append("Moderation pending:<ul>");
		if (counts.getT1().longValue() > 0L)
			html.append("<li>").append(counts.getT1()).append(" trail(s) to review</li>");
		if (counts.getT2().longValue() > 0L)
			html.append("<li>").append(counts.getT2()).append(" comment(s) to review</li>");
		if (counts.getT3().longValue() > 0L)
			html.append("<li>").append(counts.getT3()).append(" replies to review</li>");
		if (counts.getT4().longValue() > 0L)
			html.append("<li>").append(counts.getT4()).append(" removal request(s) to review</li>");
		html.append("</ul>");
		return Optional.of(html.toString());
	}
	
}
