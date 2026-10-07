package com.peachhacks.backend.email;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudienceService {

	public record Recipient(String email, String firstName, String lastName, String unsubscribeToken) {
	}

	private final JdbcClient jdbc;

	public AudienceService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Only an announcement leaves out people who unsubscribed. */
	public List<Recipient> recipients(CampaignKind kind, Audience audience, String school) {
		if (!kind.allows(audience)) {
			throw ApiException.invalidField("audience",
					"An event update can only go to people who registered. Choose registrants or accepted hackers.");
		}
		String schoolFilter = Texts.clean(school);
		String table = audience.registered() ? "registrations" : "pre_registrations";
		String otherTable = audience.registered() ? "pre_registrations" : "registrations";

		StringBuilder sql = new StringBuilder(
				"select t.email, t.first_name, t.last_name, t.unsubscribe_token from " + table + " t where true");
		if (kind == CampaignKind.ANNOUNCEMENT) {
			sql.append(" and t.unsubscribed = false and not exists (select 1 from " + otherTable
					+ " o where o.email = t.email and o.unsubscribed = true)");
		}
		if (audience == Audience.ACCEPTED) {
			sql.append(" and t.status = 'ACCEPTED' and t.acceptance_notified_at is not null");
		}
		if (audience == Audience.PRE_REGISTRANTS_NOT_REGISTERED) {
			sql.append(" and not exists (select 1 from registrations r where r.email = t.email)");
		}
		if (schoolFilter != null) {
			sql.append(" and t.school = :school");
		}
		sql.append(" order by t.created_at, t.id");

		JdbcClient.StatementSpec statement = jdbc.sql(sql.toString());
		if (schoolFilter != null) {
			statement = statement.param("school", schoolFilter);
		}
		List<Recipient> rows = statement
			.query((rs, rowNum) -> new Recipient(rs.getString("email"), rs.getString("first_name"),
					rs.getString("last_name"), rs.getString("unsubscribe_token")))
			.list();
		Map<String, Recipient> byEmail = new LinkedHashMap<>();
		for (Recipient row : rows) {
			byEmail.putIfAbsent(row.email(), row);
		}
		return new ArrayList<>(byEmail.values());
	}

	@Transactional
	public void unsubscribe(String token) {
		String cleaned = Texts.clean(token);
		if (cleaned == null || cleaned.length() > 64) {
			throw ApiException.notFound("This unsubscribe link is not valid.");
		}
		List<String> emails = jdbc
			.sql("select email from pre_registrations where unsubscribe_token = :token"
					+ " union select email from registrations where unsubscribe_token = :token")
			.param("token", cleaned)
			.query(String.class)
			.list();
		if (emails.isEmpty()) {
			throw ApiException.notFound("This unsubscribe link is not valid.");
		}
		for (String email : emails) {
			jdbc.sql("update pre_registrations set unsubscribed = true where email = :email")
				.param("email", email)
				.update();
			jdbc.sql("update registrations set unsubscribed = true where email = :email")
				.param("email", email)
				.update();
		}
	}

}
