package com.peachhacks.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * googleClientId is the OAuth client the platform's "Sign in with Google" button uses;
 * empty turns that button off. googleTokenInfoUrl is where an ID token is checked.
 */
@ConfigurationProperties(prefix = "app.platform")
public record PlatformProperties(String baseUrl, String googleClientId, String googleTokenInfoUrl,
		Integer maxTeamSize, Duration sessionTtl) {

	public PlatformProperties {
		baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "")
				: "http://localhost:5176";
		googleClientId = (googleClientId != null) ? googleClientId.trim() : "";
		googleTokenInfoUrl = (googleTokenInfoUrl != null && !googleTokenInfoUrl.isBlank()) ? googleTokenInfoUrl.trim()
				: "https://oauth2.googleapis.com/tokeninfo";
		maxTeamSize = (maxTeamSize != null) ? maxTeamSize : 4;
		sessionTtl = (sessionTtl != null) ? sessionTtl : Duration.ofDays(30);
		if (maxTeamSize < 1) {
			throw new IllegalArgumentException("MAX_TEAM_SIZE must be at least 1");
		}
	}

	/** Where Discord sends the browser back to after "Connect Discord"; registered in the Developer Portal. */
	public String discordRedirectUri() {
		return baseUrl + "/";
	}

}
