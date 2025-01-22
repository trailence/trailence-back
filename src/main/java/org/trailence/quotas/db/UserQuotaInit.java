package org.trailence.quotas.db;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.trailence.init.Migration;

public class UserQuotaInit implements Migration {
	
	private static final String QUERY_INSERT_USERS = "INSERT INTO user_quotas (email) (SELECT email FROM users)";
	public static final String QUERY_COMPUTE_USAGE =
		"UPDATE user_quotas SET "
		+ "collections_used = (SELECT count(*) FROM collections WHERE collections.owner = user_quotas.email),"
		+ "trails_used = (SELECT count(*) FROM trails WHERE trails.owner = user_quotas.email),"
		+ "tracks_used = (SELECT count(*) FROM tracks WHERE tracks.owner = user_quotas.email),"
		+ "tracks_size_used = (SELECT COALESCE(sum(octet_length(tracks.data)), 0) FROM tracks WHERE tracks.owner = user_quotas.email),"
		+ "photos_used = (SELECT count(*) FROM photos WHERE photos.owner = user_quotas.email),"
		+ "photos_size_used = (SELECT COALESCE(sum(files.size),0) FROM photos left join files on files.id = photos.file_id WHERE photos.owner = user_quotas.email),"
		+ "tags_used = (SELECT count(*) FROM tags WHERE tags.owner = user_quotas.email),"
		+ "trail_tags_used = (SELECT count(*) FROM trails_tags WHERE trails_tags.owner = user_quotas.email),"
		+ "shares_used = (SELECT count(*) FROM shares WHERE shares.from_email = user_quotas.email)";

	@Override
	public String id() {
		return "0.12_quotas";
	}
	
	@Override
	public void execute(R2dbcEntityTemplate db) throws Exception {
		db.getDatabaseClient().sql(QUERY_INSERT_USERS).fetch().rowsUpdated().block();
		db.getDatabaseClient().sql(QUERY_COMPUTE_USAGE).fetch().rowsUpdated().block();
	}
	
}
