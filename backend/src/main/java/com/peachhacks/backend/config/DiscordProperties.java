package com.peachhacks.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PeachBot is off unless botToken, publicKey, applicationId, guildId, hackerRoleId and
 * verificationChannelId are all present. publicKey is the application's hex public key,
 * which Discord signs every interaction with. clientSecret is only needed for "Connect
 * Discord" on the hacker platform, welcomeChannelId only for welcoming new members, and
 * applicationsChannelId only for telling organizers about new applications.
 */
@ConfigurationProperties(prefix = "app.discord")
public record DiscordProperties(String botToken, String publicKey, String applicationId, String clientSecret,
		String guildId, String hackerRoleId, String verificationChannelId, String welcomeChannelId,
		String applicationsChannelId, String apiBaseUrl, String gatewayUrl, String cdnBaseUrl) {

	public DiscordProperties {
		botToken = trim(botToken);
		publicKey = trim(publicKey);
		applicationId = trim(applicationId);
		clientSecret = trim(clientSecret);
		guildId = trim(guildId);
		hackerRoleId = trim(hackerRoleId);
		verificationChannelId = trim(verificationChannelId);
		welcomeChannelId = trim(welcomeChannelId);
		applicationsChannelId = trim(applicationsChannelId);
		apiBaseUrl = orDefault(apiBaseUrl, "https://discord.com/api/v10");
		gatewayUrl = orDefault(gatewayUrl, "wss://gateway.discord.gg");
		cdnBaseUrl = orDefault(cdnBaseUrl, "https://cdn.discordapp.com");
	}

	public boolean configured() {
		return !botToken.isEmpty() && !publicKey.isEmpty() && !applicationId.isEmpty() && !guildId.isEmpty()
				&& !hackerRoleId.isEmpty() && !verificationChannelId.isEmpty();
	}

	public boolean oauthConfigured() {
		return configured() && !clientSecret.isEmpty();
	}

	public boolean welcomeConfigured() {
		return configured() && !welcomeChannelId.isEmpty();
	}

	public boolean applicationsConfigured() {
		return configured() && !applicationsChannelId.isEmpty();
	}

	private static String trim(String value) {
		return (value != null) ? value.trim() : "";
	}

	private static String orDefault(String value, String fallback) {
		return trim(value).isEmpty() ? fallback : trim(value).replaceAll("/+$", "");
	}

	@Override
	public String toString() {
		return "DiscordProperties[guildId=" + guildId + ", hackerRoleId=" + hackerRoleId + ", configured="
				+ configured() + "]";
	}

}
