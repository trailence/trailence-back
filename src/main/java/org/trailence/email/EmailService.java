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

	public Mono<Void> send(String to, String template, String lang, Map<String, String> templateData) {
		Mono<String> readSubject = TrailenceUtils.readResource("templates/" + template + "." + lang + ".subject.txt");
		Mono<String> readText = TrailenceUtils.readResource("templates/" + template + "." + lang + ".body.txt");
		Mono<String> readHtml = TrailenceUtils.readResource("templates/" + template + "." + lang + ".body.html");
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
