package com.peachhacks.backend.prereg;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreRegistrationView(UUID id, String firstName, String lastName, String email, String school,
		String schoolEmail, boolean unsubscribed, boolean registered, Instant createdAt,
		Instant schoolEmailConfirmedAt) {

	@JsonProperty
	public boolean schoolEmailConfirmed() {
		return schoolEmailConfirmedAt != null;
	}

}
