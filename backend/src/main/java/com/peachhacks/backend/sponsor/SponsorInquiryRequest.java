package com.peachhacks.backend.sponsor;

import com.peachhacks.backend.common.Patterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SponsorInquiryRequest(
		@NotBlank(message = "Organization name is required") @Size(max = 200,
				message = "Organization name must be at most 200 characters") String organization,
		@NotBlank(message = "Your name is required") @Size(max = 100,
				message = "Name must be at most 100 characters") @Pattern(regexp = Patterns.NAME,
						message = Patterns.NAME_MESSAGE) String name,
		@NotBlank(message = "Email is required") @Email(regexp = Patterns.EMAIL,
				message = "Must be a valid email") @Size(max = 254,
						message = "Email must be at most 254 characters") String email,
		@Size(max = 4000, message = "Message must be at most 4000 characters") String message,
		/* Honeypot: real visitors never see or fill this field. */
		String website) {
}
