package org.trailence;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.ComponentScan;
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
public class TrailenceApp implements SmartLifecycle, ApplicationContextAware {

	public static void main(String[] args) {
		SpringApplication.run(TrailenceApp.class, args);
	}
	
	public void initApp() {
		InitDB init = new InitDB();
		context.getAutowireCapableBeanFactory().autowireBean(init);
		init.init(context);
		checks(context);
	}
	
	private void checks(ApplicationContext ctx) {
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

	private boolean running = false;
	private ApplicationContext context;
	
	@Override
	public void start() {
		initApp();
		running = true;
	}

	@Override
	public void stop() {
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}
	
	@Override
	public int getPhase() {
		// WebServerStartStopLifecycle - 1 to do it before the web server is exposed
		return WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE - 1025;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		context = applicationContext;
	}
}
