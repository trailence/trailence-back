package org.trailence;

import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.ldap.LdapRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.data.GraphQlQueryByExampleAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.data.GraphQlQuerydslAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.data.GraphQlReactiveQueryByExampleAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.data.GraphQlReactiveQuerydslAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.reactive.GraphQlWebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.rsocket.GraphQlRSocketAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.rsocket.RSocketGraphQlClientAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.security.GraphQlWebFluxSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.security.GraphQlWebMvcSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.graphql.servlet.GraphQlWebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration;
import org.springframework.boot.autoconfigure.hateoas.HypermediaAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastJpaDependencyAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JndiDataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jersey.JerseyAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.JndiConnectionFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.autoconfigure.jsonb.JsonbAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.embedded.EmbeddedLdapAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.pulsar.PulsarAutoConfiguration;
import org.springframework.boot.autoconfigure.pulsar.PulsarReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerJwtAutoConfiguration;
import org.springframework.boot.autoconfigure.security.rsocket.RSocketSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyAutoConfiguration;
import org.springframework.boot.autoconfigure.sendgrid.SendGridAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveMultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebSessionIdResolverAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.webservices.WebServicesAutoConfiguration;
import org.springframework.boot.autoconfigure.webservices.client.WebServiceTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.reactive.WebSocketReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
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

@SpringBootApplication(exclude= {
	JmxAutoConfiguration.class,
	ReactiveMultipartAutoConfiguration.class,
	ReactiveOAuth2ResourceServerAutoConfiguration.class,
	SpringApplicationAdminJmxAutoConfiguration.class,
	SqlInitializationAutoConfiguration.class,
	SslAutoConfiguration.class,
	WebSessionIdResolverAutoConfiguration.class,
	ActiveMQAutoConfiguration.class,
	ArtemisAutoConfiguration.class,
	BatchAutoConfiguration.class,
	CacheAutoConfiguration.class,
	CassandraAutoConfiguration.class,
	CassandraDataAutoConfiguration.class,
	CassandraReactiveDataAutoConfiguration.class,
	CassandraReactiveRepositoriesAutoConfiguration.class,
	CassandraRepositoriesAutoConfiguration.class,
	CouchbaseAutoConfiguration.class,
	CouchbaseDataAutoConfiguration.class,
	CouchbaseReactiveDataAutoConfiguration.class,
	CouchbaseReactiveRepositoriesAutoConfiguration.class,
	CouchbaseRepositoriesAutoConfiguration.class,
	DispatcherServletAutoConfiguration.class,
	ElasticsearchClientAutoConfiguration.class,
	ElasticsearchDataAutoConfiguration.class,
	ElasticsearchRepositoriesAutoConfiguration.class,
	ElasticsearchRestClientAutoConfiguration.class,
	EmbeddedLdapAutoConfiguration.class,
	ErrorMvcAutoConfiguration.class,
	FlywayAutoConfiguration.class,
	FreeMarkerAutoConfiguration.class,
	GraphQlAutoConfiguration.class,
	GraphQlQueryByExampleAutoConfiguration.class,
	GraphQlQuerydslAutoConfiguration.class,
	GraphQlRSocketAutoConfiguration.class,
	GraphQlReactiveQueryByExampleAutoConfiguration.class,
	GraphQlReactiveQuerydslAutoConfiguration.class,
	GraphQlWebFluxAutoConfiguration.class,
	GraphQlWebFluxSecurityAutoConfiguration.class,
	GraphQlWebMvcAutoConfiguration.class,
	GraphQlWebMvcSecurityAutoConfiguration.class,
	GroovyTemplateAutoConfiguration.class,
	GsonAutoConfiguration.class,
	H2ConsoleAutoConfiguration.class,
	HazelcastAutoConfiguration.class,
	HazelcastJpaDependencyAutoConfiguration.class,
	HibernateJpaAutoConfiguration.class,
	HypermediaAutoConfiguration.class,
	IntegrationAutoConfiguration.class,
	JdbcClientAutoConfiguration.class,
	JdbcRepositoriesAutoConfiguration.class,
	JdbcTemplateAutoConfiguration.class,
	JerseyAutoConfiguration.class,
	JmsAutoConfiguration.class,
	JndiConnectionFactoryAutoConfiguration.class,
	JndiDataSourceAutoConfiguration.class,
	JooqAutoConfiguration.class,
	JpaRepositoriesAutoConfiguration.class,
	JsonbAutoConfiguration.class,
	JtaAutoConfiguration.class,
	KafkaAutoConfiguration.class,
	LdapAutoConfiguration.class,
	LdapRepositoriesAutoConfiguration.class,
	LiquibaseAutoConfiguration.class,
	MongoAutoConfiguration.class,
	MongoDataAutoConfiguration.class,
	MongoReactiveAutoConfiguration.class,
	MongoReactiveDataAutoConfiguration.class,
	MongoReactiveRepositoriesAutoConfiguration.class,
	MongoRepositoriesAutoConfiguration.class,
	MultipartAutoConfiguration.class,
	MustacheAutoConfiguration.class,
	Neo4jAutoConfiguration.class,
	Neo4jDataAutoConfiguration.class,
	Neo4jReactiveDataAutoConfiguration.class,
	Neo4jReactiveRepositoriesAutoConfiguration.class,
	Neo4jRepositoriesAutoConfiguration.class,
	OAuth2AuthorizationServerAutoConfiguration.class,
	OAuth2AuthorizationServerJwtAutoConfiguration.class,
	OAuth2ClientAutoConfiguration.class,
	OAuth2ClientWebSecurityAutoConfiguration.class,
	OAuth2ResourceServerAutoConfiguration.class,
	PulsarAutoConfiguration.class,
	PulsarReactiveAutoConfiguration.class,
	QuartzAutoConfiguration.class,
	RSocketGraphQlClientAutoConfiguration.class,
	RSocketMessagingAutoConfiguration.class,
	RSocketRequesterAutoConfiguration.class,
	RSocketSecurityAutoConfiguration.class,
	RSocketServerAutoConfiguration.class,
	RSocketStrategiesAutoConfiguration.class,
	RabbitAutoConfiguration.class,
	ReactiveElasticsearchClientAutoConfiguration.class,
	ReactiveElasticsearchRepositoriesAutoConfiguration.class,
	ReactiveOAuth2ClientAutoConfiguration.class,
	ReactiveOAuth2ClientWebSecurityAutoConfiguration.class,
	RedisAutoConfiguration.class,
	RedisReactiveAutoConfiguration.class,
	RedisRepositoriesAutoConfiguration.class,
	RepositoryRestMvcAutoConfiguration.class,
	Saml2RelyingPartyAutoConfiguration.class,
	SendGridAutoConfiguration.class,
	ServletWebServerFactoryAutoConfiguration.class,
	SessionAutoConfiguration.class,
	SpringDataWebAutoConfiguration.class,
	ThymeleafAutoConfiguration.class,
	WebMvcAutoConfiguration.class,
	WebServiceTemplateAutoConfiguration.class,
	WebServicesAutoConfiguration.class,
	WebSocketMessagingAutoConfiguration.class,
	WebSocketReactiveAutoConfiguration.class,
	WebSocketServletAutoConfiguration.class,
	XADataSourceAutoConfiguration.class
})
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
