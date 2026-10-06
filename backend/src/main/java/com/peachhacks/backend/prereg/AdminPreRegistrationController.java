package com.peachhacks.backend.prereg;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.peachhacks.backend.common.Csv;
import com.peachhacks.backend.common.PageResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/pre-registrations")
public class AdminPreRegistrationController {

	private final PreRegistrationService service;

	public AdminPreRegistrationController(PreRegistrationService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<PreRegistrationView> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String q,
			@RequestParam(required = false) String school,
			@RequestParam(required = false) Boolean schoolEmailConfirmed) {
		return service.page(q, school, schoolEmailConfirmed, page, size);
	}

	@GetMapping("/export.csv")
	ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
			@RequestParam(required = false) String school,
			@RequestParam(required = false) Boolean schoolEmailConfirmed) {
		Csv csv = new Csv(List.of("id", "firstName", "lastName", "email", "school", "schoolEmail", "unsubscribed",
				"registered", "createdAt", "school_email_confirmed"));
		for (PreRegistrationView row : service.search(q, school, schoolEmailConfirmed, Pageable.unpaged())) {
			csv.row(Arrays.asList(row.id(), row.firstName(), row.lastName(), row.email(), row.school(),
					row.schoolEmail(), row.unsubscribed(), row.registered(), row.createdAt(),
					row.schoolEmailConfirmed()));
		}
		return csv.toResponse("peachhacks-pre-registrations");
	}

	@PostMapping("/{id}/school-email/resend")
	ResponseEntity<Void> resendSchoolEmailConfirmation(@PathVariable UUID id) {
		service.resendSchoolEmailConfirmation(id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
