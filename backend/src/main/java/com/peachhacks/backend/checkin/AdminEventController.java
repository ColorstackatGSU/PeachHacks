package com.peachhacks.backend.checkin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.common.Csv;
import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Volunteers may only list events; SecurityConfig keeps every other route here admin-only. */
@RestController
@RequestMapping("/admin/events")
public class AdminEventController {

	private static final Logger log = LoggerFactory.getLogger(AdminEventController.class);

	private final EventService events;

	private final CheckInService checkIns;

	private final RegistrationRepository registrations;

	public AdminEventController(EventService events, CheckInService checkIns, RegistrationRepository registrations) {
		this.events = events;
		this.checkIns = checkIns;
		this.registrations = registrations;
	}

	@GetMapping
	List<EventService.EventView> list() {
		return events.list();
	}

	@PostMapping
	ResponseEntity<EventService.EventView> create(@RequestBody Map<String, Object> body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(events.create(body));
	}

	@PatchMapping("/{id}")
	EventService.EventView update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return events.update(id, body);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		events.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/export.csv")
	ResponseEntity<byte[]> export(@PathVariable UUID id, @AuthenticationPrincipal AdminPrincipal admin) {
		Event event = events.get(id);
		List<CheckIn> attendees = checkIns.attendees(id);
		Map<UUID, Registration> byId = registrations
			.findAllById(attendees.stream().map(CheckIn::getRegistrationId).toList())
			.stream()
			.collect(Collectors.toMap(Registration::getId, Function.identity()));
		Csv csv = new Csv(List.of("event", "registrationId", "firstName", "lastName", "email", "school", "status",
				"checked_in_at", "checked_in_by"));
		for (CheckIn checkIn : attendees) {
			Registration r = byId.get(checkIn.getRegistrationId());
			if (r != null) {
				csv.row(Arrays.asList(event.getName(), r.getId(), r.getFirstName(), r.getLastName(), r.getEmail(),
						r.getSchool(), r.getStatus(), checkIn.getCheckedInAt(), checkIn.getCheckedInBy()));
			}
		}
		log.info("Attendees CSV of {} rows for event {} (\"{}\") exported by {}", csv.rows(), id, event.getName(),
				admin.email());
		return csv.toResponse("peachhacks-attendees-" + slug(event.getName()));
	}

	private static String slug(String name) {
		String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return slug.isEmpty() ? "event" : slug;
	}

}
