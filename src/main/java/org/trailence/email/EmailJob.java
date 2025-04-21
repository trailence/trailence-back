package org.trailence.email;

import java.time.Duration;
import java.util.LinkedList;
import java.util.ListIterator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;
import org.trailence.jobs.Job;
import org.trailence.jobs.JobService;
import org.trailence.jobs.db.JobEntity;

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
	@Value("${trailence.mail.throttling.max:500}")
	private int maxMails;
	@Value("${trailence.mail.throttling.max-delay:1d}")
	private Duration maxDelay;
	@Value("${trailence.mail.throttling.min-delay:1m}")
	private Duration minDelay;
	@Value("${trailence.mail.throttling.min-delay-count:10}")
	private int minDelayCount;
	
	private LinkedList<Long> lastEmails = new LinkedList<>();
	
	public String getFromTrailenceEmail() {
		return this.fromEmail;
	}

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
		return 5L * 24 * 60 * 60 * 1000;
	}
	
	@Override
	@SuppressWarnings("java:S3776")
	public Long acceptNewJob(JobEntity job) {
		long now = System.currentTimeMillis();
		synchronized (lastEmails) {
			// clean lastEmails older than 1 hour
			while (!lastEmails.isEmpty() && lastEmails.getFirst().longValue() < now - maxDelay.toMillis())
				lastEmails.removeFirst();
			if (!lastEmails.isEmpty()) {
				// minimum delay
				int count = 0;
				long maxTime = now - minDelay.toMillis();
				for (ListIterator<Long> it = lastEmails.listIterator(lastEmails.size()); it.hasPrevious(); )
					if (it.previous() > maxTime) count++;
					else break;
				if (count >= minDelayCount) return minDelay.toMillis();
				// if half of limit is reached, accept only mails with priority below 100
				if (lastEmails.size() > maxMails / 2 && job.getPriority() >= 100) return 5L * 60 * 1000;
				// if max is reached, delay the mail
				if (lastEmails.size() >= maxMails) {
					long nextTime = lastEmails.getFirst() + maxDelay.toMillis();
					if (nextTime - now < 60000) return 60000L;
					return nextTime - now;
				}
			}
			lastEmails.addLast(now);
		}
		return null;
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
				return new Result(true, null);
			} catch (Exception e) {
				log.error("Error sending mail", e);
				return new Result(false, trial >= 20 ? null : System.currentTimeMillis() + trial * 10000);
			}
		}).subscribeOn(Schedulers.boundedElastic());
	}
	
	public Mono<Void> send(Email email, int priority) {
		return jobService.createJob(TYPE, priority, email).then(Mono.fromRunnable(jobService::launch));
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
