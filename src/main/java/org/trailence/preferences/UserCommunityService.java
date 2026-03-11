package org.trailence.preferences;

import java.util.List;
import java.util.UUID;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.trailence.preferences.db.UserCommunityRepository;
import org.trailence.preferences.dto.UserCommunity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCommunityService {
	
	private final R2dbcEntityTemplate r2dbc;
	private final UserCommunityRepository repo;
	
	public Mono<Void> addPublication(String email) {
		return repo.addPublication(email);
	}
	
	public Mono<Void> removePublication(String email) {
		return repo.removePublication(email);
	}
	
	public Mono<Void> addComment(String email, boolean withComment, boolean withRate) {
		return repo.addCommentRate(email, withComment ? 1 : 0, withRate ? 1 : 0);
	}
	
	public Mono<Void> removeComment(String email, boolean withComment, boolean withRate) {
		return repo.removeCommentRate(email, withComment ? 1 : 0, withRate ? 1 : 0);
	}
	
	public Mono<UserCommunity> getUserCommunity(String email) {
		return repo.getUserCommunity(email).switchIfEmpty(Mono.defer(() -> Mono.just(new UserCommunity(email, null, null, null, null, null, null))));
	}
	
	public Flux<UserCommunity> getUsersCommunity(List<String> emails) {
		return repo.getUsersCommunity(emails);
	}
	
	public Mono<UserCommunity> getUserCommunityFromPublicId(String publicId) {
		return repo.getUserCommunityFromPublicUuId(UUID.fromString(publicId));
	}

	private static final String QUERY_COMPUTE =
		"WITH allUsers AS ("
		+ "SELECT"
		+ "  u.email,"
		+ "  (SELECT count(*) FROM public_trails pt WHERE pt.author = u.email) as nb_publications,"
		+ "  (SELECT count(*) FROM public_trail_feedback f WHERE f.email = u.email AND f.comment IS NOT NULL) as nb_comments"
		+ "  (SELECT count(*) FROM public_trail_feedback f WHERE f.email = u.email AND f.rate IS NOT NULL) as nb_rates,"
		+ " FROM users u"
		+ ")"
		+ "INSERT INTO user_community (email,nb_publications,nb_comments,nb_rates)"
		+ " SELECT * FROM allUsers WHERE nb_publications > 0 OR nb_rates > 0 OR nb_comments > 0"
		+ " ON CONFLICT (email) DO UPDATE"
		+ " SET nb_publications = EXCLUDED.nb_publications, nb_comments = EXCLUDED.nb_comments, nb_rates = EXCLUDED.nb_rates";
	
	@Scheduled(initialDelayString = "1d", fixedDelayString = "1d")
	public void computeUserCommunity() {
		log.info("Computing user community counts");
		long startTime = System.currentTimeMillis();
		long nbUpdates = r2dbc.getDatabaseClient().sql(QUERY_COMPUTE).fetch().rowsUpdated().block();
		long totalTime = System.currentTimeMillis() - startTime;
		log.info("User community counts computed in {} ms. ({})", totalTime, nbUpdates);
	}
	
}
