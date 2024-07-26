package org.trailence.init;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
		"jobs_queue", "verification_codes"
	};
	
	public void init() {
		for (var table : TABLES) createTable(table);
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
	
}
