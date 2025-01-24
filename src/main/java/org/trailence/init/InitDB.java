package org.trailence.init;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.trailence.quotas.db.UserQuotaInit;
import org.trailence.user.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("java:S6813") // use autowired instead of constructor
public class InitDB {

	@Autowired private R2dbcEntityTemplate db;
	@Autowired private UserService userService;
	
	private static final String[] TABLES = {
		"users", "user_keys", "user_preferences", "user_extensions",
		"collections", "tracks", "trails", "tags", "trails_tags", "shares",
		"jobs_queue", "verification_codes",
		"files", "photos",
		"user_quotas",
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
	};
	
	public void init() {
		for (var table : TABLES) createTable(table);
		doMigrations();
		if (System.getenv("TRAILENCE_INIT_USER") != null && System.getenv("TRAILENCE_INIT_PASSWORD") != null)
			userService.createUser(System.getenv("TRAILENCE_INIT_USER"), System.getenv("TRAILENCE_INIT_PASSWORD"), true)
					.onErrorComplete(DuplicateKeyException.class)
					.block();
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
	private void doMigrations() {
		log.info("Retrieving migrations...");
		List<String> done = db.getDatabaseClient().sql("SELECT id FROM migrations ORDER BY id").map((row,meta) -> row.get(0, String.class)).all().collectList().block();
		List<Migration> todo = new LinkedList<>(Arrays.asList(migrations).stream().filter(m -> !done.contains(m.id())).toList());
		log.info("Migrations done: {}, to be executed: {}", done.size(), todo.size());
		for (var migration : todo) {
			try {
				log.info("Doing migration {}", migration.id());
				migration.execute(db);
				db.getDatabaseClient().sql("INSERT INTO migrations (id) VALUES ($1)").bind(0, migration.id()).then().block();
				log.info("Migration done: {}", migration.id());
			} catch (Exception e) {
				log.error("Error doing migration {}", migration.id(), e);
				throw new RuntimeException("Migration error");
			}
		}
	}
	
}
