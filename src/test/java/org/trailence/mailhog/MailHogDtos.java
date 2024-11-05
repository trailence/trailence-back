package org.trailence.mailhog;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MailHogDtos {

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Messages {
		private long total;
		private long start;
		private long count;
		private List<Message> items;
	}
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Message {
		@JsonProperty("ID")
		private String id;
		@JsonProperty("From")
		private Path from;
		@JsonProperty("To")
		private List<Path> to;
		@JsonProperty("Content")
		private MailContent content;
	}
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Path {
		@JsonProperty("Mailbox")
		private String mailbox;
		@JsonProperty("Domain")
		private String domain;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class MailContent {
		@JsonProperty("Headers")
		private Map<String, List<String>> headers;
		@JsonProperty("Body")
		private String body;
	}
	
}
