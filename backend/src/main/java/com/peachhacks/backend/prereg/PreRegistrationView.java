package com.peachhacks.backend.prereg;

import java.time.Instant;
import java.util.UUID;

public record PreRegistrationView(UUID id, String firstName, String lastName, String email, String school,
		String schoolEmail, boolean unsubscribed, boolean registered, Instant createdAt) {
}
