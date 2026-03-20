package org.trailence.trail;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.r2dbc.core.binding.MutableBindings;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.trailence.global.TrailenceUtils;
import org.trailence.global.db.DbUtils;
import org.trailence.global.exceptions.ForbiddenException;
import org.trailence.global.exceptions.NotFoundException;
import org.trailence.notifications.NotificationsService;
import org.trailence.preferences.UserCommunityService;
import org.trailence.trail.db.PublicTrailEntity;
import org.trailence.trail.db.PublicTrailFeedbackEntity;
import org.trailence.trail.db.PublicTrailFeedbackReplyEntity;
import org.trailence.trail.db.PublicTrailFeedbackReplyRepository;
import org.trailence.trail.db.PublicTrailFeedbackRepository;
import org.trailence.trail.db.PublicTrailRepository;
import org.trailence.trail.dto.CreateFeedbackRequest;
import org.trailence.trail.dto.MyFeedback;
import org.trailence.trail.dto.PublicTrailFeedback;
import org.trailence.trail.dto.PublicTrailFeedback.Reply;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FeedbackService {

	private final PublicTrailFeedbackRepository feedbackRepo;
	private final PublicTrailFeedbackReplyRepository feedbackReplyRepo;
	private final PublicTrailRepository publicTrailRepo;
	private final R2dbcEntityTemplate r2dbc;
	private final NotificationsService notificationsService;
	private final UserCommunityService userCommunityService;
	
	@Transactional
	@SuppressWarnings("java:S3776")
	public Mono<Void> createFeedback(CreateFeedbackRequest request, Authentication auth) {
		long date = System.currentTimeMillis();
		String email = TrailenceUtils.email(auth);
		UUID trailUuid = UUID.fromString(request.getTrailUuid());
		
		String comment = request.getComment();
		if (comment != null) {
			comment = comment.trim();
			if (comment.isEmpty()) comment = null;
		}
		Integer rate = request.getRate();
		if (rate != null && (rate.intValue() < 0 || rate.intValue() > 5)) rate = null;
		
		if (comment == null && rate == null) return Mono.empty();
		
		String c = comment;
		Integer r = rate;
		
		return publicTrailRepo.getAuthorAndName(trailUuid)
		.flatMap(authorAndName -> {
			if (authorAndName.getAuthor().equals(email)) return Mono.empty();
			Mono<Optional<PublicTrailFeedbackEntity>> existingRate = r == null ? Mono.just(Optional.empty()) :
				feedbackRepo.findOneByPublicTrailUuidAndEmailAndRateIsNotNull(trailUuid, email).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()));
			return existingRate.flatMap(existingRateOpt -> {
				if (existingRateOpt.isPresent()) {
					PublicTrailFeedbackEntity entity = existingRateOpt.get();
					Mono<Void> update = entity.getRate().intValue() != r.intValue() ? this.updateRate(entity, r) : Mono.empty();
					return update
					.then(Mono.defer(() -> {
						if (c == null) return Mono.empty();
						return this.createFeedback(trailUuid, email, date, null, c, authorAndName.getAuthor(), authorAndName.getName())
						.then(userCommunityService.addComment(email, true, false));
					}));
				}
				return this.createFeedback(trailUuid, email, date, r, c, authorAndName.getAuthor(), authorAndName.getName())
				.then(userCommunityService.addComment(email, c != null, r != null));
			});
		});
	}
	
	@SuppressWarnings("java:S3358")
	private Mono<Void> createFeedback(UUID trailUuid, String email, long date, Integer rate, String comment, String trailAuthor, String trailName) {
		PublicTrailFeedbackEntity entity = new PublicTrailFeedbackEntity(
			UUID.randomUUID(),
			trailUuid,
			email,
			date,
			rate,
			comment,
			comment == null // reviewed if no comment
		);
		return r2dbc.insert(entity)
		.flatMap(feedback -> {
			if (feedback.getRate() == null) return Mono.empty();
			return addRateToTrail(trailUuid, feedback.getRate());
		})
		.then(Mono.defer(() -> 
			notificationsService.create(trailAuthor, "comments.someone_leave_" + (rate != null ? (comment != null ? "a_rate_with_comment" : "a_rate") : "a_comment"), List.of(trailUuid.toString(), trailName))
		));
	}
	
	private Mono<Void> addRateToTrail(UUID trailUuid, int rate) {
		String col = PublicTrailEntity.COL_NB_RATE_PREFIX + rate;
		String sql = "UPDATE public_trails SET " + col + " = " + col + " + 1 WHERE uuid = '" + trailUuid.toString() + "'";
		return r2dbc.getDatabaseClient().sql(sql).fetch().rowsUpdated().then();
	}
	
	private Mono<Void> updateRate(PublicTrailFeedbackEntity entity, int newRate) {
		String col1 = PublicTrailEntity.COL_NB_RATE_PREFIX + entity.getRate().intValue();
		String col2 = PublicTrailEntity.COL_NB_RATE_PREFIX + newRate;
		return r2dbc.getDatabaseClient().sql("UPDATE public_trail_feedback SET rate = " + newRate + " WHERE uuid = '" + entity.getUuid().toString() + "'").fetch().rowsUpdated()
		.then(r2dbc.getDatabaseClient().sql("UPDATE public_trails SET " + col1 + " = " + col1 + " - 1, " + col2 + " = " + col2 + " + 1 WHERE uuid = '" + entity.getPublicTrailUuid().toString() + "'").fetch().rowsUpdated().then());
	}
	
	public Mono<PublicTrailFeedback.Reply> replyToFeedback(String feedbackUuid, String reply, Authentication auth) {
		long date = System.currentTimeMillis();
		String email = TrailenceUtils.email(auth);
		UUID uuid = UUID.fromString(feedbackUuid);
		if (reply == null) return Mono.empty();
		String comment = reply.trim();
		if (comment.isEmpty()) return Mono.empty();
		String c = comment;
		return feedbackRepo.findById(uuid)
		.switchIfEmpty(Mono.error(new NotFoundException("feedback", feedbackUuid)))
		.flatMap(feedback -> 
			r2dbc.insert(new PublicTrailFeedbackReplyEntity(UUID.randomUUID(), uuid, email, date, c, false))
			.doOnNext(_ -> notifyUsersForCommentReply(feedback, email))
		)
		.flatMap(entity ->
			userCommunityService.getUserCommunity(email)
			.map(userCommunity -> {
				String alias = userCommunity.getAlias();
				if (alias != null && alias.isBlank()) alias = null;
				return new PublicTrailFeedback.Reply(entity.getUuid().toString(), alias, userCommunity.getAvatar(), true, entity.getDate(), c, entity.isReviewed());
			})
		);
	}
	
	private void notifyUsersForCommentReply(PublicTrailFeedbackEntity feedback, String email) {
		feedbackReplyRepo.findAllByReplyTo(feedback.getUuid())
		.map(re -> re.getEmail())
		.distinct()
		.collectList()
		.flatMap(users -> {
			Set<String> recipients = new HashSet<>();
			if (!email.equals(feedback.getEmail())) recipients.add(feedback.getEmail());
			for (var u : users) if (!u.equals(email)) recipients.add(u);
			if (recipients.isEmpty()) return Mono.empty();
			return publicTrailRepo.findById(feedback.getPublicTrailUuid())
			.flatMap(trail ->
				Flux.fromIterable(recipients)
				.flatMap(recipient -> notificationsService.create(recipient, "comments.reply", List.of(feedback.getPublicTrailUuid().toString(), trail.getName())), 1, 1)
				.then()
			);
		})
		.subscribe();		
	}
	
	public Mono<MyFeedback> getMyFeedback(String trailUuid, Authentication auth) {
		String email = TrailenceUtils.email(auth);
		Optional<UUID> isUuid = TrailenceUtils.ifUuid(trailUuid);
		return (isUuid.isPresent() ? Mono.just(isUuid.get()) : publicTrailRepo.findOneBySlug(trailUuid).map(e -> e.getUuid()))
		.flatMap(uuid -> 
			feedbackRepo.findOneByPublicTrailUuidAndEmailAndRateIsNotNull(uuid, email)
			.map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
			.flatMap(myRateOpt ->
				feedbackRepo.findFirst1ByPublicTrailUuidAndEmailAndCommentIsNotNullOrderByDateDesc(uuid, email)
				.map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
				.map(myLatestCommentOpt -> new MyFeedback(
					myRateOpt.isPresent() ? myRateOpt.get().getRate() : null,
					myRateOpt.isPresent() ? myRateOpt.get().getDate() : null,
					myLatestCommentOpt.isPresent() ? myLatestCommentOpt.get().getDate() : null
				))
			)
		);
	}
	
	public Mono<List<PublicTrailFeedback>> getMyFeedbacks(Authentication auth) {
		String email = TrailenceUtils.email(auth);
		return feedbackRepo.findAllByEmail(email)
		.map(entity -> new PublicTrailFeedback(
			entity.getUuid().toString(),
			entity.getPublicTrailUuid().toString(),
			null, null, true,
			entity.getDate(),
			entity.getRate(),
			entity.getComment(),
			entity.isReviewed(),
			new LinkedList<>()
		)).collectList();
	}
	
	public Mono<List<PublicTrailFeedback>> getFeedbacks(String trailUuid, long pageFromDate, int size, String excludeFromStartingDate, Integer filterRate, Authentication auth) {
		return this.fetchFeedbacks(trailUuid, (sql, _) -> {
			if (pageFromDate > 0) {
				sql.append(" AND public_trail_feedback.date <= ").append(pageFromDate);
			}
			if (filterRate != null) {
				sql.append(" AND public_trail_feedback.rate = ").append(filterRate);
			}
			if (excludeFromStartingDate != null) {
				String notIn = String.join(",",
					Stream.of(excludeFromStartingDate.split(","))
					.map(TrailenceUtils::ifUuid).filter(o -> o.isPresent())
					.map(Optional::get)
					.map(uuid -> "'" + uuid.toString() + "'").toList()
				);
				if (!notIn.isEmpty())
					sql.append(" AND public_trail_feedback.uuid NOT IN (").append(notIn).append(')');
			}
			sql.append(" ORDER BY public_trail_feedback.date DESC");
			sql.append(" LIMIT ").append(size > 100 || size < 1 ? 100 : size);
		}, auth);
	}
	
	public Mono<List<PublicTrailFeedback>> fetchFeedbacks(String trailUuid, BiConsumer<StringBuilder, MutableBindings> addWhereAndPaging, Authentication auth) {
		String youEmail = auth == null ? "" : TrailenceUtils.email(auth);
		
		var dialect = DialectResolver.getDialect(r2dbc.getDatabaseClient().getConnectionFactory());
		MutableBindings bindings = new MutableBindings(dialect.getBindMarkersFactory().create());
		var markerTrailUuid = bindings.bind(UUID.fromString(trailUuid));

		String avatarCondition;
		if (youEmail.isEmpty()) avatarCondition = "";
		else {
			var marker = bindings.bind(youEmail);
			avatarCondition = "public_trail_feedback.email <> " + marker.getPlaceholder() + " AND ";
		}
		
		var sql = new StringBuilder(2048)
		.append("SELECT ")
		.append("public_trail_feedback.uuid")
		.append(",public_trail_feedback.date")
		.append(",public_trail_feedback.rate")
		.append(",public_trail_feedback.comment")
		.append(",public_trail_feedback.reviewed")
		.append(",user_preferences.alias")
		.append(",user_avatar.public_uuid AS avatar_uuid")
		.append(",public_trail_feedback.email")
		.append(" FROM public_trail_feedback")
		.append(" LEFT JOIN user_preferences ON user_preferences.email = public_trail_feedback.email")
		.append(" LEFT JOIN user_avatar ON ").append(avatarCondition).append("user_avatar.email = public_trail_feedback.email AND user_avatar.current_file_id IS NOT NULL AND user_avatar.current_public = true")
		.append(" WHERE public_trail_feedback.public_trail_uuid = ").append(markerTrailUuid.getPlaceholder());
		addWhereAndPaging.accept(sql, bindings);
		return r2dbc.query(DbUtils.operation(sql.toString(), bindings), row -> new PublicTrailFeedback(
			row.get("uuid", UUID.class).toString(),
			trailUuid,
			row.get("alias", String.class),
			optionalUuidToString(row.get("avatar_uuid", UUID.class)),
			youEmail.equals(row.get("email", String.class)),
			row.get("date", Long.class),
			row.get("rate", Integer.class),
			row.get("comment", String.class),
			row.get("reviewed", Boolean.class),
			new LinkedList<>()
		)).all().collectList()
		.flatMap(feedbacks -> {
			if (feedbacks.isEmpty()) return Mono.just(feedbacks);
			MutableBindings bindings2 = null;
			String avatarCondition2;
			if (youEmail.isEmpty()) avatarCondition2 = "";
			else {
				bindings2 = new MutableBindings(dialect.getBindMarkersFactory().create());
				var marker = bindings2.bind(youEmail);
				avatarCondition2 = "public_trail_feedback_reply.email <> " + marker.getPlaceholder() + " AND ";
			}
			
			var sqlReplies = new StringBuilder(1024)
			.append("SELECT ")
			.append("public_trail_feedback_reply.reply_to")
			.append(",public_trail_feedback_reply.uuid")
			.append(",user_preferences.alias")
			.append(",user_avatar.public_uuid AS avatar_uuid")
			.append(",public_trail_feedback_reply.date")
			.append(",public_trail_feedback_reply.comment")
			.append(",public_trail_feedback_reply.email")
			.append(",public_trail_feedback_reply.reviewed")
			.append(" FROM public_trail_feedback_reply")
			.append(" LEFT JOIN user_preferences ON user_preferences.email = public_trail_feedback_reply.email")
			.append(" LEFT JOIN user_avatar ON ").append(avatarCondition2).append("user_avatar.email = public_trail_feedback_reply.email AND user_avatar.current_file_id IS NOT NULL AND user_avatar.current_public = true")
			.append(" WHERE public_trail_feedback_reply.reply_to IN (")
				.append(String.join(",", feedbacks.stream().map(f -> "'" + f.getUuid() + "'").collect(Collectors.toSet())))
			.append(") ORDER BY public_trail_feedback_reply.date DESC").toString();
			return r2dbc.query(DbUtils.operation(sqlReplies, bindings2), row -> new TmpReply(
				row.get("reply_to", UUID.class).toString(),
				row.get("uuid", UUID.class).toString(),
				row.get("alias", String.class),
				optionalUuidToString(row.get("avatar_uuid", UUID.class)),
				youEmail.equals(row.get("email", String.class)),
				row.get("date", Long.class),
				row.get("comment", String.class),
				row.get("reviewed", Boolean.class)
			)).all().collectList()
			.map(replies -> {
				for (var reply : replies) {
					var feedbackOpt = feedbacks.stream().filter(f -> f.getUuid().equals(reply.getCommentUuid())).findAny();
					if (feedbackOpt.isPresent())
						feedbackOpt.get().getReplies().add(new Reply(reply.getUuid(), reply.getAlias(), reply.getAvatarUuid(), reply.isYou(), reply.getDate(), reply.getComment(), reply.isReviewed()));
				}
				return feedbacks;
			});
		});
	}
	
	private String optionalUuidToString(UUID uuid) {
		return uuid == null ? null : uuid.toString();
	}
	
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class TmpReply {
		private String commentUuid;
		private String uuid;
		private String alias;
		private String avatarUuid;
		private boolean you;
		private long date;
		private String comment;
		private boolean reviewed;
	}
	
	@Transactional
	public Mono<Void> deleteComment(String feedbackUuid, Authentication auth) {
		String user = auth == null ? "" : TrailenceUtils.email(auth);
		boolean moderator = TrailenceUtils.hasRole(auth, TrailenceUtils.ROLE_MODERATOR);
		return feedbackRepo.findById(UUID.fromString(feedbackUuid))
		.switchIfEmpty(Mono.error(new NotFoundException("feedback", feedbackUuid)))
		.flatMap(entity -> {
			if (!moderator && !entity.getEmail().equals(user)) return Mono.error(new ForbiddenException());
			if (entity.getComment() == null) return Mono.empty();
			if (entity.getRate() != null) {
				entity.setComment(null);
				return feedbackRepo.save(entity)
				.flatMap(e -> userCommunityService.removeComment(entity.getEmail(), true, false).thenReturn(e));
			} else {
				return feedbackRepo.delete(entity)
				.then(userCommunityService.removeComment(entity.getEmail(), true, false))
				.thenReturn(entity);
			}
		})
		.flatMap(entity -> feedbackReplyRepo.deleteAllByReplyTo(entity.getUuid()));
	}
	
	public Mono<Void> deleteReply(String uuid, Authentication auth) {
		String user = auth == null ? "" : TrailenceUtils.email(auth);
		boolean moderator = TrailenceUtils.hasRole(auth, TrailenceUtils.ROLE_MODERATOR);
		return feedbackReplyRepo.findById(UUID.fromString(uuid))
		.switchIfEmpty(Mono.error(new NotFoundException("feedback-reply", uuid)))
		.flatMap(entity -> {
			if (!moderator && !entity.getEmail().equals(user)) return Mono.error(new ForbiddenException());
			return feedbackReplyRepo.deleteById(entity.getUuid());
		});
	}
	
	Mono<Void> publicTrailDeleted(UUID trailUuid) {
		return feedbackRepo.findAllByPublicTrailUuid(trailUuid)
		.flatMap(comment ->
			feedbackReplyRepo.deleteAllByReplyTo(comment.getUuid())
			.then(feedbackRepo.delete(comment))
			.then(userCommunityService.removeComment(comment.getEmail(), comment.getComment() != null, comment.getRate() != null))
		, 1, 1).then();
	}
	
}
