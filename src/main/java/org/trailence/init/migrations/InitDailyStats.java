package org.trailence.init.migrations;

import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.trailence.init.Migration;
import org.trailence.stats.StatsService;

public class InitDailyStats implements Migration {

	@Override
	public String id() {
		return "1.5_first_daily_stats";
	}
	
	@Override
	public void execute(R2dbcEntityTemplate db, ApplicationContext context) throws Exception {
		context.getBean(StatsService.class).computeDailyStats();
	}
	
}
