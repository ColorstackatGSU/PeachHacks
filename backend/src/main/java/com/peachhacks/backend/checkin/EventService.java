package com.peachhacks.backend.checkin;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

	public record EventView(UUID id, String name, Instant startsAt, boolean general, long checkedIn) {

		static EventView of(Event event, long checkedIn) {
			return new EventView(event.getId(), event.getName(), event.getStartsAt(), event.isGeneral(), checkedIn);
		}

	}

	private static final int MAX_NAME_LENGTH = 120;

	private static final Logger log = LoggerFactory.getLogger(EventService.class);

	private final EventRepository events;

	private final CheckInRepository checkIns;

	public EventService(EventRepository events, CheckInRepository checkIns) {
		this.events = events;
		this.checkIns = checkIns;
	}

	public List<EventView> list() {
		return events.findAllWithCounts()
			.stream()
			.map(row -> EventView.of((Event) row[0], ((Number) row[1]).longValue()))
			.toList();
	}

	public Event general() {
		return events.findByGeneralTrue()
			.orElseThrow(() -> new IllegalStateException("The general check-in event is missing"));
	}

	public Event get(UUID id) {
		return events.findById(id).orElseThrow(() -> ApiException.notFound("Event not found."));
	}

	public Event resolve(UUID idOrNull) {
		return (idOrNull != null) ? get(idOrNull) : general();
	}

	public EventView create(Map<String, Object> body) {
		if (body == null || !body.containsKey("name")) {
			throw ApiException.invalidField("name", "Name is required");
		}
		String name = name(body.get("name"), null);
		Event event = save(new Event(name, startsAt(body.get("startsAt"))));
		log.info("Event \"{}\" created", name);
		return EventView.of(event, 0);
	}

	/** Only the keys present in the body change, so a rename does not clear the start time. */
	public EventView update(UUID id, Map<String, Object> body) {
		Event event = get(id);
		if (body != null && body.containsKey("name")) {
			event.setName(name(body.get("name"), id));
		}
		if (body != null && body.containsKey("startsAt")) {
			event.setStartsAt(startsAt(body.get("startsAt")));
		}
		return EventView.of(save(event), checkIns.countByEventId(id));
	}

	@Transactional
	public void delete(UUID id) {
		Event event = get(id);
		if (event.isGeneral()) {
			throw ApiException.validation("The general check-in event cannot be deleted.", null);
		}
		long checkedIn = checkIns.countByEventId(id);
		if (checkedIn > 0) {
			// The foreign key would cascade and silently erase the attendance record.
			throw new ApiException(HttpStatus.CONFLICT, "EVENT_HAS_CHECK_INS",
					"\"%s\" has %d check-in%s, so it cannot be deleted. Undo the check-ins first if it really should go."
						.formatted(event.getName(), checkedIn, (checkedIn == 1) ? "" : "s"));
		}
		events.delete(event);
		log.info("Event \"{}\" deleted", event.getName());
	}

	private Event save(Event event) {
		try {
			return events.saveAndFlush(event);
		}
		catch (DataIntegrityViolationException ex) {
			throw nameTaken();
		}
	}

	private String name(Object value, UUID exceptId) {
		String name = (value instanceof String text) ? Texts.clean(text) : null;
		if (name == null) {
			throw ApiException.invalidField("name", "Name is required");
		}
		if (name.length() > MAX_NAME_LENGTH) {
			throw ApiException.invalidField("name", "Name must be at most " + MAX_NAME_LENGTH + " characters");
		}
		// A never-used id stands in for "no event to exclude" so one query serves create and rename.
		if (events.nameTaken(name, (exceptId != null) ? exceptId : new UUID(0, 0))) {
			throw nameTaken();
		}
		return name;
	}

	private static Instant startsAt(Object value) {
		if (value == null || (value instanceof String text && text.isBlank())) {
			return null;
		}
		try {
			return Instant.parse(value.toString().strip());
		}
		catch (DateTimeParseException ex) {
			throw ApiException.invalidField("startsAt", "Start time must be an ISO-8601 UTC timestamp");
		}
	}

	private static ApiException nameTaken() {
		return ApiException.invalidField("name", "An event with this name already exists");
	}

}
