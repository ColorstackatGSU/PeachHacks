package com.peachhacks.backend.discord;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.peachhacks.backend.config.DiscordProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * Posts a welcome, with a picture made for the person, when someone joins the server.
 * Everyone who joins gets one, accepted or not, and gets it once: leaving and coming back
 * does not post another.
 */
@Service
public class DiscordWelcome {

	private static final Pattern SNOWFLAKE = Pattern.compile("\\d{1,32}");

	private static final Pattern AVATAR_HASH = Pattern.compile("[A-Za-z0-9_]{1,64}");

	private static final Logger log = LoggerFactory.getLogger(DiscordWelcome.class);

	private final JdbcClient jdbc;

	private final DiscordClient client;

	private final DiscordProperties properties;

	private final TaskExecutor executor;

	private volatile String lastResult;

	public DiscordWelcome(JdbcClient jdbc, DiscordClient client, DiscordProperties properties,
			@Qualifier("discordExecutor") TaskExecutor executor) {
		this.jdbc = jdbc;
		this.client = client;
		this.properties = properties;
		this.executor = executor;
	}

	/** Returns at once; the picture and the post are made in the background. */
	public void memberJoined(String userId, String username, String displayName, String avatarHash) {
		if (!properties.welcomeConfigured() || !SNOWFLAKE.matcher(userId).matches()) {
			return;
		}
		try {
			executor.execute(() -> welcome(userId, displayName.isBlank() ? username : displayName, avatarHash));
		}
		catch (TaskRejectedException ex) {
			log.warn("Discord queue is full; Discord user {} was not welcomed", userId);
		}
	}

	/** What happened to the most recent welcome, for the admin site; null before the first one. */
	public String lastResult() {
		return lastResult;
	}

	private void welcome(String userId, String name, String avatarHash) {
		int first = jdbc
			.sql("insert into discord_welcomes (discord_user_id) values (:userId) on conflict (discord_user_id) do nothing")
			.param("userId", userId)
			.update();
		if (first == 0) {
			lastResult = "Skipped the last join: that person was already welcomed once.";
			return;
		}
		Map<String, Object> message = Map.of("content",
				"Welcome to PeachHacks, <@" + userId + ">! If you have been accepted, head to <#"
						+ properties.verificationChannelId() + "> and press **Verify** to get your Hacker role.",
				"allowed_mentions", Map.of("users", List.of(userId)));
		try {
			client.postMessage(properties.welcomeChannelId(), message, "welcome.png", card(userId, name, avatarHash));
			lastResult = "The last welcome was posted.";
		}
		catch (RuntimeException ex) {
			lastResult = "The last welcome could not be posted: " + describe(ex);
			log.warn("Could not welcome Discord user {}: {}", userId, ex.toString());
			jdbc.sql("delete from discord_welcomes where discord_user_id = :userId").param("userId", userId).update();
		}
	}

	/** Null when the picture cannot be drawn; the welcome is then posted as text alone. */
	private byte[] card(String userId, String name, String avatarHash) {
		try {
			byte[] avatar = AVATAR_HASH.matcher(avatarHash).matches() ? client.avatar(userId, avatarHash) : null;
			return WelcomeCard.render(name, userId, avatar);
		}
		// Java2D fails with Errors, not only exceptions, on a machine without fonts or
		// graphics libraries; none of them may cost the person their welcome.
		catch (Throwable ex) {
			log.warn("Could not draw the welcome card for Discord user {}: {}", userId, ex.toString());
			return null;
		}
	}

	private static String describe(RuntimeException ex) {
		if (ex instanceof RestClientResponseException response) {
			String body = response.getResponseBodyAsString().strip();
			return "Discord answered " + response.getStatusCode().value() + " "
					+ ((body.length() > 200) ? body.substring(0, 200) : body);
		}
		return ex.getClass().getSimpleName();
	}

}
