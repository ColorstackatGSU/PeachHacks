package com.peachhacks.backend.discord;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.config.DiscordProperties;
import com.peachhacks.backend.config.PlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gives the Hacker role in the PeachHacks Discord to people whose registration is
 * ACCEPTED. A Discord account is tied to a registration in one place only: "Connect
 * Discord" on the hacker platform, where the hacker is signed in and Discord vouches for
 * the account. The Verify button in Discord looks that link up; it never creates one.
 * Connecting a second Discord account moves the link, and the first account loses the role.
 */
@Service
public class DiscordVerification {

	public enum Check {

		VERIFIED, NOT_ACCEPTED, NOT_CONNECTED

	}

	/** message is the text of the Verify message as it will be (or was) posted. */
	public record Status(boolean configured, long verified, Instant messagePostedAt, String message) {
	}

	static final String VERIFY_BUTTON = "peachbot:verify";

	static final int MAX_MESSAGE_LENGTH = 900;

	static final String DEFAULT_MESSAGE = """
			**Verify to get into PeachHacks**
			Press Verify to open the hacker channels. If your Discord account is not connected to your \
			application yet, the button shows you where to do that on the hacker platform.

			Verification is for accepted hackers. Acceptance emails come from PeachHacks; once you have \
			yours, come back here and verify.""";

	private static final String POSTED_SETTING = "discord_verification_message";

	private static final String TEXT_SETTING = "discord_verification_text";

	private static final Logger log = LoggerFactory.getLogger(DiscordVerification.class);

	private record Member(String userId, boolean accepted) {
	}

	private final JdbcClient jdbc;

	private final DiscordClient client;

	private final DiscordProperties properties;

	private final PlatformProperties platform;

	private final TaskExecutor executor;

	private final TransactionTemplate transaction;

	public DiscordVerification(JdbcClient jdbc, DiscordClient client, DiscordProperties properties,
			PlatformProperties platform, @Qualifier("discordExecutor") TaskExecutor executor,
			PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.client = client;
		this.properties = properties;
		this.platform = platform;
		this.executor = executor;
		this.transaction = new TransactionTemplate(transactionManager);
		if (properties.configured()) {
			log.info("PeachBot: on, server {}", properties.guildId());
		}
	}

	public Status status() {
		long verified = jdbc.sql("select count(*) from discord_links").query(Long.class).single();
		Instant postedAt = jdbc.sql("select updated_at from settings where key = :key")
			.param("key", POSTED_SETTING)
			.query(OffsetDateTime.class)
			.optional()
			.map(OffsetDateTime::toInstant)
			.orElse(null);
		return new Status(properties.configured(), verified, postedAt, setting(TEXT_SETTING).orElse(DEFAULT_MESSAGE));
	}

	/** Where the Verify button sends someone whose Discord account is not connected yet. */
	public String connectUrl() {
		return platform.baseUrl() + "/#/discord";
	}

	/**
	 * Saves the text and puts it in Discord: the message that is already up is edited in
	 * place, so it keeps its position in the channel; if it is gone, a new one is posted.
	 * A null text keeps the text as it is.
	 */
	public void publishVerificationMessage(String text) {
		if (!properties.configured()) {
			throw ApiException.validation("PeachBot is not set up on the server yet.", null);
		}
		if (text != null) {
			String cleaned = Texts.clean(text.replace("\r\n", "\n"));
			if (cleaned == null) {
				throw ApiException.invalidField("message", "Write the message people will see above the Verify button.");
			}
			if (cleaned.length() > MAX_MESSAGE_LENGTH) {
				throw ApiException.invalidField("message",
						"The message can be at most " + MAX_MESSAGE_LENGTH + " characters.");
			}
			put(TEXT_SETTING, cleaned);
		}
		Map<String, Object> message = verificationMessage(setting(TEXT_SETTING).orElse(DEFAULT_MESSAGE));
		String channel = properties.verificationChannelId();
		String[] posted = setting(POSTED_SETTING).map(value -> value.split("/")).orElse(new String[0]);
		try {
			if (posted.length == 2 && posted[0].equals(channel) && edited(channel, posted[1], message)) {
				put(POSTED_SETTING, channel + "/" + posted[1]);
				return;
			}
			put(POSTED_SETTING, channel + "/" + client.postMessage(channel, message));
		}
		catch (RestClientException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "DISCORD_ERROR",
					"Discord did not take the message: " + describe(ex));
		}
		if (posted.length == 2 && !posted[0].equals(channel)) {
			try {
				client.deleteMessage(posted[0], posted[1]);
			}
			catch (RestClientException ex) {
				log.info("The verification message in the previous channel was not removed: {}", describe(ex));
			}
		}
	}

	/** False when the message no longer exists (someone deleted it in Discord). */
	private boolean edited(String channel, String messageId, Map<String, Object> message) {
		try {
			client.editMessage(channel, messageId, message);
			return true;
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
				return false;
			}
			throw ex;
		}
	}

	private static Map<String, Object> verificationMessage(String text) {
		return Map.of("content", text, "allowed_mentions", Map.of("parse", List.of()), "components",
				List.of(Map.of("type", 1, "components", List.of(
						Map.of("type", 2, "style", 1, "label", "Verify", "custom_id", VERIFY_BUTTON)))));
	}

	/**
	 * What the Verify button does: looks the Discord account up among the accounts hackers
	 * connected on the platform. For an accepted hacker the role is given in the background
	 * (again, if they left the server and came back).
	 */
	public Check verify(String userId) {
		Optional<Boolean> accepted = jdbc.sql("""
				select r.status = 'ACCEPTED' from discord_links l
				join registrations r on r.id = l.registration_id
				where l.discord_user_id = :userId
				""").param("userId", userId).query(Boolean.class).optional();
		if (accepted.isEmpty()) {
			return Check.NOT_CONNECTED;
		}
		if (!accepted.get()) {
			return Check.NOT_ACCEPTED;
		}
		try {
			executor.execute(() -> setRole(userId, true));
		}
		catch (TaskRejectedException ex) {
			log.error("Discord queue is full; the Hacker role was not given to Discord user {}", userId);
		}
		return Check.VERIFIED;
	}

	/**
	 * "Connect Discord" on the hacker platform: the person has just approved PeachBot in
	 * their browser, so Discord vouches for the account. Puts them in the server with the
	 * role and returns their Discord username.
	 */
	public String connect(UUID registrationId, String code, String redirectUri) {
		if (!properties.oauthConfigured()) {
			throw ApiException.validation("Connecting Discord is not set up yet.", null);
		}
		DiscordClient.User user;
		String accessToken;
		try {
			accessToken = client.exchangeCode(code, redirectUri);
			user = client.currentUser(accessToken);
		}
		catch (RestClientException ex) {
			log.info("Discord did not accept an authorization code: {}", describe(ex));
			throw ApiException.validation("Discord did not confirm the connection. Start again from Connect Discord.",
					null);
		}
		String username = (user.username().length() > 64) ? user.username().substring(0, 64) : user.username();
		List<String> displaced = link(registrationId, user.id(), username);
		if (displaced == null) {
			throw ApiException.notFound("Registration not found.");
		}
		for (String other : displaced) {
			setRole(other, false);
		}
		try {
			client.joinGuild(user.id(), accessToken);
			client.addHackerRole(user.id());
		}
		catch (RestClientException ex) {
			log.error("Discord user {} is linked to registration {} but could not be added to the server: {}",
					user.id(), registrationId, describe(ex));
			throw new ApiException(HttpStatus.BAD_GATEWAY, "DISCORD_ERROR",
					"Your Discord account is connected, but Discord would not add you to the server just now."
							+ " Join the server and press Verify there, or try Connect Discord again in a minute.");
		}
		log.info("Discord user {} connected as registration {}", user.id(), registrationId);
		return username;
	}

	/** The accounts that lost their link to make room for this one; null unless the registration is ACCEPTED. */
	private List<String> link(UUID registrationId, String userId, String username) {
		return transaction.execute(tx -> {
			// Locked so a status change made at this moment waits, then finds the link and
			// takes the role away again.
			boolean accepted = jdbc.sql("select status = 'ACCEPTED' from registrations where id = :id for update")
				.param("id", registrationId)
				.query(Boolean.class)
				.optional()
				.orElse(false);
			if (!accepted) {
				return null;
			}
			List<String> previous = jdbc.sql("""
					delete from discord_links where registration_id = :registrationId or discord_user_id = :userId
					returning discord_user_id
					""").param("registrationId", registrationId).param("userId", userId).query(String.class).list();
			jdbc.sql("""
					insert into discord_links (registration_id, discord_user_id, discord_username)
					values (:registrationId, :userId, :username)
					""")
				.param("registrationId", registrationId)
				.param("userId", userId)
				.param("username", username)
				.update();
			return previous.stream().filter(other -> !other.equals(userId)).toList();
		});
	}

	/**
	 * After a status change: linked accounts of registrations that are now ACCEPTED get the
	 * role, the others lose it. Runs in the background; the caller must have committed.
	 */
	public void syncRoles(Collection<UUID> registrationIds) {
		if (!properties.configured() || registrationIds.isEmpty()) {
			return;
		}
		List<UUID> ids = List.copyOf(registrationIds);
		try {
			executor.execute(() -> {
				List<Member> members = jdbc.sql("""
						select l.discord_user_id, r.status = 'ACCEPTED' as accepted
						from discord_links l join registrations r on r.id = l.registration_id
						where l.registration_id in (:ids)
						""")
					.param("ids", ids)
					.query((rs, rowNum) -> new Member(rs.getString("discord_user_id"), rs.getBoolean("accepted")))
					.list();
				for (Member member : members) {
					setRole(member.userId(), member.accepted());
				}
			});
		}
		catch (TaskRejectedException ex) {
			log.error("Discord queue is full; roles of {} registrations were not updated", ids.size());
		}
	}

	private boolean setRole(String userId, boolean hacker) {
		try {
			if (hacker) {
				client.addHackerRole(userId);
			}
			else {
				client.removeHackerRole(userId);
			}
			return true;
		}
		catch (RuntimeException ex) {
			log.error("Could not {} the Hacker role for Discord user {}: {}", hacker ? "give" : "remove", userId,
					describe(ex));
			return false;
		}
	}

	private Optional<String> setting(String key) {
		return jdbc.sql("select value from settings where key = :key").param("key", key).query(String.class).optional();
	}

	private void put(String key, String value) {
		jdbc.sql("""
				insert into settings (key, value) values (:key, :value)
				on conflict (key) do update set value = excluded.value, updated_at = now()
				""").param("key", key).param("value", value).update();
	}

	private static String describe(RuntimeException ex) {
		if (ex instanceof RestClientResponseException response) {
			String body = response.getResponseBodyAsString().strip();
			return "HTTP " + response.getStatusCode().value() + " " + ((body.length() > 300) ? body.substring(0, 300) : body);
		}
		return ex.toString();
	}

}
