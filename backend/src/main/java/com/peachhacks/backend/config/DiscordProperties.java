package com.peachhacks.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PeachBot is off unless every value except clientSecret and apiBaseUrl is present.
 * publicKey is the application's hex public key, which Discord signs every interaction
 * with. clientSecret is only needed for "Connect Discord" on the hacker platform.
 */
@ConfigurationProperties(prefix = "app.discord")
public record DiscordProperties(String botToken, String publicKey, String applicationId, String clientSecret,
		String guildId, String hackerRoleId, String verificationChannelId, String apiBaseUrl) {

	public DiscordProperties {
		botToken = trim(botToken);
		publicKey = trim(publicKey);
		applicationId = trim(applicationId);
		clientSecret = trim(clientSecret);
		guildId = trim(guildId);
		hackerRoleId = trim(hackerRoleId);
		verificationChannelId = trim(verificationChannelId);
		apiBaseUrl = trim(apiBaseUrl).isEmpty() ? "https://discord.com/api/v10" : trim(apiBaseUrl).replaceAll("/+$", "");
	}

	public boolean configured() {
		return !botToken.isEmpty() && !publicKey.isEmpty() && !applicationId.isEmpty() && !guildId.isEmpty()
				&& !hackerRoleId.isEmpty() && !verificationChannelId.isEmpty();
	}

	public boolean oauthConfigured() {
		return configured() && !clientSecret.isEmpty();
	}

	private static String trim(String value) {
		return (value != null) ? value.trim() : "";
	}

	@Override
	public String toString() {
		return "DiscordProperties[guildId=" + guildId + ", hackerRoleId=" + hackerRoleId + ", configured="
				+ configured() + "]";
	}

}
