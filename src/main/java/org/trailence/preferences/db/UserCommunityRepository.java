package org.trailence.preferences.db;

import java.util.List;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.trailence.preferences.dto.UserCommunity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserCommunityRepository extends ReactiveCrudRepository<UserCommunityEntity, String> {

	@Modifying
	@Query("INSERT INTO user_community (email,nb_publications) VALUES (:email,1) ON CONFLICT (email) DO UPDATE SET nb_publications = nb_publications + 1")
	Mono<Void> addPublication(String email);
	
	@Modifying
	@Query("UPDATE user_community SET nb_publications = nb_publications - 1 WHERE email = :email")
	Mono<Void> removePublication(String email);

	@Modifying
	@Query("INSERT INTO user_community (email,nb_comments,nb_rates) VALUES (:email,:nbComments,:nbRates) ON CONFLICT (email) DO UPDATE SET nb_comments = nb_comments + :nbComments, nb_rates = nb_rates + :nbRates")
	Mono<Void> addCommentRate(String email, int nbComments, int nbRates);
	
	@Modifying
	@Query("UPDATE user_community SET nb_comments = nb_comments - :nbComments, nb_rates = nb_rates - :nbRates WHERE email = :email")
	Mono<Void> removeCommentRate(String email, int nbComments, int nbRates);
	
	@Query("SELECT pref.email as email, c.public_uuid as public_id, pref.alias as alias, avatar.public_uuid as avatar, COALESCE(c.nb_publications, 0) as nb_publications, COALESCE(c.nb_comments, 0) as nb_comments, COALESCE(c.nb_rates, 0) as nb_rates FROM user_preferences pref LEFT JOIN user_avatar avatar ON avatar.email = pref.email AND avatar.current_file_id IS NOT NULL AND avatar.current_public = TRUE LEFT JOIN user_community c ON c.email = pref.email WHERE pref.email = :email")
	Mono<UserCommunity> getUserCommunity(String email);

	@Query("SELECT pref.email as email, c.public_uuid as public_id, pref.alias as alias, avatar.public_uuid as avatar, COALESCE(c.nb_publications, 0) as nb_publications, COALESCE(c.nb_comments, 0) as nb_comments, COALESCE(c.nb_rates, 0) as nb_rates FROM user_preferences pref LEFT JOIN user_avatar avatar ON avatar.email = pref.email AND avatar.current_file_id IS NOT NULL AND avatar.current_public = TRUE LEFT JOIN user_community c ON c.email = pref.email WHERE pref.email IN (:emails)")
	Flux<UserCommunity> getUsersCommunity(List<String> emails);
	
	@Query("SELECT c.email as email, c.public_uuid as public_id, pref.alias as alias, avatar.public_uuid as avatar, c.nb_publications as nb_publications, c.nb_comments as nb_comments, c.nb_rates as nb_rates FROM user_community c LEFT JOIN user_preferences pref ON pref.email = c.email LEFT JOIN user_avatar avatar ON avatar.email = pref.email AND avatar.current_file_id IS NOT NULL AND avatar.current_public = TRUE WHERE c.public_uuid = :publicUuid")
	Mono<UserCommunity> getUserCommunityFromPublicUuId(UUID publicUuid);
}
