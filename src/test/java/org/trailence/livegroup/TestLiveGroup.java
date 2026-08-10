package org.trailence.livegroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.trailence.livegroup.dto.LiveGroup;
import org.trailence.livegroup.dto.LiveGroupRequest;
import org.trailence.test.AbstractTest;
import org.trailence.test.TestService.TestUserLoggedIn;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

class TestLiveGroup extends AbstractTest {
	
	private List<LiveGroup> getLiveGroups(RequestSpecification req) {
		var response = req.get("/api/live-group/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		return Arrays.asList(response.getBody().as(LiveGroup[].class));
	}
	
	private List<LiveGroup> getLiveGroups(String anonymousId) {
		var response = RestAssured.given().queryParam("id", anonymousId).get("/api/live-group/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		return Arrays.asList(response.getBody().as(LiveGroup[].class));
	}
	
	private LiveGroup createLiveGroup(TestUserLoggedIn user, String name, String pseudo, String trailOwner, String trailUuid, Boolean trailShared) {
		var response = user.post("/api/live-group/v1", new LiveGroupRequest(name, pseudo, trailOwner, trailUuid, trailShared));
		assertThat(response.statusCode()).isEqualTo(200);
		return response.getBody().as(LiveGroup.class);
	}

	private LiveGroup updateLiveGroup(TestUserLoggedIn user, String slug, String name, String pseudo, String trailOwner, String trailUuid, Boolean trailShared) {
		var response = user.put("/api/live-group/v1/" + slug, new LiveGroupRequest(name, pseudo, trailOwner, trailUuid, trailShared));
		assertThat(response.statusCode()).isEqualTo(200);
		return response.getBody().as(LiveGroup.class);
	}
	
	private LiveGroup join(TestUserLoggedIn user, String slug, String pseudo) {
		var response = user.post("/api/live-group/v1/join/" + slug, pseudo);
		assertThat(response.statusCode()).isEqualTo(200);
		return response.getBody().as(LiveGroup.class);
	}

	private LiveGroup join(String anonymousId, String slug, String pseudo) {
		var response = RestAssured.given().queryParam("id", anonymousId).body(pseudo).post("/api/live-group/v1/join/" + slug);
		assertThat(response.statusCode()).isEqualTo(200);
		return response.getBody().as(LiveGroup.class);
	}

	private void leave(TestUserLoggedIn user, String slug) {
		var response = user.delete("/api/live-group/v1/join/" + slug);
		assertThat(response.statusCode()).isEqualTo(200);
	}

	private void leave(String anonymousId, String slug) {
		var response = RestAssured.given().queryParam("id", anonymousId).delete("/api/live-group/v1/join/" + slug);
		assertThat(response.statusCode()).isEqualTo(200);
	}
	
	private void deleteLiveGroup(TestUserLoggedIn user, String slug) {
		var response = user.delete("/api/live-group/v1/" + slug);
		assertThat(response.statusCode()).isEqualTo(200);
	}
	
	@Test
	void scenario() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var anon1 = RandomStringUtils.secure().nextAlphabetic(10);
		var anon2 = RandomStringUtils.secure().nextAlphabetic(10);
		
		assertThat(getLiveGroups(user1.request())).isEmpty();
		assertThat(getLiveGroups(user2.request())).isEmpty();
		assertThat(getLiveGroups(anon1)).isEmpty();
		assertThat(getLiveGroups(anon2)).isEmpty();
		
		// user1 create group without trail
		var group1 = createLiveGroup(user1, "group1", "u1", null, null, null);
		assertThat(getLiveGroups(user1.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1");
			assertThat(g.getMembers()).singleElement().satisfies(m -> assertThat(m.isYou()).isTrue());
		});
		
		// user2 join group1
		join(user2, group1.getSlug(), "u2");
		assertThat(getLiveGroups(user1.request())).singleElement()
			.satisfies(g -> {
				assertThat(g.getName()).isEqualTo("group1");
				assertThat(g.getMembers())
					.hasSize(2)
					.anySatisfy(m -> {
						assertThat(m.isYou()).isTrue();
						assertThat(m.getName()).isEqualTo("u1");
					})
					.anySatisfy(m -> {
						assertThat(m.isYou()).isFalse();
						assertThat(m.getName()).isEqualTo("u2");
					});
			});
		assertThat(getLiveGroups(user2.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1");
			assertThat(g.getMembers())
				.hasSize(2)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u2");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1");
				});
		});
		
		// anon1 join group1
		join(anon1, group1.getSlug(), "a1");
		assertThat(getLiveGroups(user1.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1");
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u1");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1");
				})
				;
		});
		assertThat(getLiveGroups(user2.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1");
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u2");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1");
				})
				;
		});
		assertThat(getLiveGroups(anon1)).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1");
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("a1");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2");
				})
				;
		});
		assertThat(getLiveGroups(anon2)).isEmpty();
		
		// user1 share a trail attached to the group, but nobody can see it
		var trail = user1.createTrail(user1.getMyTrails(), true);
		updateLiveGroup(user1, group1.getSlug(), "group1Updated", "u1Updated", trail.getOwner(), trail.getUuid(), Boolean.TRUE);
		assertThat(getLiveGroups(user1.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1Updated");
			assertThat(g.getTrailOwner()).isEqualTo(trail.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail.getUuid());
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1");
				})
				;
		});
		assertThat(getLiveGroups(user2.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1Updated");
			assertThat(g.getTrailOwner()).isNull();
			assertThat(g.getTrailUuid()).isNull();
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u2");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1");
				})
				;
		});
		assertThat(getLiveGroups(anon1)).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1Updated");
			assertThat(g.getTrailOwner()).isNull();
			assertThat(g.getTrailUuid()).isNull();
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("a1");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2");
				})
				;
		});
		
		// user1 create a public link on the trail => everybody can see it
		var link = user1.createPublicLink(trail);
		assertThat(getLiveGroups(user1.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1Updated");
			assertThat(g.getTrailOwner()).isEqualTo(trail.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail.getUuid());
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1");
				})
				;
		});
		assertThat(getLiveGroups(user2.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1Updated");
			assertThat(g.getTrailOwner()).isEqualTo("link");
			assertThat(g.getTrailUuid()).isEqualTo(link.getLink());
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u2");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1");
				})
				;
		});
		assertThat(getLiveGroups(anon1)).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1Updated");
			assertThat(g.getTrailOwner()).isEqualTo("link");
			assertThat(g.getTrailUuid()).isEqualTo(link.getLink());
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("a1");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2");
				})
				;
		});
		
		// user2 rename itself
		join(user2, group1.getSlug(), "u2Updated");
		assertThat(getLiveGroups(user1.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1");
				})
				;
		});
		assertThat(getLiveGroups(user2.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u2Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1");
				})
				;
		});
		assertThat(getLiveGroups(anon1)).singleElement()
		.satisfies(g -> {
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("a1");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2Updated");
				})
				;
		});
		
		// anon1 rename itself
		join(anon1, group1.getSlug(), "a1Updated");
		assertThat(getLiveGroups(user1.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1Updated");
				})
				;
		});
		assertThat(getLiveGroups(user2.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u2Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("a1Updated");
				})
				;
		});
		assertThat(getLiveGroups(anon1)).singleElement()
		.satisfies(g -> {
			assertThat(g.getMembers())
				.hasSize(3)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("a1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2Updated");
				})
				;
		});
		
		// anon1 leaves
		leave(anon1, group1.getSlug());
		assertThat(getLiveGroups(user1.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1Updated");
			assertThat(g.getTrailOwner()).isEqualTo(trail.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail.getUuid());
			assertThat(g.getMembers())
				.hasSize(2)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u1Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u2Updated");
				});
		});
		assertThat(getLiveGroups(user2.request())).singleElement()
		.satisfies(g -> {
			assertThat(g.getName()).isEqualTo("group1Updated");
			assertThat(g.getTrailOwner()).isEqualTo("link");
			assertThat(g.getTrailUuid()).isEqualTo(link.getLink());
			assertThat(g.getMembers())
				.hasSize(2)
				.anySatisfy(m -> {
					assertThat(m.isYou()).isTrue();
					assertThat(m.getName()).isEqualTo("u2Updated");
				})
				.anySatisfy(m -> {
					assertThat(m.isYou()).isFalse();
					assertThat(m.getName()).isEqualTo("u1Updated");
				});
		});
		assertThat(getLiveGroups(anon1)).isEmpty();
		
		// user1 delete the group
		deleteLiveGroup(user1, group1.getSlug());
		assertThat(getLiveGroups(user1.request())).isEmpty();
		assertThat(getLiveGroups(user2.request())).isEmpty();
		assertThat(getLiveGroups(anon1)).isEmpty();
	}
	
	@Test
	void withTrailFromSharedCollection() {
		var user1 = test.createUserAndLogin();
		var user2 = test.createUserAndLogin();
		var user3 = test.createUserAndLogin();
		var anon = RandomStringUtils.secure().nextAlphabetic(15);
		
		var col1 = user1.createSharedCollection(user2.getEmail());
		var trail1 = user1.createTrail(col1, true);
		var trail1User2 = user2.getTrails().getFirst();
		var group = createLiveGroup(user1, "The Group", "The Owner", trail1.getOwner(), trail1.getUuid(), Boolean.TRUE);
		
		join(user2, group.getSlug(), "Second");
		join(user3, group.getSlug(), "Third");
		join(anon, group.getSlug(), "Anon");
		
		assertThat(getLiveGroups(user1.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("The Group");
			assertThat(g.getTrailOwner()).isEqualTo(trail1.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail1.getUuid());
			assertThat(g.getMembers()).hasSize(4);
		});
		assertThat(getLiveGroups(user2.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("The Group");
			assertThat(g.getTrailOwner()).isEqualTo(trail1User2.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail1User2.getUuid());
			assertThat(g.getMembers()).hasSize(4);
		});
		assertThat(getLiveGroups(user3.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("The Group");
			assertThat(g.getTrailOwner()).isNull();
			assertThat(g.getTrailUuid()).isNull();
			assertThat(g.getMembers()).hasSize(4);
		});
		assertThat(getLiveGroups(anon)).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("The Group");
			assertThat(g.getTrailOwner()).isNull();
			assertThat(g.getTrailUuid()).isNull();
			assertThat(g.getMembers()).hasSize(4);
		});
		
		var col2 = user1.createSharedCollection(user3.getEmail());
		var trail2 = user1.createTrail(col2, true);
		var trail2User3 = user3.getTrails().getFirst();
		
		updateLiveGroup(user1, group.getSlug(), "Updated Group", null, trail2.getOwner(), trail2.getUuid(), Boolean.TRUE);
		assertThat(getLiveGroups(user1.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("Updated Group");
			assertThat(g.getTrailOwner()).isEqualTo(trail2.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail2.getUuid());
			assertThat(g.getMembers()).hasSize(4);
		});
		assertThat(getLiveGroups(user2.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("Updated Group");
			assertThat(g.getTrailOwner()).isNull();
			assertThat(g.getTrailUuid()).isNull();
			assertThat(g.getMembers()).hasSize(4);
		});
		assertThat(getLiveGroups(user3.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("Updated Group");
			assertThat(g.getTrailOwner()).isEqualTo(trail2User3.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail2User3.getUuid());
			assertThat(g.getMembers()).hasSize(4);
		});
		assertThat(getLiveGroups(anon)).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("Updated Group");
			assertThat(g.getTrailOwner()).isNull();
			assertThat(g.getTrailUuid()).isNull();
			assertThat(g.getMembers()).hasSize(4);
		});

		
		leave(user2, group.getSlug());
		assertThat(getLiveGroups(user1.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("Updated Group");
			assertThat(g.getTrailOwner()).isEqualTo(trail2.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail2.getUuid());
			assertThat(g.getMembers()).hasSize(3);
		});
		assertThat(getLiveGroups(user2.request())).isEmpty();
		assertThat(getLiveGroups(user3.request())).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("Updated Group");
			assertThat(g.getTrailOwner()).isEqualTo(trail2User3.getOwner());
			assertThat(g.getTrailUuid()).isEqualTo(trail2User3.getUuid());
			assertThat(g.getMembers()).hasSize(3);
		});
		assertThat(getLiveGroups(anon)).singleElement().satisfies(g -> {
			assertThat(g.getName()).isEqualTo("Updated Group");
			assertThat(g.getTrailOwner()).isNull();
			assertThat(g.getTrailUuid()).isNull();
			assertThat(g.getMembers()).hasSize(3);
		});
		
		deleteLiveGroup(user1, group.getSlug());
		assertThat(getLiveGroups(user1.request())).isEmpty();
		assertThat(getLiveGroups(user2.request())).isEmpty();
		assertThat(getLiveGroups(user3.request())).isEmpty();
		assertThat(getLiveGroups(anon)).isEmpty();
	}
	
}
