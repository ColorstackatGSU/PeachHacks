package com.peachhacks.backend.registration;

import java.time.Instant;
import java.util.UUID;

public record RegistrationSummary(UUID id, String firstName, String lastName, String email, String school,
		String levelOfStudy, String countryOfResidence, Integer age, RegistrationStatus status, Instant createdAt) {

	static RegistrationSummary from(Registration r) {
		return new RegistrationSummary(r.getId(), r.getFirstName(), r.getLastName(), r.getEmail(), r.getSchool(),
				r.getLevelOfStudy(), r.getCountryOfResidence(), r.getAge(), r.getStatus(), r.getCreatedAt());
	}

}
