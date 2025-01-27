package org.trailence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.trailence.captcha.CaptchaService;
import org.trailence.external.geonames.GeonamesService;
import org.trailence.external.outdooractive.OutdoorActiveService;
import org.trailence.init.InitDB;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableScheduling
@ComponentScan
@Slf4j
public class TrailenceApp {

	public static void main(String[] args) {
		SpringApplication.run(TrailenceApp.class, args);
	}
	
	@EventListener
	public void onApplicationReady(ApplicationReadyEvent event) {
		InitDB init = new InitDB();
		event.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(init);
		init.init();
		checks(event.getApplicationContext());
	}
	
	private void checks(ConfigurableApplicationContext ctx) {
		if (ctx.getBean(CaptchaService.class).isActivated()) {
			log.info(" ✔ Captcha service activated: " + ctx.getBean(CaptchaService.class).getConfig().getProvider());
		} else {
			log.warn(" ❌ Captcha service disabled ! There will be no security especially protecting spam emails !");
		}
		if (ctx.getBean(GeonamesService.class).isConfigured()) {
			log.info(" ✔ Geonames service configured");
		} else {
			log.warn(" ❌ Geonames service not configured, it will always returns empty responses.");
		}
		if (ctx.getBean(OutdoorActiveService.class).configured()) {
			log.info(" ✔ Outdoor Active API configured");
		} else {
			log.warn(" ❌ Outdoor Active API not configured, it will not be available.");
		}
	}
	
}
