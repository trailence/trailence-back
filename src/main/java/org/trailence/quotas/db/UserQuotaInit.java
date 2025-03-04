package org.trailence.quotas.db;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SQL;
import org.trailence.global.TrailenceUtils;
import org.trailence.init.Migration;

public class UserQuotaInit implements Migration {
	
	private static final String QUERY_INSERT_USERS = "INSERT INTO user_quotas (email) (SELECT email FROM users)";
	private static final String QUERY_SUBSCRIBE_USERS_TO_FREE_PLAN =
		"INSERT INTO user_subscriptions "
		+ "(uuid, user_email, plan_name, starts_at, ends_at) "
		+ "(SELECT"
		+ " gen_random_uuid(), users.email, " + SQL.literalOf(TrailenceUtils.FREE_PLAN) + ", " + System.currentTimeMillis() + ", NULL"
		+ " FROM users"
		+ " LEFT JOIN user_subscriptions ON user_subscriptions.user_email = users.email AND user_subscriptions.plan_name = " + SQL.literalOf(TrailenceUtils.FREE_PLAN)
		+ " WHERE user_subscriptions.uuid IS NULL"
		+ ")";

	@Override
	public String id() {
		return "0.12_quotas";
	}
	
	@Override
	public void execute(R2dbcEntityTemplate db) throws Exception {
		db.getDatabaseClient().sql(QUERY_INSERT_USERS).fetch().rowsUpdated().block();
		db.getDatabaseClient().sql(QUERY_SUBSCRIBE_USERS_TO_FREE_PLAN).fetch().rowsUpdated().block();
	}
	
}
