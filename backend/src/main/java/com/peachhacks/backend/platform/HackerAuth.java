package com.peachhacks.backend.platform;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.peachhacks.backend.admin.AuthService;
import com.peachhacks.backend.admin.LoginThrottle;
import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.RateLimiter;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.common.Tokens;
import com.peachhacks.backend.config.PlatformProperties;
import com.peachhacks.backend.email.MailService;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sign-in for hackers. Only an ACCEPTED registration can sign in, and a session stops
 * working the moment its registration leaves ACCEPTED. There is no sign-up: the account is
 * the registration, reached through its email address (a mailed link to choose a
 * password) or through a Google account with that address.
 */
@Service
public class HackerAuth {

	public record Session(String token, Instant expiresAt) {

		@Override
		public String toString() {
			return "Session[expiresAt=" + expiresAt + "]";
		}

	}

	static final int MIN_PASSWORD_LENGTH = 10;

	static final Duration LINK_VALID_FOR = Duration.ofHours(1);

	private static final Duration LINK_INTERVAL = Duration.ofMinutes(2);

	private static final String BEARER = "Bearer ";

	private static final String THROTTLE_PREFIX = "hacker:";

	private record Credentials(UUID id, String passwordHash) {
	}

	private record Person(UUID id, String firstName) {
	}

	private final JdbcClient jdbc;

	private final PasswordEncoder passwordEncoder;

	private final LoginThrottle throttle;

	private final RateLimiter rateLimiter;

	private final MailService mailService;

	private final GoogleIdentity google;

	private final PlatformProperties properties;

	private final TransactionTemplate transaction;

	private final String dummyHash;

	public HackerAuth(JdbcClient jdbc, PasswordEncoder passwordEncoder, LoginThrottle throttle,
			RateLimiter rateLimiter, MailService mailService, GoogleIdentity google, PlatformProperties properties,
			PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
		this.throttle = throttle;
		this.rateLimiter = rateLimiter;
		this.mailService = mailService;
		this.google = google;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
		this.dummyHash = passwordEncoder.encode(Tokens.random());
	}

	/** The id of the signed-in hacker's registration. */
	public UUID require(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			Optional<UUID> id = jdbc.sql("""
					select s.registration_id from hacker_sessions s
					join registrations r on r.id = s.registration_id
					where s.token_hash = :tokenHash and s.expires_at > now() and r.status = 'ACCEPTED'
					""")
				.param("tokenHash", Tokens.sha256(header.substring(BEARER.length()).trim()))
				.query(UUID.class)
				.optional();
			if (id.isPresent()) {
				return id.get();
			}
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in to continue.");
	}

	/**
	 * Answers the same whether or not the email belongs to an accepted hacker, and whether
	 * or not they have chosen a password yet.
	 */
	public Session login(String typedEmail, String password) {
		String email = Texts.orEmpty(Texts.email(typedEmail));
		String key = THROTTLE_PREFIX + email;
		if (throttle.blocked(key)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
					"Too many failed sign-ins for this email. Try again in " + throttle.windowMinutes() + " minutes.");
		}
		Optional<Credentials> found = jdbc.sql("""
				select r.id, a.password_hash from registrations r
				join hacker_accounts a on a.registration_id = r.id
				where r.email = :email and r.status = 'ACCEPTED' and a.password_hash is not null
				""")
			.param("email", email)
			.query((rs, rowNum) -> new Credentials(rs.getObject("id", UUID.class), rs.getString("password_hash")))
			.optional();
		String candidate = (password != null && AuthService.fitsBcrypt(password)) ? password : "";
		boolean matches = passwordEncoder.matches(candidate, found.map(Credentials::passwordHash).orElse(dummyHash));
		if (found.isEmpty() || !matches) {
			throttle.failed(key);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
					"That email and password do not match. If you have not chosen a password yet, use the email link"
							+ " below.");
		}
		throttle.clear(key);
		return startSession(found.get().id());
	}

	/** Says nothing about whether the email is known. */
	public void requestPasswordLink(String typedEmail) {
		String email = Texts.email(typedEmail);
		if (email == null || email.length() > 255) {
			return;
		}
		Optional<Person> person = jdbc
			.sql("select id, first_name from registrations where email = :email and status = 'ACCEPTED'")
			.param("email", email)
			.query((rs, rowNum) -> new Person(rs.getObject("id", UUID.class), rs.getString("first_name")))
			.optional();
		if (person.isEmpty() || !rateLimiter.tryAcquire("platform-link:" + email, 1, LINK_INTERVAL)) {
			return;
		}
		String token = Tokens.random();
		jdbc.sql("delete from hacker_password_tokens where expires_at <= now()").update();
		jdbc.sql("""
				insert into hacker_password_tokens (token_hash, registration_id, expires_at)
				values (:tokenHash, :id, now() + make_interval(mins => cast(:minutes as integer)))
				""")
			.param("tokenHash", Tokens.sha256(token))
			.param("id", person.get().id())
			.param("minutes", LINK_VALID_FOR.toMinutes())
			.update();
		mailService.sendPlatformPasswordLink(email, person.get().firstName(),
				properties.baseUrl() + "/#/set-password?token=" + token, LINK_VALID_FOR);
	}

	/** Signs the person in, and signs out every other device that was using the old password. */
	public Session setPassword(String token, String password) {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw ApiException.invalidField("password", "Use at least " + MIN_PASSWORD_LENGTH + " characters.");
		}
		if (!AuthService.fitsBcrypt(password)) {
			throw ApiException.invalidField("password",
					"Password is too long. Accented letters and emoji count more than once towards the limit of 72.");
		}
		String cleaned = Texts.orEmpty(token);
		return transaction.execute(tx -> {
			UUID id = jdbc.sql("""
					update hacker_password_tokens t set used_at = now()
					from registrations r
					where t.token_hash = :tokenHash and t.used_at is null and t.expires_at > now()
						and r.id = t.registration_id and r.status = 'ACCEPTED'
					returning t.registration_id
					""")
				.param("tokenHash", Tokens.sha256(cleaned))
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> ApiException.notFound("This link is not valid or has expired. Ask for a new one."));
			ensureAccount(id);
			jdbc.sql("update hacker_accounts set password_hash = :hash where registration_id = :id")
				.param("hash", passwordEncoder.encode(password))
				.param("id", id)
				.update();
			jdbc.sql("delete from hacker_sessions where registration_id = :id").param("id", id).update();
			return startSession(id);
		});
	}

	/**
	 * The Google account's address must be the one the person applied with, or their school
	 * address once they have confirmed it.
	 */
	public Session googleSignIn(String credential) {
		GoogleIdentity.Account account = google.verify(credential)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
					"Google sign-in did not work. Try again."));
		UUID id = jdbc.sql("""
				select r.id from registrations r
				where r.status = 'ACCEPTED' and (r.email = :email or (r.school_email = :email and exists (
					select 1 from school_email_confirmations c
					where c.email = r.email and c.school_email = r.school_email and c.confirmed_at is not null)))
				order by (r.email = :email) desc, r.created_at
				limit 1
				""")
			.param("email", account.email())
			.query(UUID.class)
			.optional()
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOT_ACCEPTED",
					"There is no accepted PeachHacks application for " + account.email()
							+ ". Sign in with the email you applied with."));
		return transaction.execute(tx -> {
			ensureAccount(id);
			jdbc.sql("update hacker_accounts set google_subject = null where google_subject = :subject and registration_id <> :id")
				.param("subject", account.subject())
				.param("id", id)
				.update();
			jdbc.sql("update hacker_accounts set google_subject = :subject where registration_id = :id")
				.param("subject", account.subject())
				.param("id", id)
				.update();
			return startSession(id);
		});
	}

	public void logout(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			jdbc.sql("delete from hacker_sessions where token_hash = :tokenHash")
				.param("tokenHash", Tokens.sha256(header.substring(BEARER.length()).trim()))
				.update();
		}
	}

	/** The LinkedIn address from the registration form is the starting point for the profile. */
	private void ensureAccount(UUID id) {
		jdbc.sql("""
				insert into hacker_accounts (registration_id, linkedin_url)
				select r.id, left(r.linkedin_url, 200) from registrations r where r.id = :id
				on conflict (registration_id) do nothing
				""").param("id", id).update();
	}

	private Session startSession(UUID id) {
		String token = Tokens.random();
		Instant expiresAt = Instant.now().plus(properties.sessionTtl());
		jdbc.sql("delete from hacker_sessions where expires_at <= now()").update();
		jdbc.sql("""
				insert into hacker_sessions (token_hash, registration_id, expires_at)
				values (:tokenHash, :id, :expiresAt)
				""")
			.param("tokenHash", Tokens.sha256(token))
			.param("id", id)
			.param("expiresAt", Timestamp.from(expiresAt))
			.update();
		return new Session(token, expiresAt);
	}

}
