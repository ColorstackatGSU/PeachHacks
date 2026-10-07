package com.peachhacks.backend.email;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.peachhacks.backend.common.PageResponse;
import com.peachhacks.backend.email.AudienceService.Recipient;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Who a campaign goes to and how far the send has got, one row per person. */
@Repository
public class CampaignRecipients {

	public enum Status {

		PENDING, SENT, FAILED

	}

	public record View(String email, String firstName, String lastName, Status status, Instant sentAt) {
	}

	record Pending(long id, String email, String firstName, String lastName, String unsubscribeToken) {
	}

	private static final int MAX_ERROR_LENGTH = 500;

	private static final String COUNTS = """
			(select count(*) filter (where status = 'SENT') as sent,
				count(*) filter (where status = 'FAILED') as failed
			from campaign_recipients where campaign_id = :id) t
			""";

	private final JdbcClient jdbc;

	private final JdbcTemplate jdbcTemplate;

	public CampaignRecipients(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
		this.jdbc = jdbc;
		this.jdbcTemplate = jdbcTemplate;
	}

	void add(UUID campaignId, List<Recipient> recipients) {
		jdbcTemplate.batchUpdate("""
				insert into campaign_recipients (campaign_id, email, first_name, last_name, unsubscribe_token)
				values (?, ?, ?, ?, ?)
				""", recipients.stream()
			.map(recipient -> new Object[] { campaignId, recipient.email(), recipient.firstName(),
					recipient.lastName(), recipient.unsubscribeToken() })
			.toList());
	}

	boolean exist(UUID campaignId) {
		return jdbc.sql("select exists (select 1 from campaign_recipients where campaign_id = :id)")
			.param("id", campaignId)
			.query(Boolean.class)
			.single();
	}

	/** The next people still to be mailed, in the order they were added, after the row with id afterId. */
	List<Pending> pending(UUID campaignId, long afterId, int limit) {
		return jdbc.sql("""
				select id, email, first_name, last_name, unsubscribe_token from campaign_recipients
				where campaign_id = :id and status = 'PENDING' and id > :afterId
				order by id limit :limit
				""")
			.param("id", campaignId)
			.param("afterId", afterId)
			.param("limit", limit)
			.query((rs, rowNum) -> new Pending(rs.getLong("id"), rs.getString("email"), rs.getString("first_name"),
					rs.getString("last_name"), rs.getString("unsubscribe_token")))
			.list();
	}

	void markSent(long id) {
		jdbc.sql("update campaign_recipients set status = 'SENT', sent_at = now(), error = null where id = :id")
			.param("id", id)
			.update();
	}

	/** Never overwrites SENT: a failure reported late must not hide a delivery. */
	void markFailed(long id, String error) {
		String text = (error.length() > MAX_ERROR_LENGTH) ? error.substring(0, MAX_ERROR_LENGTH) : error;
		jdbc.sql("update campaign_recipients set status = 'FAILED', error = :error"
				+ " where id = :id and status = 'PENDING'")
			.param("id", id)
			.param("error", text)
			.update();
	}

	/** Copies the counts of SENT and FAILED rows onto the campaign and marks it SENDING. */
	void recordProgress(UUID campaignId) {
		jdbc.sql("update email_campaigns c set status = 'SENDING', sent_count = t.sent, failed_count = t.failed from "
				+ COUNTS + " where c.id = :id")
			.param("id", campaignId)
			.update();
	}

	/** SENT when at least one person was reached, as before; the counts say how many were not. */
	void complete(UUID campaignId) {
		jdbc.sql("update email_campaigns c set status = case when t.sent > 0 then 'SENT' else 'FAILED' end,"
				+ " sent_count = t.sent, failed_count = t.failed, completed_at = now() from " + COUNTS
				+ " where c.id = :id")
			.param("id", campaignId)
			.update();
	}

	/** A null status means everyone. */
	PageResponse<View> page(UUID campaignId, Status status, Pageable pageable) {
		String filter = (status != null) ? status.name() : "";
		String where = " from campaign_recipients where campaign_id = :id and (:status = '' or status = :status)";
		long total = jdbc.sql("select count(*)" + where)
			.param("id", campaignId)
			.param("status", filter)
			.query(Long.class)
			.single();
		List<View> items = jdbc
			.sql("select email, first_name, last_name, status, sent_at" + where
					+ " order by id limit :limit offset :offset")
			.param("id", campaignId)
			.param("status", filter)
			.param("limit", pageable.getPageSize())
			.param("offset", pageable.getOffset())
			.query((rs, rowNum) -> {
				OffsetDateTime sentAt = rs.getObject("sent_at", OffsetDateTime.class);
				return new View(rs.getString("email"), rs.getString("first_name"), rs.getString("last_name"),
						Status.valueOf(rs.getString("status")), (sentAt != null) ? sentAt.toInstant() : null);
			})
			.list();
		return new PageResponse<>(items, total, pageable.getPageNumber(), pageable.getPageSize());
	}

}
