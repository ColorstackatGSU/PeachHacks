package com.peachhacks.backend.checkin;

import java.util.UUID;

import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/check-in")
public class CheckInController {

	/** code is whatever the scanner read: the bare ticket token or the whole ticket URL. */
	public record ScanRequest(
			@NotBlank(message = "Code is required") @Size(max = 2048, message = "Code is too long") String code,
			UUID eventId) {
	}

	private final CheckInService service;

	public CheckInController(CheckInService service) {
		this.service = service;
	}

	@GetMapping
	CheckInService.CheckInPage list(@RequestParam(required = false) UUID eventId,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return service.search(eventId, q, PageResponse.pageable(page, size));
	}

	@PostMapping("/scan")
	CheckInService.ScanResult scan(@Valid @RequestBody ScanRequest request,
			@AuthenticationPrincipal AdminPrincipal current) {
		return service.scan(request.code(), request.eventId(), current);
	}

	@PostMapping("/{id}")
	CheckInItem checkIn(@PathVariable UUID id, @RequestParam(required = false) UUID eventId,
			@AuthenticationPrincipal AdminPrincipal current) {
		return service.checkIn(id, eventId, current);
	}

	@DeleteMapping("/{id}")
	CheckInItem undo(@PathVariable UUID id, @RequestParam(required = false) UUID eventId,
			@AuthenticationPrincipal AdminPrincipal current) {
		return service.undo(id, eventId, current);
	}

}
