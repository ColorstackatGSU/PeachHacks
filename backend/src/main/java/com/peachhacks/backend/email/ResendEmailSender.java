package com.peachhacks.backend.email;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

public class ResendEmailSender implements EmailSender {

	private static final Duration RATE_LIMIT_BACKOFF = Duration.ofMillis(1500);

	private final RestClient restClient;

	private final String from;

	public ResendEmailSender(String apiKey, String from, String baseUrl) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(15));
		this.restClient = RestClient.builder()
			.requestFactory(requestFactory)
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.build();
		this.from = from;
	}

	@Override
	public void send(EmailMessage message) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("from", from);
		payload.put("to", List.of(message.to()));
		payload.put("subject", message.subject());
		payload.put("html", message.html());
		payload.put("text", message.text());
		if (!message.headers().isEmpty()) {
			payload.put("headers", message.headers());
		}
		try {
			post(payload);
		}
		catch (HttpClientErrorException.TooManyRequests ex) {
			pause();
			post(payload);
		}
	}

	private void post(Map<String, Object> payload) {
		restClient.post()
			.uri("/emails")
			.contentType(MediaType.APPLICATION_JSON)
			.body(payload)
			.retrieve()
			.toBodilessEntity();
	}

	private static void pause() {
		try {
			Thread.sleep(RATE_LIMIT_BACKOFF);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting to retry", ex);
		}
	}

}
