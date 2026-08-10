package org.trailence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.trailence.contact.dto.ContactMessage;
import org.trailence.contact.dto.CreateMessageRequest;
import org.trailence.global.dto.PageResult;
import org.trailence.init.FreePlanProperties;
import org.trailence.quotas.dto.Plan;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestUtils;
import org.trailence.test.stubs.CaptchaStub;
import org.trailence.user.dto.User;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;

class TestAdmin extends AbstractTest {
	
	@Autowired private FreePlanProperties freePlan;
	
	// --- Users ---

	@Test
	void testGetUsersAndKeysWithAdminAccount() {
		assertThat(freePlan.getCollections()).isPositive();
		assertThat(freePlan.getTrails()).isPositive();
		assertThat(freePlan.getTracks()).isPositive();
		assertThat(freePlan.getTracksSize()).isPositive();
		assertThat(freePlan.getPhotos()).isPositive();
		assertThat(freePlan.getPhotosSize()).isPositive();
		assertThat(freePlan.getTags()).isPositive();
		assertThat(freePlan.getTrailTags()).isPositive();
		assertThat(freePlan.getShares()).isPositive();
		
		var user = test.createUserAndLogin(true, null);
		
		var response = user.get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		
		var users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		assertThat(users.getCount()).isPositive();
		assertThat(users.getElements()).isNotEmpty();
		assertThat(users.getPage()).isZero();
		assertThat(users.getSize()).isEqualTo(-1);
		assertThat(users.getElements()).allMatch(u -> 
			u.getQuotas().getCollectionsMax() == freePlan.getCollections() &&
			u.getQuotas().getTrailsMax() == freePlan.getTrails() &&
			u.getQuotas().getTracksMax() == freePlan.getTracks() &&
			u.getQuotas().getTracksSizeMax() == freePlan.getTracksSize() &&
			u.getQuotas().getPhotosMax() == freePlan.getPhotos() &&
			u.getQuotas().getPhotosSizeMax() == freePlan.getPhotosSize() &&
			u.getQuotas().getTagsMax() == freePlan.getTags() &&
			u.getQuotas().getTrailTagsMax() == freePlan.getTrailTags() &&
			u.getQuotas().getSharesMax() == freePlan.getShares()
		);
		for (int i = 0; i < 3 && i < users.getElements().size(); ++i) {
			var keysResponse = user.get("/api/admin/users/v1/{user}/keys", users.getElements().get(i).getEmail());
			assertThat(keysResponse.statusCode()).isEqualTo(200);
		}
		
		response = user.get("/api/admin/users/v1?page=1&size=2&sort=createdAt,desc");
		assertThat(response.statusCode()).isEqualTo(200);
		users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		assertThat(users.getCount()).isPositive();
		assertThat(users.getPage()).isEqualTo(1);
		assertThat(users.getSize()).isEqualTo(2);
		
		response = user.get("/api/admin/users/v1?page=1&size=2&sort=minAppVersion,asc");
		assertThat(response.statusCode()).isEqualTo(200);
		users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		assertThat(users.getCount()).isPositive();
		assertThat(users.getPage()).isEqualTo(1);
		assertThat(users.getSize()).isEqualTo(2);
	}

	@Test
	void testGetUsersAndKeysWithoutAdminAccount() {
		var user = test.createUserAndLogin(false, null);
		
		var response = user.get("/api/admin/users/v1");
		TestUtils.expectError(response, 403, "forbidden");
		
		response = user.get("/api/admin/users/v1/{user}/keys", user.getEmail());
		TestUtils.expectError(response, 403, "forbidden");
	}
	
	@Test
	void updateUserRoles() {
		var user = test.createUserAndLogin(false, null);
		
		var response = test.asAdmin().get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		var users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		var myUser = users.getElements().stream().filter(u -> u.getEmail().equals(user.getEmail().toLowerCase())).findAny();
		assertThat(myUser).isPresent();
		assertThat(myUser.get().getRoles()).isEmpty();
		
		response = test.asAdmin().put("/api/admin/users/v1/" + user.getEmail() + "/roles", List.of("hello", "world"));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(String[].class)).isEqualTo(new String[] { "hello", "world" });
		
		response = test.asAdmin().get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		myUser = users.getElements().stream().filter(u -> u.getEmail().equals(user.getEmail().toLowerCase())).findAny();
		assertThat(myUser).isPresent();
		assertThat(myUser.get().getRoles()).isEqualTo(List.of("hello", "world"));
		
		response = test.asAdmin().put("/api/admin/users/v1/" + user.getEmail() + "/roles", List.of());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.getBody().as(String[].class)).isEqualTo(new String[] {});
		
		response = test.asAdmin().get("/api/admin/users/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		users = response.getBody().as(new TypeRef<PageResult<User>>() {});
		myUser = users.getElements().stream().filter(u -> u.getEmail().equals(user.getEmail().toLowerCase())).findAny();
		assertThat(myUser).isPresent();
		assertThat(myUser.get().getRoles()).isEmpty();
	}

	// --- Plans ---
	
	private static final String PLAN_PATH = "/api/admin/plans/v1";

	@Test
	void crudPlans() {
		var name = RandomStringUtils.insecure().nextAlphabetic(20);
		var response = test.asAdmin().post(PLAN_PATH, new Plan(name, 1, 2, 3, 4, 5, 6, 7, 8, 9, null, null));
		assertThat(response.statusCode()).isEqualTo(200);
		
		response = test.asAdmin().get(PLAN_PATH);
		assertThat(response.statusCode()).isEqualTo(200);
		var plans = response.as(new TypeRef<PageResult<Plan>>() {}).getElements();
		assertThat(plans.stream().filter(p -> p.getName().equals(name)).findAny()).isPresent().get().satisfies(plan -> {
			assertThat(plan.getCollections()).isEqualTo(1);
			assertThat(plan.getTrails()).isEqualTo(2);
			assertThat(plan.getTracks()).isEqualTo(3);
			assertThat(plan.getTracksSize()).isEqualTo(4);
			assertThat(plan.getPhotos()).isEqualTo(5);
			assertThat(plan.getPhotosSize()).isEqualTo(6);
			assertThat(plan.getTags()).isEqualTo(7);
			assertThat(plan.getTrailTags()).isEqualTo(8);
			assertThat(plan.getShares()).isEqualTo(9);
			assertThat(plan.getActiveSubscriptionsCount()).isZero();
			assertThat(plan.getSubscriptionsCount()).isZero();
		});
		
		var newName = RandomStringUtils.insecure().nextAlphabetic(20);
		response = test.asAdmin().put(PLAN_PATH + "/" + name, new Plan(newName, 10, 20, 30, 40, 50, 60, 70, 80, 90, null, null));
		assertThat(response.statusCode()).isEqualTo(200);

		response = test.asAdmin().get(PLAN_PATH);
		assertThat(response.statusCode()).isEqualTo(200);
		plans = response.as(new TypeRef<PageResult<Plan>>() {}).getElements();
		assertThat(plans.stream().filter(p -> p.getName().equals(newName)).findAny()).isPresent().get().satisfies(plan -> {
			assertThat(plan.getCollections()).isEqualTo(10);
			assertThat(plan.getTrails()).isEqualTo(20);
			assertThat(plan.getTracks()).isEqualTo(30);
			assertThat(plan.getTracksSize()).isEqualTo(40);
			assertThat(plan.getPhotos()).isEqualTo(50);
			assertThat(plan.getPhotosSize()).isEqualTo(60);
			assertThat(plan.getTags()).isEqualTo(70);
			assertThat(plan.getTrailTags()).isEqualTo(80);
			assertThat(plan.getShares()).isEqualTo(90);
			assertThat(plan.getActiveSubscriptionsCount()).isZero();
			assertThat(plan.getSubscriptionsCount()).isZero();
		});
		assertThat(plans.stream().noneMatch(p -> p.getName().equals(name))).isTrue();
		
		response = test.asAdmin().delete(PLAN_PATH + "/" + newName);
		assertThat(response.statusCode()).isEqualTo(200);
		
		response = test.asAdmin().get(PLAN_PATH);
		assertThat(response.statusCode()).isEqualTo(200);
		plans = response.as(new TypeRef<PageResult<Plan>>() {}).getElements();
		assertThat(plans.stream().noneMatch(p -> p.getName().equals(name))).isTrue();
		assertThat(plans.stream().noneMatch(p -> p.getName().equals(newName))).isTrue();
	}

	
	// --- Contact ---
	
	@Test
	void testContactScenario() {
		assertThat(getMessages()).isEmpty();
		assertThat(getUnreadCount()).isZero();
		
		// send without account
		var captchaToken = RandomStringUtils.secure().next(30);
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
