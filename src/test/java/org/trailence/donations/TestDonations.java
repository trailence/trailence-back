package org.trailence.donations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.trailence.donations.dto.CreateDonationRequest;
import org.trailence.donations.dto.Donation;
import org.trailence.donations.dto.DonationGoal;
import org.trailence.donations.dto.DonationStatus;
import org.trailence.global.dto.PageResult;
import org.trailence.test.AbstractTest;
import org.trailence.test.stubs.CurrencyStub;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestDonations extends AbstractTest {

	@Order(1)
	@Test
	void configureGoals() {
		var response = test.asAdmin().get("/api/donation/v1/goals");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.as(new TypeRef<List<DonationGoal>>() {})).isEmpty();
		
		response = test.asAdmin().post("/api/donation/v1/goals", List.of(
			new DonationGoal(1, "type1", 1000, 0, 0),
			new DonationGoal(2, "type2", 2000, 0, 0)
		));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.as(new TypeRef<List<DonationGoal>>() {}))
		.satisfiesOnlyOnce(goal -> {
			assertThat(goal.getIndex()).isEqualTo(1);
			assertThat(goal.getType()).isEqualTo("type1");
			assertThat(goal.getAmount()).isEqualTo(1000);
		})
		.satisfiesOnlyOnce(goal -> {
			assertThat(goal.getIndex()).isEqualTo(2);
			assertThat(goal.getType()).isEqualTo("type2");
			assertThat(goal.getAmount()).isEqualTo(2000);
		});
		
		response = test.asAdmin().get("/api/donation/v1/goals");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.as(new TypeRef<List<DonationGoal>>() {}))
		.satisfiesOnlyOnce(goal -> {
			assertThat(goal.getIndex()).isEqualTo(1);
			assertThat(goal.getType()).isEqualTo("type1");
			assertThat(goal.getAmount()).isEqualTo(1000);
		})
		.satisfiesOnlyOnce(goal -> {
			assertThat(goal.getIndex()).isEqualTo(2);
			assertThat(goal.getType()).isEqualTo("type2");
			assertThat(goal.getAmount()).isEqualTo(2000);
		});
		
		assertThat(getStatus()).satisfies(status -> {
			assertThat(status.getCurrentDonations()).isZero();
			assertThat(status.getGoals()).hasSize(2)
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(1);
				assertThat(goal.getType()).isEqualTo("type1");
				assertThat(goal.getAmount()).isEqualTo(1000);
			})
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(2);
				assertThat(goal.getType()).isEqualTo("type2");
				assertThat(goal.getAmount()).isEqualTo(2000);
			});
		});
	}
	
	@Order(10)
	@Test
	void createCustomDonationInEuro() {
		var response = test.asAdmin().post("/api/donation/v1", new CreateDonationRequest(
			"test", "test1",
			System.currentTimeMillis(),
			"1.50", "EUR",
			"1.49", "EUR",
			"generous@trailence.org",
			""
		));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.as(Donation.class)).satisfies(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		});
		
		assertThat(getStatus()).satisfies(status -> {
			assertThat(status.getCurrentDonations()).isEqualTo(1490000);
			assertThat(status.getGoals()).hasSize(2)
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(1);
				assertThat(goal.getType()).isEqualTo("type1");
				assertThat(goal.getAmount()).isEqualTo(1000);
			})
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(2);
				assertThat(goal.getType()).isEqualTo("type2");
				assertThat(goal.getAmount()).isEqualTo(2000);
			});
		});
		
		assertThat(getDonations()).singleElement().satisfies(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		});
	}

	
	@Order(20)
	@Test
	void createCustomDonationInDollar() {
		var stub = CurrencyStub.stubCurrency(wireMockServer, "1.12345");
		var response = test.asAdmin().post("/api/donation/v1", new CreateDonationRequest(
			"test", "test2",
			System.currentTimeMillis(),
			"2.50", "USD",
			"2.00", "USD",
			"generous2@trailence.org",
			""
		));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.as(Donation.class)).satisfies(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test2");
			assertThat(d.getAmount()).isEqualTo((long)(2500000 / 1.12345));
			assertThat(d.getRealAmount()).isEqualTo((long)(2000000 / 1.12345));
			assertThat(d.getEmail()).isEqualTo("generous2@trailence.org");
		});
		assertThat(wireMockServer.countRequestsMatching(stub.getRequest()).getCount()).isEqualTo(1);
		wireMockServer.removeStub(stub);
		
		assertThat(getStatus()).satisfies(status -> {
			assertThat(status.getCurrentDonations()).isEqualTo(1490000 + (long)(2000000 / 1.12345));
			assertThat(status.getGoals()).hasSize(2)
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(1);
				assertThat(goal.getType()).isEqualTo("type1");
				assertThat(goal.getAmount()).isEqualTo(1000);
			})
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(2);
				assertThat(goal.getType()).isEqualTo("type2");
				assertThat(goal.getAmount()).isEqualTo(2000);
			});
		});
		
		assertThat(getDonations())
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		})
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test2");
			assertThat(d.getAmount()).isEqualTo((long)(2500000 / 1.12345));
			assertThat(d.getRealAmount()).isEqualTo((long)(2000000 / 1.12345));
			assertThat(d.getEmail()).isEqualTo("generous2@trailence.org");
		});
	}
	
	@Order(30)
	@Test
	void createDonationWithSameIdDoesNotCreateIt() {
		var response = test.asAdmin().post("/api/donation/v1", new CreateDonationRequest(
			"test", "test1",
			System.currentTimeMillis(),
			"2.50", "EUR",
			"2.49", "EUR",
			"generous3@trailence.org",
			""
		));
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.as(Donation.class)).satisfies(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		});
		
		assertThat(getStatus()).satisfies(status -> {
			assertThat(status.getCurrentDonations()).isEqualTo(1490000 + (long)(2000000 / 1.12345));
			assertThat(status.getGoals()).hasSize(2)
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(1);
				assertThat(goal.getType()).isEqualTo("type1");
				assertThat(goal.getAmount()).isEqualTo(1000);
			})
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(2);
				assertThat(goal.getType()).isEqualTo("type2");
				assertThat(goal.getAmount()).isEqualTo(2000);
			});
		});
		
		assertThat(getDonations())
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		})
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test2");
			assertThat(d.getAmount()).isEqualTo((long)(2500000 / 1.12345));
			assertThat(d.getRealAmount()).isEqualTo((long)(2000000 / 1.12345));
			assertThat(d.getEmail()).isEqualTo("generous2@trailence.org");
		});
	}
	
	@Order(100)
	@Test
	void deleteDonation() {
		var secondDonation = getDonations().stream().filter(d -> "test2".equals(d.getPlatformId())).findAny().get();
		var response = test.asAdmin().delete("/api/donation/v1/" + secondDonation.getUuid());
		assertThat(response.statusCode()).isEqualTo(200);

		assertThat(getDonations()).singleElement().satisfies(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		});
		
		assertThat(getStatus()).satisfies(status -> {
			assertThat(status.getCurrentDonations()).isEqualTo(1490000);
			assertThat(status.getGoals()).hasSize(2)
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(1);
				assertThat(goal.getType()).isEqualTo("type1");
				assertThat(goal.getAmount()).isEqualTo(1000);
			})
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(2);
				assertThat(goal.getType()).isEqualTo("type2");
				assertThat(goal.getAmount()).isEqualTo(2000);
			});
		});
	}
	
	@Order(1000)
	@Test
	@SuppressWarnings("java:S6126")
	void createKofiDonation() {
		var response = RestAssured.given()
		.contentType(ContentType.URLENC)
		.formParam("data", "{\r\n"
			+ "  \"verification_token\": \"05ae7a8e-e497-4bec-8919-79c56bf097df\",\r\n"
			+ "  \"message_id\": \"393634c9-c1f7-4017-9ec5-ecd568047003\",\r\n"
			+ "  \"timestamp\": \"2025-04-04T01:18:37Z\",\r\n"
			+ "  \"type\": \"Donation\",\r\n"
			+ "  \"is_public\": true,\r\n"
			+ "  \"from_name\": \"Jo Example\",\r\n"
			+ "  \"message\": \"Good luck with the integration!\",\r\n"
			+ "  \"amount\": \"3.00\",\r\n"
			+ "  \"url\": \"https://ko-fi.com/Home/CoffeeShop?txid=00000000-1111-2222-3333-444444444444\",\r\n"
			+ "  \"email\": \"jo.example@example.com\",\r\n"
			+ "  \"currency\": \"EUR\",\r\n"
			+ "  \"is_subscription_payment\": false,\r\n"
			+ "  \"is_first_subscription_payment\": false,\r\n"
			+ "  \"kofi_transaction_id\": \"00000000-1111-2222-3333-444444444444\",\r\n"
			+ "  \"shop_items\": null,\r\n"
			+ "  \"tier_name\": null,\r\n"
			+ "  \"shipping\": null\r\n"
			+ "}")
		.post("/api/kofi/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(getDonations())
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		})
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("kofi");
			assertThat(d.getPlatformId()).isEqualTo("393634c9-c1f7-4017-9ec5-ecd568047003");
			assertThat(d.getAmount()).isEqualTo(3000000);
			assertThat(d.getRealAmount()).isEqualTo(3000000);
			assertThat(d.getEmail()).isEqualTo("jo.example@example.com");
		});
		
		assertThat(getStatus()).satisfies(status -> {
			assertThat(status.getCurrentDonations()).isEqualTo(1490000 + 3000000);
			assertThat(status.getGoals()).hasSize(2)
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(1);
				assertThat(goal.getType()).isEqualTo("type1");
				assertThat(goal.getAmount()).isEqualTo(1000);
			})
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(2);
				assertThat(goal.getType()).isEqualTo("type2");
				assertThat(goal.getAmount()).isEqualTo(2000);
			});
		});
	}
	
	@Order(1001)
	@Test
	@SuppressWarnings("java:S6126")
	void createSameKofiDonationIgnoresIt() {
		var response = RestAssured.given()
		.contentType(ContentType.URLENC)
		.formParam("data", "{\r\n"
			+ "  \"verification_token\": \"05ae7a8e-e497-4bec-8919-79c56bf097df\",\r\n"
			+ "  \"message_id\": \"393634c9-c1f7-4017-9ec5-ecd568047003\",\r\n"
			+ "  \"timestamp\": \"2025-04-04T01:18:37Z\",\r\n"
			+ "  \"type\": \"Donation\",\r\n"
			+ "  \"is_public\": true,\r\n"
			+ "  \"from_name\": \"Jo Example\",\r\n"
			+ "  \"message\": \"Good luck with the integration!\",\r\n"
			+ "  \"amount\": \"10.00\",\r\n"
			+ "  \"url\": \"https://ko-fi.com/Home/CoffeeShop?txid=00000000-1111-2222-3333-444444444444\",\r\n"
			+ "  \"email\": \"jo.example@example.com\",\r\n"
			+ "  \"currency\": \"EUR\",\r\n"
			+ "  \"is_subscription_payment\": false,\r\n"
			+ "  \"is_first_subscription_payment\": false,\r\n"
			+ "  \"kofi_transaction_id\": \"00000000-1111-2222-3333-444444444444\",\r\n"
			+ "  \"shop_items\": null,\r\n"
			+ "  \"tier_name\": null,\r\n"
			+ "  \"shipping\": null\r\n"
			+ "}")
		.post("/api/kofi/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(getDonations())
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		})
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("kofi");
			assertThat(d.getPlatformId()).isEqualTo("393634c9-c1f7-4017-9ec5-ecd568047003");
			assertThat(d.getAmount()).isEqualTo(3000000);
			assertThat(d.getRealAmount()).isEqualTo(3000000);
			assertThat(d.getEmail()).isEqualTo("jo.example@example.com");
		});
		
		assertThat(getStatus()).satisfies(status -> {
			assertThat(status.getCurrentDonations()).isEqualTo(1490000 + 3000000);
			assertThat(status.getGoals()).hasSize(2)
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(1);
				assertThat(goal.getType()).isEqualTo("type1");
				assertThat(goal.getAmount()).isEqualTo(1000);
			})
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(2);
				assertThat(goal.getType()).isEqualTo("type2");
				assertThat(goal.getAmount()).isEqualTo(2000);
			});
		});
	}
	
	@Order(2000)
	@Test
	void updateDonation() {
		var kofiDonation = getDonations().stream().filter(d -> "kofi".equals(d.getPlatform())).findAny().get();
		var response = test.asAdmin().put("/api/donation/v1/" + kofiDonation.getUuid(),  new Donation(
			kofiDonation.getUuid(),
			kofiDonation.getPlatform(),
			kofiDonation.getPlatformId(),
			kofiDonation.getTimestamp(),
			kofiDonation.getAmount(),
			1,
			kofiDonation.getEmail(),
			kofiDonation.getDetails()
		));
		assertThat(response.statusCode()).isEqualTo(200);
		
		assertThat(getDonations())
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getPlatform()).isEqualTo("test");
			assertThat(d.getPlatformId()).isEqualTo("test1");
			assertThat(d.getAmount()).isEqualTo(1500000);
			assertThat(d.getRealAmount()).isEqualTo(1490000);
			assertThat(d.getEmail()).isEqualTo("generous@trailence.org");
		})
		.satisfiesOnlyOnce(d -> {
			assertThat(d.getUuid()).isEqualTo(kofiDonation.getUuid());
			assertThat(d.getPlatform()).isEqualTo(kofiDonation.getPlatform());
			assertThat(d.getPlatformId()).isEqualTo(kofiDonation.getPlatformId());
			assertThat(d.getAmount()).isEqualTo(kofiDonation.getAmount());
			assertThat(d.getRealAmount()).isEqualTo(1);
			assertThat(d.getEmail()).isEqualTo(kofiDonation.getEmail());
			assertThat(d.getDetails()).isEqualTo(kofiDonation.getDetails());
		});
		
		assertThat(getStatus()).satisfies(status -> {
			assertThat(status.getCurrentDonations()).isEqualTo(1490000 + 1);
			assertThat(status.getGoals()).hasSize(2)
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(1);
				assertThat(goal.getType()).isEqualTo("type1");
				assertThat(goal.getAmount()).isEqualTo(1000);
			})
			.satisfiesOnlyOnce(goal -> {
				assertThat(goal.getIndex()).isEqualTo(2);
				assertThat(goal.getType()).isEqualTo("type2");
				assertThat(goal.getAmount()).isEqualTo(2000);
			});
		});
	}
	
	private DonationStatus getStatus() {
		var response = RestAssured.given().get("/api/donation/v1/status");
		assertThat(response.statusCode()).isEqualTo(200);
		return response.as(DonationStatus.class);
	}
	
	private List<Donation> getDonations() {
		var response = test.asAdmin().get("/api/donation/v1");
		assertThat(response.statusCode()).isEqualTo(200);
		return response.as(new TypeRef<PageResult<Donation>>() {}).getElements();
	}
	
}
