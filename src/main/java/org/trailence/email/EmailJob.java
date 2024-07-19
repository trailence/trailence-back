package org.trailence.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.jobs.Job;
import org.trailence.jobs.JobService;

import io.r2dbc.postgresql.codec.Json;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailJob implements Job {
	
	public static final String TYPE = "email";
	
	private final JavaMailSender emailSender;
	private final JobService jobService;
	
	@Value("${trailence.mail.from.email:trailence@trailence.org}")
	private String fromEmail;
	@Value("${trailence.mail.from.name:Trailence}")
	private String fromName;

	@Override
	public String getType() {
		return TYPE;
	}
	
	@Override
	public long getInitialDelayMillis() {
		return 0;
	}
	
	@Override
	public long getExpirationDelayMillis() {
		return 2L * 24 * 60 * 60 * 1000;
	}
	
	@Override
	public Mono<Result> execute(Json data, int trial) {
		return Mono.fromSupplier(() -> {
			try {
				Email email = TrailenceUtils.mapper.readValue(data.asString(), Email.class);
				MimeMessage message = emailSender.createMimeMessage();
				MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED);
				helper.setFrom(new InternetAddress(fromEmail, fromName));
				helper.setTo(email.getTo());
				helper.setSubject(email.getSubject());
				helper.setText(email.getText(), email.getHtml());
				emailSender.send(message);
				return new Result(null);
			} catch (Exception e) {
				log.error("Error sending mail", e);
				return new Result(trial >= 20 ? null : System.currentTimeMillis() + trial * 10000);
			}
		}).subscribeOn(Schedulers.boundedElastic());
	}
	
	public Mono<Void> send(Email email) {
		return jobService.createJob(TYPE, email).then(Mono.fromRunnable(jobService::launch));
	}
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Email {
		private String to;
		private String subject;
		private String text;
		private String html;
	}
	
}
