package com.peachhacks.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * "Continue with Google" needs googleClientId and googleClientSecret; without both the
 * button is not offered. apiBaseUrl is this backend's public address, which Google sends
 * the browser back to.
 */
@ConfigurationProperties(prefix = "app.platform")
public record PlatformProperties(String baseUrl, String apiBaseUrl, String googleClientId,
		String googleClientSecret, String googleTokenUrl, Integer maxTeamSize, Duration sessionTtl) {

	public PlatformProperties {
		baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "")
				: "http://localhost:5176";
		apiBaseUrl = (apiBaseUrl != null && !apiBaseUrl.isBlank()) ? apiBaseUrl.trim().replaceAll("/+$", "")
				: "http://localhost:8080";
		googleClientId = (googleClientId != null) ? googleClientId.trim() : "";
		googleClientSecret = (googleClientSecret != null) ? googleClientSecret.trim() : "";
		googleTokenUrl = (googleTokenUrl != null && !googleTokenUrl.isBlank()) ? googleTokenUrl.trim()
				: "https://oauth2.googleapis.com/token";
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

	@Override
	public String toString() {
		return "PlatformProperties[baseUrl=" + baseUrl + ", apiBaseUrl=" + apiBaseUrl + ", maxTeamSize=" + maxTeamSize
				+ "]";
	}

}
