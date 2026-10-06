package com.peachhacks.backend.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * hostSchoolTarget is the share of accepted hackers (0 to 1) who must come from the host
 * school; it is a decimal so that "exactly at target" is decided without rounding.
 */
@ConfigurationProperties(prefix = "app.acceptance")
public record AcceptanceProperties(String hostSchoolName, BigDecimal hostSchoolTarget) {

	public AcceptanceProperties {
		hostSchoolName = (hostSchoolName != null && !hostSchoolName.isBlank()) ? hostSchoolName.strip()
				: "Georgia State University";
		hostSchoolTarget = (hostSchoolTarget != null) ? hostSchoolTarget : new BigDecimal("0.70");
		if (hostSchoolTarget.signum() < 0 || hostSchoolTarget.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException(
					"HOST_SCHOOL_TARGET must be a fraction between 0 and 1 (0.70 for 70%), not " + hostSchoolTarget);
		}
	}

}
