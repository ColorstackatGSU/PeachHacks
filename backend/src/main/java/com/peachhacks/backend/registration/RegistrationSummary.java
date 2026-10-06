package com.peachhacks.backend.registration;

import java.time.Instant;
import java.util.UUID;

public record RegistrationSummary(UUID id, String firstName, String lastName, String email,
		String schoolEmail, boolean schoolEmailConfirmed, Instant schoolEmailConfirmedAt, String school, String levelOfStudy, String countryOfResidence, Integer age,
		RegistrationStatus status, Instant createdAt, Instant checkedInAt, boolean hasResume, boolean resumeOptIn) {

	static RegistrationSummary from(Registration r, Instant checkedInAt, RegistrationResume resume) {
		return new RegistrationSummary(r.getId(), r.getFirstName(), r.getLastName(), r.getEmail(),
				r.getSchoolEmail(), r.isSchoolEmailConfirmed(), r.getSchoolEmailConfirmedAt(), r.getSchool(), r.getLevelOfStudy(), r.getCountryOfResidence(), r.getAge(),
				r.getStatus(), r.getCreatedAt(), checkedInAt, resume != null, resume != null && resume.isSponsorOptIn());
	}

}
