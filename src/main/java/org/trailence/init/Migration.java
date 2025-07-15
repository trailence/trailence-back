package org.trailence.init;

import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

public interface Migration {

	String id();
	
	@SuppressWarnings("java:S112") // generic Exception: this is a generic interface
	void execute(R2dbcEntityTemplate db, ApplicationContext context) throws Exception;
	
}
