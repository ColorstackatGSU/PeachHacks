package com.peachhacks.backend.discord;

import com.peachhacks.backend.admin.AdminPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/discord")
public class AdminDiscordController {

	private static final Logger log = LoggerFactory.getLogger(AdminDiscordController.class);

	private final DiscordVerification verification;

	public AdminDiscordController(DiscordVerification verification) {
		this.verification = verification;
	}

	@GetMapping
	DiscordVerification.Status status() {
		return verification.status();
	}

	@PostMapping("/verification-message")
	DiscordVerification.Status postVerificationMessage(@AuthenticationPrincipal AdminPrincipal admin) {
		verification.postVerificationMessage();
		log.info("Discord verification message posted by {}", admin.email());
		return verification.status();
	}

}
