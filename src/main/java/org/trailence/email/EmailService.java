package org.trailence.email;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
	private static final List<String> SUPPORTED_LANG = Arrays.asList("en", "fr");

	public Mono<Void> send(String to, String template, String lang, Map<String, String> templateData) {
		String language = lang.toLowerCase();
		if (SUPPORTED_LANG.indexOf(language) < 0) language = "en";
		Mono<String> readSubject = TrailenceUtils.readResource(TEMPLATES_DIR + template + "." + language + ".subject.txt");
		Mono<String> readText = TrailenceUtils.readResource(TEMPLATES_DIR + template + "." + language + ".body.txt");
		Mono<String> readHtml = TrailenceUtils.readResource(TEMPLATES_DIR + template + "." + language + ".body.html");
		return Mono.zip(readSubject, readText, readHtml).flatMap(files -> {
			String subject = applyTemplate(files.getT1(), templateData);
			String text = applyTemplate(files.getT2(), templateData);
			String html = applyTemplate(files.getT3(), templateData);
			return job.send(new Email(to, subject, text, html));
		});
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
