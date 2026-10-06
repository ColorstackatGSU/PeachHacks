package com.peachhacks.backend.acceptance;

import java.util.List;

import com.peachhacks.backend.admin.AdminPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/acceptances")
public class AdminAcceptanceController {

	private final AcceptanceService service;

	private final AcceptanceMailer mailer;

	public AdminAcceptanceController(AcceptanceService service, AcceptanceMailer mailer) {
		this.service = service;
		this.mailer = mailer;
	}

	@GetMapping("/summary")
	AcceptanceService.Summary summary() {
		return service.summary();
	}

	@GetMapping("/waiting")
	List<AcceptanceService.Waiting> waiting() {
		return service.waiting();
	}

	/** 202 when a run was queued, 200 with queued 0 when nobody is waiting. */
	@PostMapping("/send")
	ResponseEntity<AcceptanceMailer.Started> send(@AuthenticationPrincipal AdminPrincipal admin) {
		AcceptanceMailer.Started started = mailer.start(admin.email());
		return ResponseEntity.status((started.queued() > 0) ? HttpStatus.ACCEPTED : HttpStatus.OK).body(started);
	}

	@GetMapping("/send")
	AcceptanceMailer.SendStatus sendStatus() {
		return mailer.status();
	}

}
