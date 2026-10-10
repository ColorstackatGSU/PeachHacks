package com.peachhacks.backend.badge;

import java.util.UUID;

import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Who may call which of these is decided in SecurityConfig. The uid is checked by BadgeUid. */
@RestController
public class BadgeController {

	/** Exactly one of the two: what the scanner read from a ticket, or a registration picked from the list. */
	public record ResolveRequest(@Size(max = 2048, message = "Code is too long") String code, UUID registrationId) {
	}

	public record BindRequest(@NotNull(message = "Registration is required") UUID registrationId, String uid,
			Boolean replace) {
	}

	public record TapRequest(String uid, UUID eventId) {
	}

	public record LookupRequest(String uid) {
	}

	private final BadgeService service;

	public BadgeController(BadgeService service) {
		this.service = service;
	}

	@PostMapping("/admin/badges/resolve")
	BadgeService.ResolveResult resolve(@Valid @RequestBody ResolveRequest request) {
		boolean hasCode = request.code() != null && !request.code().isBlank();
		if (hasCode == (request.registrationId() != null)) {
			throw ApiException.validation("Send either a scanned code or a registration, not both.", null);
		}
		return service.resolve(request.code(), request.registrationId());
	}

	@PostMapping("/admin/badges/bind")
	BadgeService.BindResult bind(@Valid @RequestBody BindRequest request,
			@AuthenticationPrincipal AdminPrincipal current) {
		return service.bind(request.registrationId(), request.uid(), Boolean.TRUE.equals(request.replace()), current);
	}

	@PostMapping("/admin/badges/tap")
	BadgeService.TapResult tap(@RequestBody TapRequest request, @AuthenticationPrincipal AdminPrincipal current) {
		return service.tap(request.uid(), request.eventId(), current);
	}

	@PostMapping("/admin/badges/lookup")
	BadgeService.LookupResult lookup(@RequestBody LookupRequest request) {
		return service.lookup(request.uid());
	}

	@DeleteMapping("/admin/registrations/{id}/badge")
	ResponseEntity<Void> revoke(@PathVariable UUID id, @AuthenticationPrincipal AdminPrincipal current) {
		service.revoke(id, current);
		return ResponseEntity.noContent().build();
	}

}
