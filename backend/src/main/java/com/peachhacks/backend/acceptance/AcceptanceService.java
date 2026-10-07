package com.peachhacks.backend.acceptance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.peachhacks.backend.config.AcceptanceProperties;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptanceService {

	/** acceptanceRate is accepted / registrations, null while there are no registrations. */
	public record Totals(long registrations, long accepted, long acceptedNotified, long acceptedWaiting, long pending,
			long waitlisted, long rejected, Double acceptanceRate) {
	}

	public record HostSchool(String name, BigDecimal target) {
	}

	/**
	 * accepted is the number organizers steer before the event; checkedIn (general
	 * check-in, any status) is who actually came. registrations and pending show what is
	 * still achievable.
	 */
	public record Shares(HostSchoolShare accepted, HostSchoolShare registrations, HostSchoolShare pending,
			HostSchoolShare checkedIn) {
	}

	public record SchoolCount(String school, long count, boolean host) {
	}

	/**
	 * total is every registration that needs an age review; accepted is how many of those
	 * are ACCEPTED now, which are the ones for organizers to look at.
	 */
	public record AgeReviewCounts(int minimumAge, long total, long accepted) {
	}

	public record Summary(Totals totals, HostSchool hostSchool, Shares shares, List<SchoolCount> acceptedBySchool,
			AgeReviewCounts ageReview, AcceptanceMailer.SendStatus send) {
	}

	/** acceptedAt is null for rows accepted before it was recorded. */
	public record Waiting(UUID id, String firstName, String lastName, String email, String school, boolean host,
			boolean ageReview, Instant acceptedAt) {
	}

	// Compared against the start of the trimmed, lower-cased school name, so the host's
	// other campuses ("... Perimeter College") count without a LIKE pattern to escape.
	static final String IS_HOST = "left(lower(trim(r.school)), :hostLength) = :host";

	private final JdbcClient jdbc;

	private final AcceptanceMailer mailer;

	private final AcceptanceProperties properties;

	private final String host;

	public AcceptanceService(JdbcClient jdbc, AcceptanceMailer mailer, AcceptanceProperties properties) {
		this.jdbc = jdbc;
		this.mailer = mailer;
		this.properties = properties;
		this.host = properties.hostSchoolName().toLowerCase(Locale.ROOT);
	}

	@Transactional(readOnly = true)
	public Summary summary() {
		BigDecimal target = properties.hostSchoolTarget();
		long[] n = jdbc.sql("""
				select count(*),
					count(*) filter (where status = 'ACCEPTED'),
					count(*) filter (where status = 'ACCEPTED' and notified),
					count(*) filter (where status = 'PENDING'),
					count(*) filter (where status = 'WAITLISTED'),
					count(*) filter (where status = 'REJECTED'),
					count(*) filter (where host),
					count(*) filter (where host and status = 'ACCEPTED'),
					count(*) filter (where host and status = 'PENDING'),
					count(*) filter (where checked_in),
					count(*) filter (where host and checked_in),
					count(*) filter (where age_review),
					count(*) filter (where age_review and status = 'ACCEPTED')
				from (
					select r.status, r.acceptance_notified_at is not null as notified, %s as host,
						%s as age_review,
						exists (select 1 from check_ins c join events e on e.id = c.event_id
							where e.general and c.registration_id = r.id) as checked_in
					from registrations r
				) x
				""".formatted(IS_HOST, AgeReview.NEEDED))
			.param("host", host)
			.param("hostLength", host.length())
			.param("minimumAge", properties.nonHostMinimumAge())
			.query((rs, rowNum) -> {
			long[] counts = new long[13];
			for (int i = 0; i < counts.length; i++) {
				counts[i] = rs.getLong(i + 1);
			}
			return counts;
		}).single();
		long registrations = n[0];
		long accepted = n[1];
		Totals totals = new Totals(registrations, accepted, n[2], accepted - n[2], n[3], n[4], n[5],
				(registrations > 0) ? (double) accepted / registrations : null);
		Shares shares = new Shares(HostSchoolShare.of(n[7], accepted, target),
				HostSchoolShare.of(n[6], registrations, target), HostSchoolShare.of(n[8], n[3], target),
				HostSchoolShare.of(n[10], n[9], target));
		List<SchoolCount> bySchool = jdbc.sql("""
				select r.school, count(*) as total, %s as host from registrations r
				where r.status = 'ACCEPTED'
				group by r.school
				order by total desc, r.school asc
				""".formatted(IS_HOST))
			.param("host", host)
			.param("hostLength", host.length())
			.query((rs, rowNum) -> new SchoolCount(rs.getString("school"), rs.getLong("total"), rs.getBoolean("host")))
			.list();
		return new Summary(totals, new HostSchool(properties.hostSchoolName(), target), shares, bySchool,
				new AgeReviewCounts(properties.nonHostMinimumAge(), n[11], n[12]), mailer.status());
	}

	/** Everyone accepted and not yet told, longest-waiting first. */
	public List<Waiting> waiting() {
		return jdbc.sql("""
				select r.id, r.first_name, r.last_name, r.email, r.school, %s as host, %s as age_review,
					r.accepted_at
				from registrations r
				where r.status = 'ACCEPTED' and r.acceptance_notified_at is null
				order by r.accepted_at asc nulls first, lower(r.last_name), lower(r.first_name), r.id
				""".formatted(IS_HOST, AgeReview.NEEDED))
			.param("host", host)
			.param("hostLength", host.length())
			.param("minimumAge", properties.nonHostMinimumAge())
			.query((rs, rowNum) -> {
				OffsetDateTime acceptedAt = rs.getObject("accepted_at", OffsetDateTime.class);
				return new Waiting(rs.getObject("id", UUID.class), rs.getString("first_name"),
						rs.getString("last_name"), rs.getString("email"), rs.getString("school"),
						rs.getBoolean("host"), rs.getBoolean("age_review"),
						(acceptedAt != null) ? acceptedAt.toInstant() : null);
			})
			.list();
	}

}
