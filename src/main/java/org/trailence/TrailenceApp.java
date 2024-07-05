package org.trailence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.trailence.init.InitDB;

@SpringBootApplication
@ComponentScan
public class TrailenceApp {

	public static void main(String[] args) {
		SpringApplication.run(TrailenceApp.class, args);
	}
	
	@EventListener
	public void onApplicationReady(ApplicationReadyEvent event) {
		InitDB init = new InitDB();
		event.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(init);
		init.init();
	}
	
}
