package org.trailence.global.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Service
public class TokenService {

	@Value("${trailence.jwt.secret}")
	private String secret;
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class TokenData {
		private String type;
		private String email;
		private String data;
	}
	
	public static class InvalidTokenException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private InvalidTokenException() {
			super("Invalid token");
		}
	}
	
	private static final String ALGO = "HmacSHA512";
	
	public String generate(TokenData data) throws GeneralSecurityException, JsonProcessingException {
		SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO);
		Mac mac = Mac.getInstance(ALGO);
		mac.init(secretKeySpec);
		byte[] dataBytes = TrailenceUtils.mapper.writeValueAsBytes(data);
		byte[] signature = mac.doFinal(dataBytes);
		return Base64.getUrlEncoder().encodeToString(dataBytes) + "." + Base64.getUrlEncoder().encodeToString(signature);
	}
	
	public TokenData check(String token) throws GeneralSecurityException, IOException {
		int i = token.indexOf('.');
		if (i <= 0) throw new InvalidTokenException();
		byte[] dataBytes = Base64.getUrlDecoder().decode(token.substring(0, i));
		byte[] signature = Base64.getUrlDecoder().decode(token.substring(i + 1));
		SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO);
		Mac mac = Mac.getInstance(ALGO);
		mac.init(secretKeySpec);
		if (!Arrays.equals(signature, mac.doFinal(dataBytes))) throw new InvalidTokenException();
		return TrailenceUtils.mapper.readValue(dataBytes, TokenData.class);
	}
	
}
