package com.peachhacks.backend.email;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/emails")
public class AdminEmailController {

	public record RecipientCountRequest(@NotNull(message = "Choose the kind of email") CampaignKind kind,
			@NotNull(message = "Choose an audience") Audience audience, @Size(max = 255) String school) {
	}

	/** kind decides which footer the test copy shows; without one it is an announcement. */
	public record TestEmailRequest(CampaignKind kind,
			@NotBlank(message = "Subject is required") @Size(max = 200,
					message = "Subject must be at most 200 characters") String subject,
			@NotBlank(message = "Body is required") @Size(max = 20000,
					message = "Body must be at most 20000 characters") String body) {
	}

	public record CampaignRequest(@NotNull(message = "Choose the kind of email") CampaignKind kind,
			@NotNull(message = "Choose an audience") Audience audience, @Size(max = 255) String school,
			@NotBlank(message = "Subject is required") @Size(max = 200,
					message = "Subject must be at most 200 characters") String subject,
			@NotBlank(message = "Body is required") @Size(max = 20000,
					message = "Body must be at most 20000 characters") String body) {
	}

	private final CampaignService campaigns;

	private final AudienceService audiences;

	public AdminEmailController(CampaignService campaigns, AudienceService audiences) {
		this.campaigns = campaigns;
		this.audiences = audiences;
	}

	@PostMapping("/recipient-count")
	Map<String, Integer> recipientCount(@Valid @RequestBody RecipientCountRequest request) {
		return Map.of("recipientCount", audiences.recipients(request.kind(), request.audience(), request.school()).size());
	}

	@PostMapping("/test")
	ResponseEntity<Void> test(@Valid @RequestBody TestEmailRequest request, @AuthenticationPrincipal AdminPrincipal admin) {
		campaigns.sendTest(admin.email(), admin.name(),
				(request.kind() != null) ? request.kind() : CampaignKind.ANNOUNCEMENT, request.subject(),
				request.body());
		return ResponseEntity.noContent().build();
	}

	/** The draft rendered exactly as the test copy would be, for the composer's preview. Sends nothing. */
	@PostMapping("/preview")
	Map<String, String> preview(@Valid @RequestBody TestEmailRequest request,
			@AuthenticationPrincipal AdminPrincipal admin) {
		EmailMessage message = campaigns.sample(admin.email(), admin.name(),
				(request.kind() != null) ? request.kind() : CampaignKind.ANNOUNCEMENT, request.subject(),
				request.body());
		return Map.of("subject", message.subject(), "html", message.html(), "text", message.text());
	}

	@PostMapping
	ResponseEntity<EmailCampaign> send(@Valid @RequestBody CampaignRequest request,
			@AuthenticationPrincipal AdminPrincipal admin) {
		EmailCampaign campaign = campaigns.start(request.kind(), request.audience(), request.school(),
				request.subject(), request.body(), admin.email());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(campaign);
	}

	@GetMapping
	List<EmailCampaign> list() {
		return campaigns.list();
	}

	/** Empty for a campaign sent before recipients were recorded. */
	@GetMapping("/{id}/recipients")
	PageResponse<CampaignRecipients.View> recipients(@PathVariable UUID id,
			@RequestParam(required = false) CampaignRecipients.Status status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		return campaigns.recipients(id, status, page, size);
	}

}
