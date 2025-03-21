package org.trailence.donations.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.trailence.donations.DonationService;
import org.trailence.donations.dto.CreateDonationRequest;
import org.trailence.global.exceptions.ForbiddenException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/kofi/v1")
@RequiredArgsConstructor
public class KofiV1Controller {
	
	private final DonationService service;
	@Value("${trailence.external.kofi.verificationToken:}")
	private String token;

	// doc: https://ko-fi.com/manage/webhooks?src=sidemenu
	
	@PostMapping(consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
	public Mono<Void> kofiPaymentReceived(KofiBody data) {
		KofiData k;
		try {
			k = new ObjectMapper().readValue(data.data, KofiData.class);
			if (token != null && !token.isBlank() && !token.equals(k.verificationToken))
				return Mono.error(new ForbiddenException());
			CreateDonationRequest request = new CreateDonationRequest(
				"kofi", k.messageId, null,
				k.amount, k.currency,
				k.amount, k.currency,
				k.email, data.data
			);
			return service.createDonation(request).then();
		} catch (Exception e) {
			return Mono.error(e);
		}
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class KofiBody {
		@BindParam("data")
		private String data;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class KofiData {
		@JsonProperty("verification_token")
		private String verificationToken;
		@JsonProperty("message_id")
		private String messageId;
		private String timestamp;
		private String type;
		@JsonProperty("is_public")
		private Boolean isPublic;
		@JsonProperty("from_name")
		private String fromName;
		private String message;
		private String amount;
		private String url;
		private String email;
		private String currency;
		@JsonProperty("is_subscription_payment")
		private Boolean isSubscription;
		@JsonProperty("is_first_subscription_payment")
		private Boolean isFirstSubscription;
		@JsonProperty("kofi_transaction_id")
		private String kofiTransactionId;
	}
	
}
