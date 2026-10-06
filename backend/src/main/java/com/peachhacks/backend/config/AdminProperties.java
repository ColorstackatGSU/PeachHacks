package com.peachhacks.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String bootstrapEmail, String bootstrapPassword, String bootstrapName,
		Duration sessionTtl) {

	public AdminProperties {
		sessionTtl = (sessionTtl != null) ? sessionTtl : Duration.ofHours(12);
	}

	@Override
	public String toString() {
		return "AdminProperties[bootstrapEmail=" + bootstrapEmail + ", sessionTtl=" + sessionTtl + "]";
	}

}
