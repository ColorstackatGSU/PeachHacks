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

	public List<Recipient> recipients(Audience audience, String school) {
		String schoolFilter = Texts.clean(school);
		boolean registrants = (audience == Audience.REGISTRANTS);
		String table = registrants ? "registrations" : "pre_registrations";
		String otherTable = registrants ? "pre_registrations" : "registrations";

		StringBuilder sql = new StringBuilder(
				"select t.email, t.first_name, t.last_name, t.unsubscribe_token from " + table + " t"
						+ " where t.unsubscribed = false and not exists (select 1 from " + otherTable
						+ " o where o.email = t.email and o.unsubscribed = true)");
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
