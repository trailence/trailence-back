package org.trailence.email;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.trailence.email.EmailJob.Email;
import org.trailence.global.TrailenceUtils;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class EmailService {
	
	private final EmailJob job;
	
	@Value("${trailence.hostname:trailence.org}")
	private String hostname;
	@Value("${trailence.protocol:https}")
	private String protocol;
	@Value("${trailence.linkpath:/link/}")
	private String linkpath;
	
	private static final String TEMPLATES_DIR = "templates/";
	
	public static final int REGISTER_USER_PRIORITY = 1;
	public static final int CHANGE_PASSWORD_PRIORITY = 3;
	public static final int FORGOT_PASSWORD_PRIORITY = 10;
	public static final int DELETE_USER_PRIORITY = 25;
	public static final int SHARE_INVITE_PRIORITY = 100;
	public static final int SHARE_NEW_PRIORITY = 200;

	public Mono<Void> send(int priority, String to, String template, String lang, Map<String, String> templateData) {
		String language = getLanguage(lang);
		Mono<String> readSubject = TrailenceUtils.readResource(TEMPLATES_DIR + template + "." + language + ".subject.txt");
		Mono<String> readText = TrailenceUtils.readResource(TEMPLATES_DIR + template + "." + language + ".body.txt");
		Mono<String> readHtml = TrailenceUtils.readResource(TEMPLATES_DIR + template + "." + language + ".body.html");
		return Mono.zip(readSubject, readText, readHtml).flatMap(files -> {
			String subject = applyTemplate(files.getT1(), templateData);
			String text = applyTemplate(files.getT2(), templateData);
			String html = applyTemplate(files.getT3(), templateData);
			return job.send(new Email(to, subject, text, html), priority);
		});
	}
	
	@SuppressWarnings("java:S1301") // switch instead of if
	private String getLanguage(String lang) {
		if (lang == null) return "en";
		switch (lang.toLowerCase()) {
		case "fr": return "fr";
		default: return "en";
		}
	}
	
	public String getLinkUrl(String link) {
		return protocol + "://" + hostname + linkpath + link;
	}
	
	private String applyTemplate(String template, Map<String, String> templateData) {
		Map<String, String> data = new HashMap<>(templateData);
		data.put("hostname", hostname);
		String result = template;
		for (var entry : data.entrySet()) result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
		return result;
	}
	
}
