package org.trailence.init;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.trailence.user.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InitDB {

	@Autowired private R2dbcEntityTemplate db;
	@Autowired private UserService userService;
	
	private static final String[] TABLES = {
		"users", "user_keys", "user_preferences", "user_extensions",
		"collections", "tracks", "trails", "tags", "trails_tags", "shares",
		"jobs_queue", "verification_codes",
		"files", "photos",
		"migrations"
	};
	
	private static final Migration[] migrations = {
		new DatabaseMigration("0.7_shares_add_column_include_photos")
	};
	
	public void init() {
		for (var table : TABLES) createTable(table);
		doMigrations();
		if (System.getenv("TRAILENCE_INIT_USER") != null && System.getenv("TRAILENCE_INIT_PASSWORD") != null)
			userService.createUser(System.getenv("TRAILENCE_INIT_USER"), System.getenv("TRAILENCE_INIT_PASSWORD"))
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
	
	private void doMigrations() {
		log.info("Retrieving migrations...");
		List<String> done = db.getDatabaseClient().sql("SELECT id FROM migrations ORDER BY id").map((row,meta) -> row.get(0, String.class)).all().collectList().block();
		List<Migration> todo = new LinkedList<>(Arrays.asList(migrations).stream().filter(m -> !done.contains(m.id())).toList());
		log.info("Migrations done: {}, to be executed: {}", done.size(), todo.size());
		todo.sort((m1, m2) -> m1.id().compareToIgnoreCase(m2.id()));
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
