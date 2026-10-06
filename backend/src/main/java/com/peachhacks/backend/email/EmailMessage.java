package com.peachhacks.backend.email;

import java.util.Map;

public record EmailMessage(String to, String subject, String html, String text, Map<String, String> headers) {

	public EmailMessage {
		headers = (headers != null) ? Map.copyOf(headers) : Map.of();
	}

}
