package org.trailence.init;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SQL;
import org.trailence.global.TrailenceUtils;
import org.trailence.init.migrations.AddLanguageAndTranslationsToPublicTrails;
import org.trailence.quotas.QuotaService;
import org.trailence.quotas.db.UserQuotaInit;
import org.trailence.trail.TrackStorage;
import org.trailence.user.UserService;

import lombok.extern.slf4j.Slf4j;
import reactor.util.function.Tuples;

@Slf4j
@SuppressWarnings("java:S6813") // use autowired instead of constructor
public class InitDB {

	@Autowired private R2dbcEntityTemplate db;
	@Autowired private UserService userService;
	@Autowired private FreePlanProperties freePlan;
	@Autowired private QuotaService quotaService;
	
	private static final String[] TABLES = {
		"users", "user_keys", "user_preferences", "user_extensions",
		"collections", "tracks", "trails", "tags", "trails_tags", "shares", "share_emails",
		"jobs_queue", "verification_codes", "files", "photos",
		"user_quotas", "user_subscriptions", "plans", "donations", "donation_goals",
		"contact_messages", "public_trails", "notifications", "moderation_messages",
		"public_trail_feedback", "public_trail_feedback_reply",
		"user_selection", "trail_links", "user_avatar", "live_groups",
		"migrations"
	};
	
	private static final Migration[] migrations = {
		new DatabaseMigration("0.7_shares_add_column_include_photos"),
		new DatabaseMigration("0.7_preferences_remove_elevation_unit"),
		new DatabaseMigration("0.7_preferences_add_photo_max"),
		new DatabaseMigration("0.9_verification_codes_add_column_invalid_attempts"),
		new DatabaseMigration("0.12_users_add_columns_is_admin_and_roles"),
		new DatabaseMigration("0.12_user_keys_add_columns_deleted_at_expires_after"),
		new UserQuotaInit(),
		new DatabaseMigration("0.13_shares"),
		new DatabaseMigration("0.13_jobs_queue_add_priority"),
		new DatabaseMigration("0.13_users_add_last_pasword_email"),
		new DatabaseMigration("0.17_trails_add_activity"),
		new DatabaseMigration("0.17_trails_add_source"),
		new DatabaseMigration("0.17_trails_add_date"),
		new DatabaseMigration("0.18_public_trails"),
		new DatabaseMigration("0.18_trails_add_followed"),
		new DatabaseMigration("0.18_preferences_add_alias"),
		new DatabaseMigration("0.18_trails_add_published_from"),
		new DatabaseMigration("0.18_public_trails_language"),
		new AddLanguageAndTranslationsToPublicTrails(),
		new DatabaseMigration("0.18_feedback_add_reviewed"),
		new DatabaseMigration("1.0_trails_add_publication_data"),
		new DatabaseMigration("1.0_public_trails_add_source_url"),
		new DatabaseMigration("1.0_trails_followed_uuid_varchar"),
		new DatabaseMigration("1.2_public_trails_search_text"),
		new DatabaseMigration("1.2_moderation_messages_add_type"),
		new DatabaseMigration("1.2_preferences_add_elevation_calibration"),
		new DatabaseMigration("1.3_trails_source_url"),
		new DatabaseMigration("1.3_preferences_add_trail_filters"),
		new DatabaseMigration("1.5_trust_token"),
	};
	
	public void init(ApplicationContext context) {
		for (var table : TABLES) createTable(table);
		doMigrations(context);
		log.info("Configuring free plan: {}", freePlan);
		setFreePlan();
		quotaService.computeQuotas().block();
		if (System.getenv("TRAILENCE_INIT_USER") != null && System.getenv("TRAILENCE_INIT_PASSWORD") != null)
			userService.createUser(System.getenv("TRAILENCE_INIT_USER"), System.getenv("TRAILENCE_INIT_PASSWORD"), true, List.of(Tuples.of(TrailenceUtils.FREE_PLAN, Optional.empty())))
					.onErrorComplete(DuplicateKeyException.class)
					.block();
		
		log.info("Test track v2:");
		Stats stats = new Stats();
		long start = System.currentTimeMillis();
		db.getDatabaseClient().sql("SELECT uuid,owner,data FROM tracks ORDER BY uuid,owner")
		.fetch()
		.all()
		.map(row -> {
			try {
				UUID uuid = (UUID) row.get("uuid");
				String owner = (String) row.get("owner");
				ByteBuffer bb = (ByteBuffer) row.get("data");
				byte[] data = new byte[bb.remaining()];
				bb.get(data);
				check(data, stats, "UUID " + uuid + " owner " + owner);
				return row;
			} catch (IOException e) {
				throw new RuntimeException("IO error", e);
			}
		})
		.count()
		.block();
		db.getDatabaseClient().sql("SELECT trail_uuid, data FROM public_tracks ORDER BY trail_uuid")
		.fetch()
		.all()
		.map(row -> {
			try {
				UUID uuid = (UUID) row.get("trail_uuid");
				ByteBuffer bb = (ByteBuffer) row.get("data");
				byte[] data = new byte[bb.remaining()];
				bb.get(data);
				check(data, stats, "Public track UUID " + uuid);
				return row;
			} catch (IOException e) {
				throw new RuntimeException("IO error", e);
			}
		})
		.count()
		.block();
		log.info("Total tracks: {} in {}", stats.nb.getValue(), System.currentTimeMillis() - start);
		log.info("V2 was better {} times, same {} times, worst {} times", stats.better.getValue(), stats.same.getValue(), stats.worst.size());
		for (String s : stats.worst) log.info("Worst: {}", s);
		log.info("V1 / V2 = {} / {} => {} saved using V2 = {}%",
			stats.v1TotalBytes.getValue(),
			stats.v2TotalBytes.getValue(),
			stats.v1TotalBytes.getValue().longValue() - stats.v2TotalBytes.getValue().longValue(),
			(stats.v1TotalBytes.getValue().longValue() - stats.v2TotalBytes.getValue().longValue()) * 100 / stats.v1TotalBytes.getValue()
		);
	}
	
	private static class Stats {
		MutableLong v1TotalBytes = new MutableLong(0L);
		MutableLong v2TotalBytes = new MutableLong(0L);
		MutableInt better = new MutableInt(0);
		MutableInt same = new MutableInt(0);
		Queue<String> worst = new ConcurrentLinkedQueue<>();
		MutableInt nb = new MutableInt(0);
	}
	
	private void check(byte[] data, Stats stats, String descr) throws IOException {
		var v1 = TrackStorage.V1.uncompress(data);
		var v2 = TrackStorage.V1V2Bridge.v1DtoToV2(v1);
		stats.v1TotalBytes.add(data.length);
		stats.v2TotalBytes.add(v2.length);
		if (v2.length < data.length) stats.better.increment();
		else if (v2.length == data.length) stats.same.increment();
		else stats.worst.add(descr + " v1 = " + data.length + " v2 = " + v2.length + " data: " + v1.wp.length + "wp = " + List.of(v1.wp) + " // " + List.of(v1.s));
		stats.nb.increment();
		// check
		var v1b = TrackStorage.V1V2Bridge.v2ToV1Dto(v2);
		if (v1.s.length != v1b.s.length) throw new RuntimeException(descr + ": Decoded " + v1b.s.length + " segments, but " + v1.s.length + " excepted");
		for (int s = 0; s < v1.s.length; ++s) {
			var s1 = v1.s[s];
			var s2 = v1b.s[s];
			if (s1.getP().length != s2.getP().length) throw new RuntimeException(descr + ": Decoded " + s2.getP().length + " points for segment " + s + ", but " + s1.getP().length + " excepted");
			for (int p = 0; p < s1.getP().length; ++p) {
				var p1 = s1.getP()[p];
				var p2 = s2.getP()[p];
				p1.setH(null);
				p1.setS(null);
				if (p1.getL() != null && p1.getL().longValue() == 0L) p1.setL(null);
				if (p1.getN() != null && p1.getN().longValue() == 0L) p1.setN(null);
				if (!p1.equals(p2))
					throw new RuntimeException(descr + ": Point " + s + "," + p + " is different: expected " + p1 + " found " + p2);
			}
		}
		if (v1.wp.length != v1b.wp.length) throw new RuntimeException(descr + ": Decoded " + v1b.wp.length + " waypoints, but " + v1.wp.length + " excepted");
		for (int wp = 0; wp < v1.wp.length; ++wp) {
			var wp1 = v1.wp[wp];
			var wp2 = v1b.wp[wp];
			if (!wp1.equals(wp2))
				throw new RuntimeException(descr + ": WayPoint " + wp + " is different: expected " + wp1 + " found " + wp2);
		}
	}
	
	private void createTable(String tableName) {
		log.info("Create table {}", tableName);
		try (InputStream in = InitDB.class.getClassLoader().getResourceAsStream("db_init/" + tableName + ".sql")) {
			String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			db.getDatabaseClient().sql(sql).then().block();
		} catch (Exception e) {
			log.error("Error creating table {}", tableName, e);
		}
	}
	
	@SuppressWarnings("java:S112") // RuntimeException
	private void doMigrations(ApplicationContext context) {
		log.info("Retrieving migrations...");
		List<String> done = db.getDatabaseClient().sql("SELECT id FROM migrations ORDER BY id").map((row,_) -> row.get(0, String.class)).all().collectList().block();
		List<Migration> todo = new LinkedList<>(Arrays.asList(migrations).stream().filter(m -> !done.contains(m.id())).toList());
		log.info("Migrations done: {}, to be executed: {}", done.size(), todo.size());
		for (var migration : todo) {
			try {
				log.info("Doing migration {}", migration.id());
				long start = System.currentTimeMillis();
				migration.execute(db, context);
				db.getDatabaseClient().sql("INSERT INTO migrations (id) VALUES ($1)").bind(0, migration.id()).then().block();
				log.info("Migration done: {} in {} ms.", migration.id(), System.currentTimeMillis() - start);
			} catch (Exception e) {
				log.error("Error doing migration {}", migration.id(), e);
				throw new RuntimeException("Migration error");
			}
		}
	}
	
	private void setFreePlan() {
		// create free plan or update it with current configuration
		db.getDatabaseClient().sql(
			"INSERT INTO plans (name,collections,trails,tracks,tracks_size,photos,photos_size,tags,trail_tags,shares) VALUES " +
			"(" + SQL.literalOf(TrailenceUtils.FREE_PLAN) + "," + freePlan.getCollections() + "," + freePlan.getTrails() + "," + freePlan.getTracks() + "," + freePlan.getTracksSize() +
			"," + freePlan.getPhotos() + "," + freePlan.getPhotosSize() + "," + freePlan.getTags() + "," + freePlan.getTrailTags() + "," + freePlan.getShares() +
			") ON CONFLICT (name) DO UPDATE SET " +
			"collections = " + freePlan.getCollections() + ",trails = " + freePlan.getTrails() +
			",tracks = " + freePlan.getTracks() + ",tracks_size = " + freePlan.getTracksSize() +
			",photos = " + freePlan.getPhotos() + ",photos_size = " + freePlan.getPhotosSize() +
			",tags = " + freePlan.getTags() + ",trail_tags = " + freePlan.getTrailTags() + ",shares = " + freePlan.getShares()
		).then().block();
	}
	
}
