package com.peachhacks.backend.publicapi;

import java.util.Map;
import java.util.UUID;

import com.peachhacks.backend.email.AudienceService;
import com.peachhacks.backend.prereg.PreRegistrationRequest;
import com.peachhacks.backend.prereg.PreRegistrationService;
import com.peachhacks.backend.registration.RegistrationRequest;
import com.peachhacks.backend.registration.RegistrationService;
import com.peachhacks.backend.schoolemail.SchoolEmailService;
import com.peachhacks.backend.sponsor.SponsorInquiryRequest;
import com.peachhacks.backend.sponsor.SponsorInquiryService;
import com.peachhacks.backend.stats.SettingsService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bodies are validated inside the services, after the honeypot and registration gate checks. */
@RestController
@RequestMapping("/public")
public class PublicController {

	public record UnsubscribeRequest(String token) {
	}

	public record SchoolEmailConfirmRequest(String token) {

		@Override
		public String toString() {
			return "SchoolEmailConfirmRequest[]";
		}

	}

	public record SchoolEmailResendRequest(String email) {
	}

	private final SettingsService settings;

	private final PreRegistrationService preRegistrations;

	private final RegistrationService registrations;

	private final AudienceService audiences;

	private final SchoolEmailService schoolEmails;

	private final SponsorInquiryService sponsorInquiries;

	public PublicController(SettingsService settings, PreRegistrationService preRegistrations,
			RegistrationService registrations, AudienceService audiences, SchoolEmailService schoolEmails,
			SponsorInquiryService sponsorInquiries) {
		this.settings = settings;
		this.preRegistrations = preRegistrations;
		this.registrations = registrations;
		this.audiences = audiences;
		this.schoolEmails = schoolEmails;
		this.sponsorInquiries = sponsorInquiries;
	}

	static final String PREVIEW_HEADER = "X-Registration-Preview";

	/** With a valid preview key the answer is "open" although the gate is closed; preview says so. */
	@GetMapping("/status")
	Map<String, Boolean> status(@RequestHeader(name = PREVIEW_HEADER, required = false) String previewKey) {
		boolean open = settings.isRegistrationOpen();
		boolean preview = !open && settings.previewAllows(previewKey);
		return Map.of("registrationOpen", open || preview, "preview", preview);
	}

	@PostMapping("/pre-registrations")
	ResponseEntity<Map<String, UUID>> preRegister(@RequestBody PreRegistrationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", preRegistrations.submit(request)));
	}

	@PostMapping("/registrations")
	ResponseEntity<Map<String, UUID>> register(@RequestBody RegistrationRequest request,
			@RequestHeader(name = PREVIEW_HEADER, required = false) String previewKey) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(Map.of("id", registrations.submit(request, previewKey)));
	}

	/** 204 once the inquiry has been emailed to the sponsor inbox; 503 if it could not be. */
	@PostMapping("/sponsor-inquiries")
	ResponseEntity<Void> sponsorInquiry(@RequestBody SponsorInquiryRequest request) {
		sponsorInquiries.submit(request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/unsubscribe")
	ResponseEntity<Void> unsubscribe(@RequestBody UnsubscribeRequest request) {
		audiences.unsubscribe(request.token());
		return ResponseEntity.noContent().build();
	}

	/**
	 * A POST, not a GET on the emailed link: mail scanners open links, and that must not
	 * confirm an address on its owner's behalf.
	 */
	@PostMapping("/school-email/confirm")
	Map<String, String> confirmSchoolEmail(@RequestBody SchoolEmailConfirmRequest request) {
		return Map.of("schoolEmail", schoolEmails.confirm(request.token()));
	}

	/** Always 204, so the response does not reveal whether the email belongs to anyone. */
	@PostMapping("/school-email/resend")
	ResponseEntity<Void> resendSchoolEmailConfirmation(@RequestBody SchoolEmailResendRequest request) {
		schoolEmails.resend(request.email());
		return ResponseEntity.noContent().build();
	}

}
