package com.peachhacks.backend.platform;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.config.PlatformProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The Google half of "Continue with Google": where to send the browser, and turning the
 * code Google sends back into a verified email address. The ID token comes straight from
 * Google's token endpoint over TLS in exchange for our client secret, so its signature
 * does not need checking again; its audience, issuer, nonce and expiry still do.
 */
@Component
public class GoogleIdentity {

	public record Account(String subject, String email) {
	}

	private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

	private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

	private final JsonMapper json = JsonMapper.builder().build();

	private final RestClient restClient;

	private final PlatformProperties properties;

	public GoogleIdentity(PlatformProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(5))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.properties = properties;
	}

	public boolean enabled() {
		return !properties.googleClientId().isEmpty() && !properties.googleClientSecret().isEmpty();
	}

	/** Must match an "Authorized redirect URI" of the OAuth client character for character. */
	public String redirectUri() {
		return properties.apiBaseUrl() + "/platform/auth/google/callback";
	}

	/**
	 * People often have several Google accounts signed in and only one of them is the
	 * address they applied with, so Google is told to ask which one.
	 */
	public String authorizationUrl(String state, String nonce) {
		return UriComponentsBuilder.fromUriString(AUTH_ENDPOINT)
			.queryParam("client_id", properties.googleClientId())
			.queryParam("redirect_uri", redirectUri())
			.queryParam("response_type", "code")
			.queryParam("scope", "openid email")
			.queryParam("state", state)
			.queryParam("nonce", nonce)
			.queryParam("prompt", "select_account")
			.encode()
			.build()
			.toUriString();
	}

	/** Empty when Google does not vouch for a verified address that belongs to this sign-in. */
	public Optional<Account> exchange(String code, String nonce) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", code);
		form.add("client_id", properties.googleClientId());
		form.add("client_secret", properties.googleClientSecret());
		form.add("redirect_uri", redirectUri());
		JsonNode claims;
		try {
			Map<?, ?> token = restClient.post()
				.uri(properties.googleTokenUrl())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(Map.class);
			String[] parts = String.valueOf((token != null) ? token.get("id_token") : "").split("\\.");
			if (parts.length != 3) {
				return Optional.empty();
			}
			claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
		}
		catch (RestClientException | JacksonException | IllegalArgumentException ex) {
			return Optional.empty();
		}
		if (!properties.googleClientId().equals(claims.path("aud").asString(""))
				|| !ISSUERS.contains(claims.path("iss").asString(""))
				|| !nonce.equals(claims.path("nonce").asString(""))
				|| !claims.path("email_verified").asBoolean(false)
				|| claims.path("exp").asLong(0) < Instant.now().getEpochSecond()) {
			return Optional.empty();
		}
		String subject = Texts.clean(claims.path("sub").asString(""));
		String email = Texts.email(claims.path("email").asString(""));
		return (subject != null && email != null && subject.length() <= 64 && email.length() <= 255)
				? Optional.of(new Account(subject, email)) : Optional.empty();
	}

}
