package org.trailence.global.rest;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.trailence.global.TrailenceUtils;

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
	
	public String generate(TokenData data) throws Exception {
		SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
		Mac mac = Mac.getInstance("HmacSHA512");
		mac.init(secretKeySpec);
		byte[] dataBytes = TrailenceUtils.mapper.writeValueAsBytes(data);
		byte[] signature = mac.doFinal(dataBytes);
		return Base64.getUrlEncoder().encodeToString(dataBytes) + "." + Base64.getUrlEncoder().encodeToString(signature);
	}
	
	public TokenData check(String token) throws Exception {
		int i = token.indexOf('.');
		if (i <= 0) throw new RuntimeException("Invalid token");
		byte[] dataBytes = Base64.getUrlDecoder().decode(token.substring(0, i));
		byte[] signature = Base64.getUrlDecoder().decode(token.substring(i + 1));
		SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
		Mac mac = Mac.getInstance("HmacSHA512");
		mac.init(secretKeySpec);
		if (!Arrays.equals(signature, mac.doFinal(dataBytes))) throw new RuntimeException("Invalid token");
		return TrailenceUtils.mapper.readValue(dataBytes, TokenData.class);
	}
	
}
