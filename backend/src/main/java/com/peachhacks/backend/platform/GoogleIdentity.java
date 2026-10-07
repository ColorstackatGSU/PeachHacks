package com.peachhacks.backend.platform;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.config.PlatformProperties;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Checks the ID token the "Sign in with Google" button hands the browser by asking
 * Google's tokeninfo endpoint, which validates the signature and expiry. What is left to
 * check here is that the token was issued to this site and that Google verified the email.
 */
@Component
public class GoogleIdentity {

	public record Account(String subject, String email) {
	}

	private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

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
		return !properties.googleClientId().isEmpty();
	}

	public Optional<Account> verify(String credential) {
		if (!enabled() || credential == null || credential.isBlank() || credential.length() > 4096) {
			return Optional.empty();
		}
		Map<?, ?> claims;
		try {
			claims = restClient.get()
				.uri(properties.googleTokenInfoUrl() + "?id_token={token}", credential)
				.retrieve()
				.body(Map.class);
		}
		catch (RestClientException ex) {
			return Optional.empty();
		}
		if (claims == null || !properties.googleClientId().equals(claims.get("aud"))
				|| !ISSUERS.contains(String.valueOf(claims.get("iss")))
				|| !"true".equals(String.valueOf(claims.get("email_verified")))) {
			return Optional.empty();
		}
		String subject = Texts.clean(String.valueOf(claims.get("sub")));
		String email = Texts.email(String.valueOf(claims.get("email")));
		return (subject != null && email != null && subject.length() <= 64) ? Optional.of(new Account(subject, email))
				: Optional.empty();
	}

}
