package com.peachhacks.backend.publicapi;

import java.util.Map;
import java.util.UUID;

import com.peachhacks.backend.email.AudienceService;
import com.peachhacks.backend.prereg.PreRegistrationRequest;
import com.peachhacks.backend.prereg.PreRegistrationService;
import com.peachhacks.backend.registration.RegistrationRequest;
import com.peachhacks.backend.registration.RegistrationService;
import com.peachhacks.backend.stats.SettingsService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bodies are validated inside the services, after the honeypot and registration gate checks. */
@RestController
@RequestMapping("/public")
public class PublicController {

	public record UnsubscribeRequest(String token) {
	}

	private final SettingsService settings;

	private final PreRegistrationService preRegistrations;

	private final RegistrationService registrations;

	private final AudienceService audiences;

	public PublicController(SettingsService settings, PreRegistrationService preRegistrations,
			RegistrationService registrations, AudienceService audiences) {
		this.settings = settings;
		this.preRegistrations = preRegistrations;
		this.registrations = registrations;
		this.audiences = audiences;
	}

	@GetMapping("/status")
	Map<String, Boolean> status() {
		return Map.of("registrationOpen", settings.isRegistrationOpen());
	}

	@PostMapping("/pre-registrations")
	ResponseEntity<Map<String, UUID>> preRegister(@RequestBody PreRegistrationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", preRegistrations.submit(request)));
	}

	@PostMapping("/registrations")
	ResponseEntity<Map<String, UUID>> register(@RequestBody RegistrationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", registrations.submit(request)));
	}

	@PostMapping("/unsubscribe")
	ResponseEntity<Void> unsubscribe(@RequestBody UnsubscribeRequest request) {
		audiences.unsubscribe(request.token());
		return ResponseEntity.noContent().build();
	}

}
