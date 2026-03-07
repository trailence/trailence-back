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
		return repo.getUserCommunity(email);
	}
	
	public Flux<UserCommunity> getUsersCommunity(List<String> emails) {
		return repo.getUsersCommunity(emails);
	}
	
	public Mono<UserCommunity> getUserCommunityFromPublicId(String publicId) {
		return repo.getUserCommunityFromPublicUuId(UUID.fromString(publicId));
	}

	private static final String QUERY_COMPUTE =
		"INSERT INTO user_community (email,nb_publications,nb_comments,nb_rates)"
		+ " SELECT u.email, COUNT(pt.uuid) as nb_publications, COUNT(ptfr.uuid) as nb_rates, COUNT(ptfc.uuid) as nb_comments"
		+ " FROM users u"
		+ " LEFT JOIN public_trails pt ON pt.author = u.email"
		+ " LEFT JOIN public_trail_feedback ptfr ON ptfr.email = u.email AND ptfr.rate IS NOT NULL"
		+ " LEFT JOIN public_trail_feedback ptfc ON ptfc.email = u.email AND ptfc.comment IS NOT NULL"
		+ " GROUP BY u.email"
		+ " HAVING COUNT(pt.uuid) > 0 OR COUNT(ptfr.uuid) > 0 OR COUNT(ptfc.uuid) > 0"
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
