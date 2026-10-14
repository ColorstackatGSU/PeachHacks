package com.peachhacks.backend.checkin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.peachhacks.backend.checkin.CheckInService.EventRef;
import com.peachhacks.backend.common.PageResponse;
import com.peachhacks.backend.common.Texts;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who has arrived and who is at which event, for staff. It is a record of who came, so
 * someone who has since left ACCEPTED still appears. Only names, school and the staff mark
 * are returned: no email, no status, no checkedInBy, for any role.
 */
@Service
public class AttendanceService {

	public record EventVisit(UUID eventId, String name, Instant checkedInAt) {
	}

	/** events are the person's check-ins other than the general one, oldest first. */
	public record Arrival(UUID id, String firstName, String lastName, String school, boolean staff,
			Instant arrivedAt, List<EventVisit> events) {
	}

	public record Attendee(UUID id, String firstName, String lastName, String school, boolean staff,
			Instant checkedInAt) {
	}

	public record EventAttendance(EventRef event, List<Attendee> items, long total, int page, int size) {
	}

	public static final int MAX_SIZE = 100;

	private static final String MATCHES = """
			(lower(r.first_name) like :pattern or lower(r.last_name) like :pattern
				or lower(r.first_name || ' ' || r.last_name) like :pattern)
			""";

	private final JdbcClient jdbc;

	private final EventService events;

	public AttendanceService(JdbcClient jdbc, EventService events) {
		this.jdbc = jdbc;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public PageResponse<Arrival> arrivals(String q, int page, int size) {
		Event general = events.general();
		int pageSize = pageSize(size);
		int pageNumber = Math.max(page, 0);
		String pattern = Texts.containsPattern(q);
		List<Attendee> people = attendees(general.getId(), pattern, pageNumber, pageSize);
		Map<UUID, List<EventVisit>> visits = visits(people.stream().map(Attendee::id).toList());
		List<Arrival> items = people.stream()
			.map(p -> new Arrival(p.id(), p.firstName(), p.lastName(), p.school(), p.staff(), p.checkedInAt(),
					visits.getOrDefault(p.id(), List.of())))
			.toList();
		return new PageResponse<>(items, count(general.getId(), pattern), pageNumber, pageSize);
	}

	@Transactional(readOnly = true)
	public EventAttendance atEvent(UUID eventId, String q, int page, int size) {
		Event event = events.get(eventId);
		int pageSize = pageSize(size);
		int pageNumber = Math.max(page, 0);
		String pattern = Texts.containsPattern(q);
		return new EventAttendance(EventRef.of(event), attendees(event.getId(), pattern, pageNumber, pageSize),
				count(event.getId(), pattern), pageNumber, pageSize);
	}

	private List<Attendee> attendees(UUID eventId, String pattern, int page, int size) {
		return jdbc.sql("""
				select r.id, r.first_name, r.last_name, r.school, r.staff, c.checked_in_at
				from check_ins c join registrations r on r.id = c.registration_id
				where c.event_id = :eventId and %s
				order by c.checked_in_at desc, r.id
				limit :limit offset :offset
				""".formatted(MATCHES))
			.param("eventId", eventId)
			.param("pattern", pattern)
			.param("limit", size)
			.param("offset", (long) page * size)
			.query((rs, rowNum) -> new Attendee(rs.getObject("id", UUID.class), rs.getString("first_name"),
					rs.getString("last_name"), rs.getString("school"), rs.getBoolean("staff"),
					instant(rs, "checked_in_at")))
			.list();
	}

	private long count(UUID eventId, String pattern) {
		return jdbc.sql("""
				select count(*) from check_ins c join registrations r on r.id = c.registration_id
				where c.event_id = :eventId and %s
				""".formatted(MATCHES)).param("eventId", eventId).param("pattern", pattern).query(Long.class).single();
	}

	/** One query for the whole page. */
	private Map<UUID, List<EventVisit>> visits(List<UUID> registrationIds) {
		Map<UUID, List<EventVisit>> byRegistration = new HashMap<>();
		if (registrationIds.isEmpty()) {
			return byRegistration;
		}
		jdbc.sql("""
				select c.registration_id, e.id as event_id, e.name, c.checked_in_at
				from check_ins c join events e on e.id = c.event_id
				where not e.general and c.registration_id in (:ids)
				order by c.checked_in_at, lower(e.name)
				""").param("ids", registrationIds).query((ResultSet rs) -> {
			byRegistration.computeIfAbsent(rs.getObject("registration_id", UUID.class), id -> new ArrayList<>())
				.add(new EventVisit(rs.getObject("event_id", UUID.class), rs.getString("name"),
						instant(rs, "checked_in_at")));
		});
		return byRegistration;
	}

	private static int pageSize(int size) {
		return Math.min(Math.max(size, 1), MAX_SIZE);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getTimestamp(column).toInstant();
	}

}
