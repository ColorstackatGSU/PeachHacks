package com.peachhacks.backend.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.peachhacks.backend.registration.RegistrationStatus;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

	public record SchoolCount(String school, long count) {
	}

	public record DayCount(String date, long count) {
	}

	public record LabelCount(String label, long count) {
	}

	public record PreRegistrationStats(long total, long unsubscribed, List<SchoolCount> bySchool,
			List<DayCount> byDay) {
	}

	/** resumeOptIn counts the uploaded resumes whose owner agreed to share them with sponsors. */
	public record RegistrationStats(long total, long checkedIn, long withResume, long resumeOptIn,
			List<SchoolCount> bySchool, List<DayCount> byDay, List<LabelCount> byLevelOfStudy,
			List<LabelCount> byStatus) {
	}

	public record EventCount(UUID eventId, String name, long checkedIn) {
	}

	/** registrations.checkedIn counts the general event; events lists every event, general first. */
	public record Stats(boolean registrationOpen, PreRegistrationStats preRegistrations,
			RegistrationStats registrations, long preRegisteredNotRegistered, List<EventCount> events) {
	}

	private final JdbcClient jdbc;

	private final SettingsService settings;

	public StatsService(JdbcClient jdbc, SettingsService settings) {
		this.jdbc = jdbc;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public Stats stats() {
		PreRegistrationStats preRegistrations = new PreRegistrationStats(count("select count(*) from pre_registrations"),
				count("select count(*) from pre_registrations where unsubscribed = true"),
				bySchool("pre_registrations"), byDay("pre_registrations"));

		Map<String, Long> statusCounts = new HashMap<>();
		for (LabelCount row : byLabel("status")) {
			statusCounts.put(row.label(), row.count());
		}
		List<LabelCount> byStatus = new ArrayList<>();
		for (RegistrationStatus status : RegistrationStatus.values()) {
			byStatus.add(new LabelCount(status.name(), statusCounts.getOrDefault(status.name(), 0L)));
		}
		RegistrationStats registrations = new RegistrationStats(count("select count(*) from registrations"),
				count("select count(*) from check_ins c join events e on e.id = c.event_id where e.general"),
				count("select count(*) from registration_resumes"),
				count("select count(*) from registration_resumes where sponsor_opt_in"),
				bySchool("registrations"), byDay("registrations"), byLabel("level_of_study"), byStatus);

		long preRegisteredNotRegistered = count("""
				select count(*) from pre_registrations p
				where not exists (select 1 from registrations r where r.email = p.email)
				""");
		List<EventCount> events = jdbc.sql("""
				select e.id, e.name, count(c.id) as total from events e
				left join check_ins c on c.event_id = e.id
				group by e.id, e.name, e.general, e.starts_at
				order by e.general desc, e.starts_at asc nulls last, lower(e.name) asc
				""")
			.query((rs, rowNum) -> new EventCount(rs.getObject("id", UUID.class), rs.getString("name"),
					rs.getLong("total")))
			.list();
		return new Stats(settings.isRegistrationOpen(), preRegistrations, registrations, preRegisteredNotRegistered,
				events);
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private List<SchoolCount> bySchool(String table) {
		return jdbc
			.sql("select school, count(*) as total from " + table + " group by school order by total desc, school asc")
			.query((rs, rowNum) -> new SchoolCount(rs.getString("school"), rs.getLong("total")))
			.list();
	}

	private List<DayCount> byDay(String table) {
		return jdbc
			.sql("select to_char(created_at at time zone 'UTC', 'YYYY-MM-DD') as day, count(*) as total from " + table
					+ " group by day order by day asc")
			.query((rs, rowNum) -> new DayCount(rs.getString("day"), rs.getLong("total")))
			.list();
	}

	private List<LabelCount> byLabel(String column) {
		return jdbc
			.sql("select " + column + " as label, count(*) as total from registrations group by " + column
					+ " order by total desc, label asc")
			.query((rs, rowNum) -> new LabelCount(rs.getString("label"), rs.getLong("total")))
			.list();
	}

}
