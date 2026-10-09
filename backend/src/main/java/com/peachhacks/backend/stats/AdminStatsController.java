package com.peachhacks.backend.stats;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.config.EmailProperties;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminStatsController {

	/** previewActive is only ever an answer; it is ignored in a request. */
	public record Settings(@NotNull(message = "registrationOpen is required") Boolean registrationOpen,
			Boolean previewActive) {
	}

	public record PreviewLink(String url) {
	}

	private static final Logger log = LoggerFactory.getLogger(AdminStatsController.class);

	private final StatsService stats;

	private final SettingsService settings;

	private final EmailProperties emailProperties;

	public AdminStatsController(StatsService stats, SettingsService settings, EmailProperties emailProperties) {
		this.stats = stats;
		this.settings = settings;
		this.emailProperties = emailProperties;
	}

	@GetMapping("/stats")
	StatsService.Stats stats() {
		return stats.stats();
	}

	@GetMapping("/settings")
	Settings settings() {
		return new Settings(settings.isRegistrationOpen(), settings.isPreviewActive());
	}

	/** The link is shown once. Making another one stops the previous link working. */
	@PostMapping("/settings/registration-preview")
	PreviewLink createPreviewLink(@AuthenticationPrincipal AdminPrincipal admin) {
		String key = settings.createPreviewKey();
		log.info("Registration preview link created by {}", admin.email());
		return new PreviewLink(emailProperties.webBaseUrl() + "/?preview=" + key + "#register");
	}

	@DeleteMapping("/settings/registration-preview")
	Settings endPreview(@AuthenticationPrincipal AdminPrincipal admin) {
		settings.endPreview();
		log.info("Registration preview ended by {}", admin.email());
		return settings();
	}

	@PutMapping("/settings")
	Settings updateSettings(@Valid @RequestBody Settings request, @AuthenticationPrincipal AdminPrincipal admin) {
		settings.setRegistrationOpen(request.registrationOpen());
		log.info("Registration {} by {}", request.registrationOpen() ? "opened" : "closed", admin.email());
		return settings();
	}

}
