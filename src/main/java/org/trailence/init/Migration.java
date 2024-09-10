package org.trailence.init;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

public interface Migration {

	String id();
	
	void execute(R2dbcEntityTemplate db) throws Exception;
	
}
