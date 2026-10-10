package com.peachhacks.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String bootstrapEmail, String bootstrapPassword, String bootstrapName,
		Duration sessionTtl, Duration inviteTtl, Duration passwordResetTtl) {

	public AdminProperties {
		sessionTtl = (sessionTtl != null) ? sessionTtl : Duration.ofDays(30);
		inviteTtl = (inviteTtl != null) ? inviteTtl : Duration.ofDays(7);
		passwordResetTtl = (passwordResetTtl != null) ? passwordResetTtl : Duration.ofHours(1);
	}

	@Override
	public String toString() {
		return "AdminProperties[bootstrapEmail=" + bootstrapEmail + ", sessionTtl=" + sessionTtl + "]";
	}

}
