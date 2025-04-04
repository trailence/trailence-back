package org.trailence.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.trailence.contact.dto.ContactMessage;
import org.trailence.contact.dto.CreateMessageRequest;
import org.trailence.global.dto.PageResult;
import org.trailence.test.AbstractTest;
import org.trailence.test.stubs.CaptchaStub;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;

class TestContact extends AbstractTest {

	@Test
	void testScenario() {
		assertThat(getMessages()).isEmpty();
		assertThat(getUnreadCount()).isZero();
		
		// send without account
		var captchaToken = RandomStringUtils.random(30);
		var stub = CaptchaStub.stubCaptcha(wireMockServer, captchaToken, true);
		var response = RestAssured.given().contentType(ContentType.JSON)
		.body(new CreateMessageRequest(
			"anonymous@trailence.org",
			"type1",
			"my message",
			captchaToken
		))
		.post("/api/contact/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);
		
		assertThat(getMessages()).singleElement()
		.satisfies(msg -> {
			assertThat(msg.getEmail()).isEqualTo("anonymous@trailence.org");
			assertThat(msg.getType()).isEqualTo("type1");
			assertThat(msg.getMessage()).isEqualTo("my message");
			assertThat(msg.isRead()).isFalse();
		});
		assertThat(getUnreadCount()).isEqualTo(1);
		
		// send with account
		var user = test.createUserAndLogin();
		response = user.post("/api/contact/v1", new CreateMessageRequest(
			"anonymous2@trailence.org",
			"type2",
			"my second message",
			null
		));
		assertThat(response.statusCode()).isEqualTo(200);
		
		var messages = getMessages();
		assertThat(messages).hasSize(2).satisfiesOnlyOnce(msg -> {
			assertThat(msg.getEmail()).isEqualTo("anonymous@trailence.org");
			assertThat(msg.getType()).isEqualTo("type1");
			assertThat(msg.getMessage()).isEqualTo("my message");
			assertThat(msg.isRead()).isFalse();
		}).satisfiesOnlyOnce(msg -> {
			assertThat(msg.getEmail()).isEqualTo(user.getEmail().toLowerCase());
			assertThat(msg.getType()).isEqualTo("type2");
			assertThat(msg.getMessage()).isEqualTo("my second message");
			assertThat(msg.isRead()).isFalse();
		});
		
		assertThat(getUnreadCount()).isEqualTo(2);
		
		markAsRead(messages.stream().filter(msg -> "type1".equals(msg.getType())).findAny().get().getUuid());
		assertThat(getUnreadCount()).isEqualTo(1);
		
		markAsUnread(messages.stream().filter(msg -> "type2".equals(msg.getType())).findAny().get().getUuid());
		assertThat(getUnreadCount()).isEqualTo(1);

		markAsUnread(messages.stream().filter(msg -> "type1".equals(msg.getType())).findAny().get().getUuid());
		assertThat(getUnreadCount()).isEqualTo(2);
		
		delete(messages.stream().filter(msg -> "type2".equals(msg.getType())).findAny().get().getUuid());
		assertThat(getMessages()).singleElement()
		.satisfies(msg -> {
			assertThat(msg.getEmail()).isEqualTo("anonymous@trailence.org");
			assertThat(msg.getType()).isEqualTo("type1");
			assertThat(msg.getMessage()).isEqualTo("my message");
			assertThat(msg.isRead()).isFalse();
		});
		assertThat(getUnreadCount()).isEqualTo(1);
		
		delete(messages.stream().filter(msg -> "type1".equals(msg.getType())).findAny().get().getUuid());
		assertThat(getMessages()).isEmpty();
		assertThat(getUnreadCount()).isZero();
	}
	
	private List<ContactMessage> getMessages() {
		var response = test.asAdmin().get("/api/contact/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		return response.as(new TypeRef<PageResult<ContactMessage>>() {}).getElements();
	}
	
	private long getUnreadCount() {
		var response = test.asAdmin().get("/api/contact/v1/unread");
		assertThat(response.statusCode()).isEqualTo(200);
		return response.as(Long.class).longValue();
	}
	
	private void markAsRead(String... uuids) {
		var response = test.asAdmin().put("/api/contact/v1/read", uuids);
		assertThat(response.statusCode()).isEqualTo(200);
	}
	
	private void markAsUnread(String... uuids) {
		var response = test.asAdmin().put("/api/contact/v1/unread", uuids);
		assertThat(response.statusCode()).isEqualTo(200);
	}

	
	private void delete(String... uuids) {
		var response = test.asAdmin().post("/api/contact/v1/delete", uuids);
		assertThat(response.statusCode()).isEqualTo(200);
	}
	
}
