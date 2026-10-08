package com.peachhacks.backend.discord;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.peachhacks.backend.config.DiscordProperties;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** The few calls PeachBot makes to Discord's REST API as the bot. */
@Component
public class DiscordClient {

	private static final int MAX_ATTEMPTS = 3;

	private static final Duration LONGEST_WAIT = Duration.ofSeconds(10);

	private static final String AUDIT_REASON = "PeachBot verification";

	private final JsonMapper json = JsonMapper.builder().build();

	private final RestClient restClient;

	private final DiscordProperties properties;

	public DiscordClient(DiscordProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(5))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		this.restClient = RestClient.builder()
			.requestFactory(requestFactory)
			.baseUrl(properties.apiBaseUrl())
			.defaultHeader(HttpHeaders.USER_AGENT, "DiscordBot (https://www.peachhacks.com, 1.0)")
			.build();
		this.properties = properties;
	}

	/** Returns the id of the new message. */
	public String postMessage(String channelId, Map<String, Object> message) {
		Map<?, ?> created = withRateLimit(() -> restClient.post()
			.uri("/channels/{channel}/messages", channelId)
			.header(HttpHeaders.AUTHORIZATION, authorization())
			.contentType(MediaType.APPLICATION_JSON)
			.body(message)
			.retrieve()
			.body(Map.class));
		return (created != null) ? String.valueOf(created.get("id")) : null;
	}

	/**
	 * Posts a message with a picture attached, as multipart: the message itself travels as
	 * the payload_json part. With a null image it is an ordinary message.
	 */
	public void postMessage(String channelId, Map<String, Object> message, String fileName, byte[] image) {
		if (image == null) {
			postMessage(channelId, message);
			return;
		}
		HttpHeaders jsonPart = new HttpHeaders();
		jsonPart.setContentType(MediaType.APPLICATION_JSON);
		HttpHeaders imagePart = new HttpHeaders();
		imagePart.setContentType(MediaType.IMAGE_PNG);
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("payload_json", new HttpEntity<>(json.writeValueAsString(message), jsonPart));
		body.add("files[0]", new HttpEntity<>(new ByteArrayResource(image) {
			@Override
			public String getFilename() {
				return fileName;
			}
		}, imagePart));
		withRateLimit(() -> restClient.post()
			.uri("/channels/{channel}/messages", channelId)
			.header(HttpHeaders.AUTHORIZATION, authorization())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(body)
			.retrieve()
			.toBodilessEntity());
	}

	/** A member's profile picture from Discord's image server, which needs no credentials. */
	public byte[] avatar(String userId, String avatarHash) {
		return restClient.get()
			.uri(properties.cdnBaseUrl() + "/avatars/{user}/{hash}.png?size=256", userId, avatarHash)
			.retrieve()
			.body(byte[].class);
	}

	public void deleteMessage(String channelId, String messageId) {
		withRateLimit(() -> restClient.delete()
			.uri("/channels/{channel}/messages/{message}", channelId, messageId)
			.header(HttpHeaders.AUTHORIZATION, authorization())
			.retrieve()
			.toBodilessEntity());
	}

	public void addHackerRole(String userId) {
		withRateLimit(() -> restClient.put()
			.uri("/guilds/{guild}/members/{user}/roles/{role}", properties.guildId(), userId,
					properties.hackerRoleId())
			.header(HttpHeaders.AUTHORIZATION, authorization())
			.header("X-Audit-Log-Reason", AUDIT_REASON)
			.retrieve()
			.toBodilessEntity());
	}

	/** Someone who has left the server has no role to take away; that is not an error. */
	public void removeHackerRole(String userId) {
		try {
			withRateLimit(() -> restClient.delete()
				.uri("/guilds/{guild}/members/{user}/roles/{role}", properties.guildId(), userId,
						properties.hackerRoleId())
				.header(HttpHeaders.AUTHORIZATION, authorization())
				.header("X-Audit-Log-Reason", AUDIT_REASON)
				.retrieve()
				.toBodilessEntity());
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
				throw ex;
			}
		}
	}

	public void editMessage(String channelId, String messageId, Map<String, Object> message) {
		withRateLimit(() -> restClient.patch()
			.uri("/channels/{channel}/messages/{message}", channelId, messageId)
			.header(HttpHeaders.AUTHORIZATION, authorization())
			.contentType(MediaType.APPLICATION_JSON)
			.body(message)
			.retrieve()
			.toBodilessEntity());
	}

	/** Trades the code Discord handed the browser for a token that acts as that person. */
	public String exchangeCode(String code, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", code);
		form.add("redirect_uri", redirectUri);
		Map<?, ?> token = restClient.post()
			.uri("/oauth2/token")
			.headers(headers -> headers.setBasicAuth(properties.applicationId(), properties.clientSecret()))
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(Map.class);
		return (token != null) ? String.valueOf(token.get("access_token")) : null;
	}

	public record User(String id, String username) {
	}

	public User currentUser(String accessToken) {
		Map<?, ?> user = restClient.get()
			.uri("/users/@me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.retrieve()
			.body(Map.class);
		return (user != null) ? new User(String.valueOf(user.get("id")), String.valueOf(user.get("username"))) : null;
	}

	/**
	 * Adds the person to the server with the Hacker role. Discord ignores the roles when
	 * they are a member already, so the caller still gives the role afterwards.
	 */
	public void joinGuild(String userId, String accessToken) {
		withRateLimit(() -> restClient.put()
			.uri("/guilds/{guild}/members/{user}", properties.guildId(), userId)
			.header(HttpHeaders.AUTHORIZATION, authorization())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("access_token", accessToken, "roles", List.of(properties.hackerRoleId())))
			.retrieve()
			.toBodilessEntity());
	}

	private String authorization() {
		return "Bot " + properties.botToken();
	}

	private static <T> T withRateLimit(Supplier<T> call) {
		for (int attempt = 1;; attempt++) {
			try {
				return call.get();
			}
			catch (RestClientResponseException ex) {
				if (ex.getStatusCode().value() != HttpStatus.TOO_MANY_REQUESTS.value() || attempt == MAX_ATTEMPTS) {
					throw ex;
				}
				pause(retryAfter(ex));
			}
		}
	}

	private static Duration retryAfter(RestClientResponseException ex) {
		HttpHeaders headers = ex.getResponseHeaders();
		String value = (headers != null) ? headers.getFirst(HttpHeaders.RETRY_AFTER) : null;
		try {
			Duration wait = Duration.ofMillis((long) (Double.parseDouble(String.valueOf(value)) * 1000) + 100);
			return (wait.compareTo(LONGEST_WAIT) > 0) ? LONGEST_WAIT : wait;
		}
		catch (NumberFormatException e) {
			return Duration.ofSeconds(1);
		}
	}

	private static void pause(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for Discord's rate limit", ex);
		}
	}

}
