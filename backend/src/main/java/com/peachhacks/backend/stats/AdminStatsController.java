package com.peachhacks.backend.stats;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.peachhacks.backend.admin.AdminPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminStatsController {

	public record Settings(@NotNull(message = "registrationOpen is required") Boolean registrationOpen) {
	}

	private static final Logger log = LoggerFactory.getLogger(AdminStatsController.class);

	private final StatsService stats;

	private final SettingsService settings;

	public AdminStatsController(StatsService stats, SettingsService settings) {
		this.stats = stats;
		this.settings = settings;
	}

	@GetMapping("/stats")
	StatsService.Stats stats() {
		return stats.stats();
	}

	@GetMapping("/settings")
	Settings settings() {
		return new Settings(settings.isRegistrationOpen());
	}

	@PutMapping("/settings")
	Settings updateSettings(@Valid @RequestBody Settings request, @AuthenticationPrincipal AdminPrincipal admin) {
		settings.setRegistrationOpen(request.registrationOpen());
		log.info("Registration {} by {}", request.registrationOpen() ? "opened" : "closed", admin.email());
		return new Settings(settings.isRegistrationOpen());
	}

}
