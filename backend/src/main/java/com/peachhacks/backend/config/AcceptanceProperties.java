package com.peachhacks.backend.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * hostSchoolTarget is the share of accepted hackers (0 to 1) who must come from the host
 * school; it is a decimal so that "exactly at target" is decided without rounding.
 * nonHostMinimumAge is the eligibility age for students of other schools: a younger
 * registration from another school is flagged for organizers, never refused.
 */
@ConfigurationProperties(prefix = "app.acceptance")
public record AcceptanceProperties(String hostSchoolName, BigDecimal hostSchoolTarget,
		Integer nonHostMinimumAge) {

	public AcceptanceProperties {
		hostSchoolName = (hostSchoolName != null && !hostSchoolName.isBlank()) ? hostSchoolName.strip()
				: "Georgia State University";
		hostSchoolTarget = (hostSchoolTarget != null) ? hostSchoolTarget : new BigDecimal("0.70");
		if (hostSchoolTarget.signum() < 0 || hostSchoolTarget.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException(
					"HOST_SCHOOL_TARGET must be a fraction between 0 and 1 (0.70 for 70%), not " + hostSchoolTarget);
		}
		nonHostMinimumAge = (nonHostMinimumAge != null) ? nonHostMinimumAge : 18;
		if (nonHostMinimumAge < 0) {
			throw new IllegalArgumentException(
					"NON_HOST_MINIMUM_AGE must be an age in years (18), not " + nonHostMinimumAge);
		}
	}

}
