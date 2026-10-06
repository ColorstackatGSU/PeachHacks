package com.peachhacks.backend.prereg;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreRegistrationRequest(
		@NotBlank(message = "First name is required") @Size(max = 100,
				message = "First name must be at most 100 characters") String firstName,
		@NotBlank(message = "Last name is required") @Size(max = 100,
				message = "Last name must be at most 100 characters") String lastName,
		@NotBlank(message = "Email is required") @Email(regexp = ".+@.+\\..+",
				message = "Must be a valid email") @Size(max = 255,
						message = "Email must be at most 255 characters") String email,
		@NotBlank(message = "School is required") @Size(max = 255,
				message = "School must be at most 255 characters") String school,
		@Email(regexp = "^$|.+@.+\\..+", message = "Must be a valid email") @Size(max = 255,
				message = "School email must be at most 255 characters") String schoolEmail,
		/* Honeypot: real visitors never see or fill this field. */
		String website) {
}
