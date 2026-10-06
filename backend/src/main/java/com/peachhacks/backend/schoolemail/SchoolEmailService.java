package com.peachhacks.backend.schoolemail;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.common.Tokens;
import com.peachhacks.backend.config.EmailProperties;
import com.peachhacks.backend.email.MailService;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves that a person controls the school address they gave, by mailing that address a
 * link. Confirmation is recorded per (personal email, school email) pair; pre-registrations
 * and registrations read it by matching their own two addresses.
 */
@Service
public class SchoolEmailService {

	private static final int LINK_VALID_DAYS = 14;

	private static final int RESEND_AFTER_MINUTES = 10;

	private record Person(String firstName, String schoolEmail) {
	}

	private record TokenRow(UUID confirmationId, String schoolEmail, boolean used, boolean expired) {
	}

	/**
	 * One statement decides whether a link may be mailed now, so concurrent submissions for
	 * the same pair cannot both send. It returns a row only for a new pair, or for an
	 * unconfirmed one that is outside the resend interval (or when forced).
	 */
	private static final String CLAIM_SEND = """
			insert into school_email_confirmations as c (id, email, school_email, last_sent_at)
			values (:id, :email, :schoolEmail, now())
			on conflict (email, school_email) do update set last_sent_at = now()
				where c.confirmed_at is null
					and (cast(:force as boolean) or c.last_sent_at is null
						or c.last_sent_at <= now() - make_interval(mins => cast(:minutes as integer)))
			returning c.id
			""";

	private final JdbcClient jdbc;

	private final MailService mailService;

	private final EmailProperties properties;

	private final TransactionTemplate transaction;

	public SchoolEmailService(JdbcClient jdbc, MailService mailService, EmailProperties properties,
			PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.mailService = mailService;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * Mails a link unless the pair is already confirmed or one was mailed within the resend
	 * interval. Returns whether the address still needs confirming.
	 */
	public boolean requestConfirmation(String email, String schoolEmail, String firstName) {
		if (send(email, schoolEmail, firstName, false)) {
			return true;
		}
		return !isConfirmed(email, schoolEmail);
	}

	/** For an organizer: ignores the resend interval. */
	public void sendNow(String email, String schoolEmail, String firstName) {
		if (schoolEmail == null) {
			throw ApiException.validation("There is no school email to confirm.", null);
		}
		if (!send(email, schoolEmail, firstName, true)) {
			throw ApiException.validation("This school email is already confirmed.", null);
		}
	}

	/**
	 * For the public page. Says nothing about whether the personal email is known. The
	 * registration's school address wins over the pre-registration's when they differ.
	 */
	public void resend(String personalEmail) {
		String email = Texts.email(personalEmail);
		if (email == null || email.length() > 255) {
			return;
		}
		find("registrations", email).or(() -> find("pre_registrations", email))
			.ifPresent(person -> send(email, person.schoolEmail(), person.firstName(), false));
	}

	/** Returns the confirmed school address. Repeating a used link is not an error. */
	public String confirm(String token) {
		String cleaned = Texts.clean(token);
		if (cleaned == null || cleaned.length() > 64) {
			throw invalidLink();
		}
		String tokenHash = Tokens.sha256(cleaned);
		return transaction.execute(status -> {
			TokenRow row = jdbc.sql("""
					select c.id, c.school_email, t.used_at, t.expires_at <= now() as expired
					from school_email_tokens t
					join school_email_confirmations c on c.id = t.confirmation_id
					where t.token_hash = :tokenHash
					""")
				.param("tokenHash", tokenHash)
				.query((rs, rowNum) -> new TokenRow(rs.getObject("id", UUID.class), rs.getString("school_email"),
						rs.getObject("used_at", Timestamp.class) != null, rs.getBoolean("expired")))
				.optional()
				.orElseThrow(SchoolEmailService::invalidLink);
			if (row.used()) {
				return row.schoolEmail();
			}
			if (row.expired()) {
				throw invalidLink();
			}
			jdbc.sql("update school_email_confirmations set confirmed_at = coalesce(confirmed_at, now()) where id = :id")
				.param("id", row.confirmationId())
				.update();
			jdbc.sql("update school_email_tokens set used_at = now() where token_hash = :tokenHash")
				.param("tokenHash", tokenHash)
				.update();
			return row.schoolEmail();
		});
	}

	/**
	 * Removes what is kept about a person once neither table refers to the pair any more,
	 * so deleting their records leaves no addresses behind.
	 */
	public void forget(String email) {
		jdbc.sql("""
				delete from school_email_confirmations c
				where c.email = :email
					and not exists (select 1 from registrations r
						where r.email = c.email and r.school_email = c.school_email)
					and not exists (select 1 from pre_registrations p
						where p.email = c.email and p.school_email = c.school_email)
				""").param("email", email).update();
	}

	private boolean isConfirmed(String email, String schoolEmail) {
		return jdbc.sql("""
				select exists (select 1 from school_email_confirmations
					where email = :email and school_email = :schoolEmail and confirmed_at is not null)
				""").param("email", email).param("schoolEmail", schoolEmail).query(Boolean.class).single();
	}

	private boolean send(String email, String schoolEmail, String firstName, boolean force) {
		String token = Tokens.random();
		boolean claimed = Boolean.TRUE.equals(transaction.execute(status -> {
			Optional<UUID> confirmationId = jdbc.sql(CLAIM_SEND)
				.param("id", UUID.randomUUID())
				.param("email", email)
				.param("schoolEmail", schoolEmail)
				.param("force", force)
				.param("minutes", RESEND_AFTER_MINUTES)
				.query(UUID.class)
				.optional();
			if (confirmationId.isEmpty()) {
				return false;
			}
			jdbc.sql("delete from school_email_tokens where used_at is null and expires_at <= now()").update();
			jdbc.sql("""
					insert into school_email_tokens (token_hash, confirmation_id, expires_at)
					values (:tokenHash, :confirmationId, now() + make_interval(days => cast(:days as integer)))
					""")
				.param("tokenHash", Tokens.sha256(token))
				.param("confirmationId", confirmationId.get())
				.param("days", LINK_VALID_DAYS)
				.update();
			return true;
		}));
		if (claimed) {
			mailService.sendSchoolEmailConfirmation(schoolEmail, firstName,
					properties.webBaseUrl() + "/confirm-email?token=" + token, LINK_VALID_DAYS);
		}
		return claimed;
	}

	private Optional<Person> find(String table, String email) {
		return jdbc
			.sql("select first_name, school_email from " + table + " where email = :email and school_email is not null")
			.param("email", email)
			.query((rs, rowNum) -> new Person(rs.getString("first_name"), rs.getString("school_email")))
			.optional();
	}

	private static ApiException invalidLink() {
		return ApiException.notFound("This confirmation link is not valid or has expired.");
	}

}
