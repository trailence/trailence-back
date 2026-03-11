package org.trailence.stats;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.stats.db.EventEntity;
import org.trailence.stats.db.EventRepository;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {
	
	private final EventRepository eventRepo;
	private final R2dbcEntityTemplate r2dbc;

	public Mono<Void> addEvent(EventType type, Object data) {
		EventEntity entity = new EventEntity();
		entity.setType(type.name());
		entity.setTimestamp(System.currentTimeMillis());
		Json json;
		try {
			json = Json.of(TrailenceUtils.mapper.writeValueAsString(data));
		} catch (JacksonException e) {
			log.error("Error converting event data", e);
			return Mono.empty();
		}
		entity.setData(json);
		return eventRepo.save(entity).then();
	}
	
	private static final String DAILY_SQL =
		"INSERT INTO daily_stats "
		+ "(date,nb_users,new_users,deleted_users,connected_users,"
		+ "active_users_30_30,active_users_60_45,active_users_90_45,active_users_180_45,"
		+ "inactive_users_30_30,inactive_users_60_45,inactive_users_90_45,inactive_users_180_45,"
		+ "nb_collections,nb_trails,nb_tracks,nb_tags,nb_trail_tags,nb_shares,nb_photos,nb_public_trails,nb_public_links,"
		+ "new_live_groups"
		+ ")"
		+ " SELECT"
		+ " DATE 'yesterday',"
		// nb_users
		+ " (SELECT COUNT(*) FROM users),"
		// new_users
		+ " (SELECT COUNT(*) FROM events WHERE timestamp >= $YESTERDAY AND timestamp < $TODAY AND type = 'NEW_USER'),"
		// deleted_users
		+ " (SELECT COUNT(*) FROM events WHERE timestamp >= $YESTERDAY AND timestamp < $TODAY AND type = 'DELETED_USER'),"
		// connected_users
		+ " (SELECT COUNT(*) FROM users u WHERE (SELECT 1 FROM user_keys k WHERE k.last_usage >= $YESTERDAY AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NOT NULL),"
		// users created since more than 30 days, connected in the past 30 days
		+ " (SELECT COUNT(*) FROM users u WHERE u.created_at < $SINCE30 AND (SELECT 1 FROM user_keys k WHERE k.last_usage >= $SINCE30 AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NOT NULL),"
		// users created since more than 60 days, connected in the past 45 days
		+ " (SELECT COUNT(*) FROM users u WHERE u.created_at < $SINCE60 AND (SELECT 1 FROM user_keys k WHERE k.last_usage >= $SINCE45 AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NOT NULL),"
		// users created since more than 90 days, connected in the past 45 days
		+ " (SELECT COUNT(*) FROM users u WHERE u.created_at < $SINCE90 AND (SELECT 1 FROM user_keys k WHERE k.last_usage >= $SINCE45 AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NOT NULL),"
		// users created since more than 180 days, connected in the past 45 days
		+ " (SELECT COUNT(*) FROM users u WHERE u.created_at < $SINCE180 AND (SELECT 1 FROM user_keys k WHERE k.last_usage >= $SINCE45 AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NOT NULL),"
		// users created since more than 30 days, not connected in the past 30 days
		+ " (SELECT COUNT(*) FROM users u WHERE u.created_at < $SINCE30 AND (SELECT 1 FROM user_keys k WHERE k.last_usage >= $SINCE30 AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NULL),"
		// users created since more than 60 days, not connected in the past 45 days
		+ " (SELECT COUNT(*) FROM users u WHERE u.created_at < $SINCE60 AND (SELECT 1 FROM user_keys k WHERE k.last_usage >= $SINCE45 AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NULL),"
		// users created since more than 90 days, not connected in the past 45 days
		+ " (SELECT COUNT(*) FROM users u WHERE u.created_at < $SINCE90 AND (SELECT 1 FROM user_keys k WHERE k.last_usage >= $SINCE45 AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NULL),"
		// users created since more than 180 days, not connected in the past 45 days
		+ " (SELECT COUNT(*) FROM users u WHERE u.created_at < $SINCE180 AND (SELECT 1 FROM user_keys k WHERE k.last_usage >= $SINCE45 AND k.last_usage < $TODAY AND k.email = u.email LIMIT 1) IS NULL),"
		// nb_collections
		+ " (SELECT COUNT(*) FROM collections WHERE type = 'CUSTOM'),"
		// nb_trails
		+ " (SELECT COUNT(*) FROM trails),"
		// nb_tracks
		+ " (SELECT COUNT(*) FROM tracks),"
		// nb_tags
		+ " (SELECT COUNT(*) FROM tags),"
		// nb_trail_tags
		+ " (SELECT COUNT(*) FROM trails_tags),"
		// nb_shares
		+ " (SELECT COUNT(*) FROM shares),"
		// nb_photos
		+ " (SELECT COUNT(*) FROM photos),"
		// nb_public_trails
		+ " (SELECT COUNT(*) FROM public_trails),"
		// nb_public_links
		+ " (SELECT COUNT(*) FROM trail_links),"
		// new_live_groups
		+ " (SELECT COUNT(*) FROM events WHERE timestamp >= $YESTERDAY AND timestamp < $TODAY AND type = 'NEW_LIVE_GROUP')"
		;
	
	@Scheduled(cron = "0 0 3 * * *")
	public void computeDailyStats() {
		Long today = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli();
		Long yesterday = today - 24L * 60 * 60 * 1000;
		Long since30 = today - 30L * 24 * 60 * 60 * 1000;
		Long since45 = today - 45L * 24 * 60 * 60 * 1000;
		Long since60 = today - 60L * 24 * 60 * 60 * 1000;
		Long since90 = today - 90L * 24 * 60 * 60 * 1000;
		Long since180 = today - 180L * 24 * 60 * 60 * 1000;
		String sql = DAILY_SQL
			.replace("$TODAY", today.toString())
			.replace("$YESTERDAY", yesterday.toString())
			.replace("$SINCE30", since30.toString())
			.replace("$SINCE45", since45.toString())
			.replace("$SINCE60", since60.toString())
			.replace("$SINCE90", since90.toString())
			.replace("$SINCE180", since180.toString())
			;
		log.info("Computing daily stats");
		long startTime = System.currentTimeMillis();
		r2dbc.getDatabaseClient().sql(sql).fetch().rowsUpdated().block();
		long totalTime = System.currentTimeMillis() - startTime;
		log.info("Daily stats computed in {} ms.", totalTime);
	}
	
}
