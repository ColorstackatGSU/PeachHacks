package com.peachhacks.backend.discord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.RateLimiter;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.common.Tokens;
import com.peachhacks.backend.config.DiscordProperties;
import com.peachhacks.backend.email.MailService;
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
 * ACCEPTED. A person proves which registration is theirs by entering a code mailed to the
 * address they applied with. Entering a code from another Discord account moves the link:
 * whoever can read the inbox decides which account holds the role.
 */
@Service
public class DiscordVerification {

	public enum Outcome {

		VERIFIED, ROLE_NOT_GIVEN, WRONG_CODE, NO_CODE, NOT_ACCEPTED

	}

	public record Status(boolean configured, long verified, Instant messagePostedAt) {
	}

	static final String VERIFY_BUTTON = "peachbot:verify";

	static final int CODE_LENGTH = 6;

	static final Duration CODE_VALID_FOR = Duration.ofMinutes(15);

	private static final int MAX_ATTEMPTS = 5;

	private static final int CODES_PER_ACCOUNT = 3;

	private static final Duration ACCOUNT_WINDOW = Duration.ofMinutes(10);

	private static final int CODES_PER_EMAIL = 3;

	private static final Duration EMAIL_WINDOW = Duration.ofHours(1);

	private static final String MESSAGE_SETTING = "discord_verification_message";

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Logger log = LoggerFactory.getLogger(DiscordVerification.class);

	private record Accepted(UUID id, String firstName) {
	}

	private record Pending(UUID registrationId, String codeHash) {
	}

	private record Member(String userId, boolean accepted) {
	}

	private final JdbcClient jdbc;

	private final DiscordClient client;

	private final DiscordProperties properties;

	private final MailService mailService;

	private final RateLimiter rateLimiter;

	private final TaskExecutor executor;

	private final TransactionTemplate transaction;

	public DiscordVerification(JdbcClient jdbc, DiscordClient client, DiscordProperties properties,
			MailService mailService, RateLimiter rateLimiter, @Qualifier("discordExecutor") TaskExecutor executor,
			PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.client = client;
		this.properties = properties;
		this.mailService = mailService;
		this.rateLimiter = rateLimiter;
		this.executor = executor;
		this.transaction = new TransactionTemplate(transactionManager);
		if (properties.configured()) {
			log.info("PeachBot: on, server {}", properties.guildId());
		}
	}

	public Status status() {
		long verified = jdbc.sql("select count(*) from discord_links").query(Long.class).single();
		Instant postedAt = jdbc.sql("select updated_at from settings where key = :key")
			.param("key", MESSAGE_SETTING)
			.query(OffsetDateTime.class)
			.optional()
			.map(OffsetDateTime::toInstant)
			.orElse(null);
		return new Status(properties.configured(), verified, postedAt);
	}

	/** Posts the message with the Verify button and takes down the one posted before it. */
	public void postVerificationMessage() {
		if (!properties.configured()) {
			throw ApiException.validation("PeachBot is not set up on the server yet.", null);
		}
		String channel = properties.verificationChannelId();
		Optional<String> previous = jdbc.sql("select value from settings where key = :key")
			.param("key", MESSAGE_SETTING)
			.query(String.class)
			.optional();
		String messageId;
		try {
			messageId = client.postMessage(channel, verificationMessage());
		}
		catch (RestClientException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "DISCORD_ERROR",
					"Discord did not take the message: " + describe(ex));
		}
		jdbc.sql("""
				insert into settings (key, value) values (:key, :value)
				on conflict (key) do update set value = excluded.value, updated_at = now()
				""").param("key", MESSAGE_SETTING).param("value", channel + "/" + messageId).update();
		previous.map(value -> value.split("/")).filter(parts -> parts.length == 2).ifPresent(parts -> {
			try {
				client.deleteMessage(parts[0], parts[1]);
			}
			catch (RestClientException ex) {
				log.info("The previous verification message was not removed: {}", describe(ex));
			}
		});
	}

	private static Map<String, Object> verificationMessage() {
		return Map.of("content", """
				**Verify to get into PeachHacks**
				The hacker channels open once you verify. Press the button, enter the email you applied with, \
				then type in the 6-digit code we send to that address.

				Verification is for accepted hackers. Acceptance emails come from PeachHacks; once you have \
				yours, come back here and verify.""", "allowed_mentions", Map.of("parse", List.of()), "components",
				List.of(Map.of("type", 1, "components", List.of(
						Map.of("type", 2, "style", 1, "label", "Verify", "custom_id", VERIFY_BUTTON)))));
	}

	/**
	 * For someone who verified before (and, say, left the server and came back): gives the
	 * role again in the background, without a code. False when there is nothing to restore.
	 */
	public boolean restore(String userId) {
		boolean accepted = jdbc.sql("""
				select exists (select 1 from discord_links l join registrations r on r.id = l.registration_id
					where l.discord_user_id = :userId and r.status = 'ACCEPTED')
				""").param("userId", userId).query(Boolean.class).single();
		if (!accepted) {
			return false;
		}
		try {
			executor.execute(() -> setRole(userId, true));
			return true;
		}
		catch (TaskRejectedException ex) {
			return false;
		}
	}

	/**
	 * Mails a code when the email belongs to an accepted registration. The caller learns
	 * only whether this Discord account has asked too often, never whether the email is known.
	 */
	public boolean requestCode(String userId, String username, String typedEmail) {
		if (!rateLimiter.tryAcquire("discord-code:" + userId, CODES_PER_ACCOUNT, ACCOUNT_WINDOW)) {
			return false;
		}
		String email = Texts.email(typedEmail);
		if (email == null || email.length() > 255) {
			return true;
		}
		Optional<Accepted> accepted = jdbc
			.sql("select id, first_name from registrations where email = :email and status = 'ACCEPTED'")
			.param("email", email)
			.query((rs, rowNum) -> new Accepted(rs.getObject("id", UUID.class), rs.getString("first_name")))
			.optional();
		// The second limit stops the button being used to fill one person's inbox.
		if (accepted.isEmpty() || !rateLimiter.tryAcquire("discord-code-email:" + email, CODES_PER_EMAIL, EMAIL_WINDOW)) {
			return true;
		}
		String code = String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(1_000_000));
		jdbc.sql("""
				insert into discord_verification_codes (discord_user_id, registration_id, code_hash, expires_at)
				values (:userId, :registrationId, :codeHash, now() + make_interval(mins => cast(:minutes as integer)))
				on conflict (discord_user_id) do update set registration_id = excluded.registration_id,
					code_hash = excluded.code_hash, attempts = 0, expires_at = excluded.expires_at, created_at = now()
				""")
			.param("userId", userId)
			.param("registrationId", accepted.get().id())
			.param("codeHash", hash(userId, code))
			.param("minutes", CODE_VALID_FOR.toMinutes())
			.update();
		mailService.sendDiscordCode(email, accepted.get().firstName(), code, username, CODE_VALID_FOR);
		return true;
	}

	/** Calls Discord, so it belongs on the Discord executor, not on a request thread. */
	public Outcome complete(String userId, String username, String typedCode) {
		Optional<Pending> pending = jdbc.sql("""
				update discord_verification_codes set attempts = attempts + 1
				where discord_user_id = :userId and expires_at > now() and attempts < :maxAttempts
				returning registration_id, code_hash
				""")
			.param("userId", userId)
			.param("maxAttempts", MAX_ATTEMPTS)
			.query((rs, rowNum) -> new Pending(rs.getObject("registration_id", UUID.class), rs.getString("code_hash")))
			.optional();
		if (pending.isEmpty()) {
			return Outcome.NO_CODE;
		}
		String code = (typedCode != null) ? typedCode.replaceAll("\\s", "") : "";
		if (!MessageDigest.isEqual(hash(userId, code).getBytes(StandardCharsets.UTF_8),
				pending.get().codeHash().getBytes(StandardCharsets.UTF_8))) {
			return Outcome.WRONG_CODE;
		}
		UUID registrationId = pending.get().registrationId();
		jdbc.sql("delete from discord_verification_codes where discord_user_id = :userId")
			.param("userId", userId)
			.update();
		List<String> displaced = link(registrationId, userId, username);
		if (displaced == null) {
			return Outcome.NOT_ACCEPTED;
		}
		for (String other : displaced) {
			setRole(other, false);
		}
		log.info("Discord user {} verified as registration {}", userId, registrationId);
		return setRole(userId, true) ? Outcome.VERIFIED : Outcome.ROLE_NOT_GIVEN;
	}

	/**
	 * "Connect Discord" on the hacker platform: the person has just approved PeachBot in
	 * their browser, so Discord vouches for the account and no emailed code is needed. Puts
	 * them in the server with the role and returns their Discord username.
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
							+ " Try Connect Discord again in a minute.");
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

	private static String hash(String userId, String code) {
		return Tokens.sha256(userId + ":" + code);
	}

	private static String describe(RuntimeException ex) {
		if (ex instanceof RestClientResponseException response) {
			String body = response.getResponseBodyAsString().strip();
			return "HTTP " + response.getStatusCode().value() + " " + ((body.length() > 300) ? body.substring(0, 300) : body);
		}
		return ex.toString();
	}

}
