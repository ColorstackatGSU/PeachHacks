package com.peachhacks.backend.checkin;

import java.util.List;
import java.util.UUID;

import com.peachhacks.backend.common.PageResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only. Kept outside /admin/check-in so the web check-in setting never applies here;
 * who may call it is decided in SecurityConfig.
 */
@RestController
public class AttendanceController {

	private final AttendanceService service;

	private final EventService events;

	public AttendanceController(AttendanceService service, EventService events) {
		this.service = service;
		this.events = events;
	}

	/** The same list as GET /admin/events, for a lookup account, which may not call that. */
	@GetMapping("/admin/attendance/events")
	List<EventService.EventView> events() {
		return events.list();
	}

	@GetMapping("/admin/attendance")
	PageResponse<AttendanceService.Arrival> arrivals(@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		return service.arrivals(q, page, size);
	}

	@GetMapping("/admin/events/{id}/attendance")
	AttendanceService.EventAttendance atEvent(@PathVariable UUID id, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		return service.atEvent(id, q, page, size);
	}

}
