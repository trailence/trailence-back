package org.trailence.init;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DatabaseMigration implements Migration {

	private final String id;
	
	@Override
	public String id() {
		return id;
	}
	
	@Override
	public void execute(R2dbcEntityTemplate db) throws Exception {
		try (InputStream in = DatabaseMigration.class.getClassLoader().getResourceAsStream("db_migrations/" + id + ".sql")) {
			String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			db.getDatabaseClient().sql(sql).then().block();
		}
	}
	
}
