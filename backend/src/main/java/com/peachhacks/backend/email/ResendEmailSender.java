package com.peachhacks.backend.email;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class ResendEmailSender implements EmailSender {

	static final int MAX_ATTEMPTS = 4;

	private static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);

	private static final int MAX_LOGGED_ERROR_LENGTH = 500;

	private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

	private final RestClient restClient;

	private final String from;

	private final Duration firstBackoff;

	public ResendEmailSender(String apiKey, String from, String baseUrl) {
		this(apiKey, from, baseUrl, FIRST_BACKOFF);
	}

	/** The wait doubles after every failed attempt, starting from firstBackoff. */
	ResendEmailSender(String apiKey, String from, String baseUrl, Duration firstBackoff) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(15));
		this.restClient = RestClient.builder()
			.requestFactory(requestFactory)
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.build();
		this.from = from;
		this.firstBackoff = firstBackoff;
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
		if (!message.attachments().isEmpty()) {
			List<Map<String, Object>> attachments = new ArrayList<>();
			for (EmailMessage.Attachment file : message.attachments()) {
				Map<String, Object> attachment = new LinkedHashMap<>();
				attachment.put("filename", file.filename());
				attachment.put("content", Base64.getEncoder().encodeToString(file.content()));
				attachment.put("content_type", file.contentType());
				if (file.contentId() != null) {
					attachment.put("content_id", file.contentId());
				}
				attachments.add(attachment);
			}
			payload.put("attachments", attachments);
		}
		// A retry after a timeout may follow a request the provider did take. The same key
		// on every attempt makes that a repeat of one send, not a second email.
		String idempotencyKey = (message.idempotencyKey() != null) ? message.idempotencyKey()
				: UUID.randomUUID().toString();
		Duration backoff = firstBackoff;
		for (int attempt = 1;; attempt++) {
			try {
				post(payload, idempotencyKey);
				return;
			}
			catch (RestClientException ex) {
				if (message.idempotencyKey() != null && alreadyTaken(ex)) {
					log.info("Resend had already taken \"{}\" to {} (idempotency key {}); not sent again",
							message.subject(), message.to(), idempotencyKey);
					return;
				}
				if (attempt == MAX_ATTEMPTS || !retryable(ex)) {
					log.warn("Resend did not take \"{}\" to {} after {} attempt(s): {}", message.subject(),
							message.to(), attempt, describe(ex));
					throw ex;
				}
				log.info("Resend attempt {} for \"{}\" to {} failed ({}); retrying in {} ms", attempt,
						message.subject(), message.to(), describe(ex), backoff.toMillis());
			}
			pause(backoff);
			backoff = backoff.multipliedBy(2);
		}
	}

	private void post(Map<String, Object> payload, String idempotencyKey) {
		restClient.post()
			.uri("/emails")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Idempotency-Key", idempotencyKey)
			.body(payload)
			.retrieve()
			.toBodilessEntity();
	}

	/**
	 * Resend answers 409 invalid_idempotent_request when a key it has already taken comes
	 * back with a different body. That happens to a ticket email composed a second time,
	 * because its Google Wallet link is signed with the current time.
	 */
	private static boolean alreadyTaken(RestClientException ex) {
		return ex instanceof RestClientResponseException response
				&& response.getStatusCode().value() == HttpStatus.CONFLICT.value()
				&& response.getResponseBodyAsString().contains("invalid_idempotent_request");
	}

	private static boolean retryable(RestClientException ex) {
		if (ex instanceof RestClientResponseException response) {
			int status = response.getStatusCode().value();
			return status == HttpStatus.TOO_MANY_REQUESTS.value() || status >= 500;
		}
		return ex instanceof ResourceAccessException;
	}

	private static String describe(RestClientException ex) {
		if (ex instanceof RestClientResponseException response) {
			String body = response.getResponseBodyAsString().strip();
			if (body.length() > MAX_LOGGED_ERROR_LENGTH) {
				body = body.substring(0, MAX_LOGGED_ERROR_LENGTH) + "...";
			}
			return "HTTP " + response.getStatusCode().value() + " " + body;
		}
		return ex.toString();
	}

	private static void pause(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting to retry", ex);
		}
	}

}
