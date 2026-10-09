package com.peachhacks.backend.discord;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.peachhacks.backend.admin.AdminPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/discord")
public class AdminDiscordController {

	public record MessageRequest(String message) {
	}

	private static final Logger log = LoggerFactory.getLogger(AdminDiscordController.class);

	private final DiscordVerification verification;

	private final DiscordApplications applications;

	private final DiscordGateway gateway;

	private final DiscordWelcome welcome;

	public AdminDiscordController(DiscordVerification verification, DiscordApplications applications,
			DiscordGateway gateway, DiscordWelcome welcome) {
		this.gateway = gateway;
		this.welcome = welcome;
		this.verification = verification;
		this.applications = applications;
	}

	/** gateway, lastJoinSeen and lastWelcome say why a welcome did or did not go out. */
	public record Diagnostics(@JsonUnwrapped DiscordVerification.Status status, String gateway, Instant lastJoinSeen,
			String lastWelcome) {
	}

	@GetMapping
	Diagnostics status() {
		return new Diagnostics(verification.status(), gateway.state(), gateway.lastJoinSeen(), welcome.lastResult());
	}

	/** Without a body (or with a null message) the text stays as it is and is posted or refreshed. */
	@PostMapping("/verification-message")
	DiscordVerification.Status publishVerificationMessage(@RequestBody(required = false) MessageRequest request,
			@AuthenticationPrincipal AdminPrincipal admin) {
		verification.publishVerificationMessage((request != null) ? request.message() : null);
		log.info("Discord verification message published by {}", admin.email());
		return verification.status();
	}

	/** Posts the daily recap to the applications channel now, as well as at its usual time. */
	@PostMapping("/recap")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void postRecap(@AuthenticationPrincipal AdminPrincipal admin) {
		applications.postRecapNow();
		log.info("Discord recap posted on request by {}", admin.email());
	}

}
