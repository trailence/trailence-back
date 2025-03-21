package org.trailence.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.trailence.mailhog.MailHogDtos;
import org.trailence.mailhog.MailHogDtos.Message;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import jakarta.mail.internet.MimeUtility;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
	"trailence.jwt.secret=test-secret",
	"spring.r2dbc.username=postgres",
	"spring.r2dbc.password=postgres",
	"trailence.storage.type=fs",
	"trailence.storage.root=./storage-tests",
	"trailence.jobs.initialDelay=5",
	"trailence.jobs.delay=1",
	"spring.codec.max-in-memory-size=16MB",
	"trailence.free-plan.collections=10",
	"trailence.free-plan.trails=1000",
	"trailence.free-plan.tracks=2000",
	"trailence.free-plan.tracks_size=10485760",
	"trailence.free-plan.photos=500",
	"trailence.free-plan.photos_size=104857600",
	"trailence.free-plan.tags=500",
	"trailence.free-plan.trail_tags=2000",
	"trailence.free-plan.shares=50",
	"trailence.extensions.allowed.[thunderforest.com].enabled=true",
	"trailence.extensions.allowed.[thunderforest.com].role=thunderforest",
	"trailence.extensions.allowed.[thunderforest.com].content.apikey.pattern=[0-9a-f]{32}",
})
public abstract class AbstractTest {
	
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16-alpine").withUsername("postgres").withPassword("postgres").withDatabaseName("trailence");
	@SuppressWarnings({ "rawtypes", "resource" })
	static GenericContainer smtp = new GenericContainer<>("mailhog/mailhog:v1.0.1").withExposedPorts(1025, 8025);
	public static WireMockServer wireMockServer = null;
	
	private static void start() {
		Mono.zip(
			Mono.fromRunnable(postgreSQLContainer::start).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()).then(Mono.just(1)),
			Mono.fromRunnable(smtp::start).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()).then(Mono.just(1)),
			Mono.fromRunnable(AbstractTest::cleanStorage).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()).then(Mono.just(1)),
			Mono.fromRunnable(() -> {
				if (wireMockServer == null) {
					wireMockServer = new WireMockServer(WireMockConfiguration.options().port(0));
					wireMockServer.start();
				}
			}).subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.parallel()).then(Mono.just(1))
		).block();
	}
	
	private static void cleanStorage() {
		FileSystemUtils.deleteRecursively(new File("./storage-tests"));
	}

	@LocalServerPort
    private Integer port;
	
	@Autowired
	protected TestService test;
	
	@BeforeEach
	void setupRestAssured() {
		RestAssured.port = port;
	}
	
	@DynamicPropertySource
    static void postgreSQLProperties(DynamicPropertyRegistry registry) {
		start();
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://postgres@" + postgreSQLContainer.getHost() + ":" + postgreSQLContainer.getMappedPort(5432) + "/trailence");
    }
	
	@DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
		start();
        registry.add("spring.mail.host", () -> smtp.getHost());
        registry.add("spring.mail.port", () -> smtp.getMappedPort(1025));
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    }
	
	@DynamicPropertySource
	static void captchaProperties(DynamicPropertyRegistry registry) {
		start();
		registry.add("trailence.external.captcha.clientKey", () -> "captchaClient");
		registry.add("trailence.external.captcha.secretKey", () -> "captchaSecret");
		registry.add("trailence.external.captcha.provider", () -> "turnstile");
		registry.add("trailence.external.captcha.url", () -> wireMockServer.url("/captcha/siteverify"));
	}
	
	@DynamicPropertySource
	static void geonamesProperties(DynamicPropertyRegistry registry) {
		start();
		registry.add("trailence.external.geonames.username", () -> "geo_user");
		registry.add("trailence.external.geonames.url", () -> wireMockServer.url("/geonames"));
	}
	
	protected RequestSpecification mailHogRequest() {
		return RestAssured.given().baseUri("http://" + smtp.getHost() + ":" + smtp.getMappedPort(8025) + "/api");
	}
	
	@SuppressWarnings("java:S2925")
	protected Tuple2<String, String> assertMailSent(String from, String to) {
		MailHogDtos.Message message = null;
		for (var trial = 0; trial < 300; trial++) {
			var messageOpt = searchMail(from, to);
			if (messageOpt.isPresent()) {
				message = messageOpt.get();
				break;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// ignore
			}
		}

		var subject = message.getContent().getHeaders().get("Subject").getFirst();
		try {
			subject = MimeUtility.decodeText(subject);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		var result = Tuples.of(subject, message.getContent().getBody());
		
		mailHogRequest().delete("/v1/messages/" + message.getId());
		var response = mailHogRequest().get("/v2/messages");
		assertThat(response.statusCode()).isEqualTo(200);
		
		return result;
	}
	
	@SuppressWarnings("java:S2925")
	protected void assertMailNotSent(String from, String to) {
		assertThat(searchMail(from, to)).isEmpty();
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// ignore
		}
		assertThat(searchMail(from, to)).isEmpty();
	}
	
	protected Optional<Message> searchMail(String from, String to) {
		var response = mailHogRequest().get("/v2/messages");
		assertThat(response.statusCode()).isEqualTo(200);
		var messages = response.getBody().as(MailHogDtos.Messages.class);
		return messages.getItems().stream().filter(m -> {
			if (!from.toLowerCase().equals(m.getFrom().getMailbox().toLowerCase() + "@" + m.getFrom().getDomain().toLowerCase())) return false;
			if (!to.toLowerCase().equals(m.getTo().getFirst().getMailbox().toLowerCase() + "@" + m.getTo().getFirst().getDomain().toLowerCase())) return false;
			return true;
		}).findAny();
	}
}
