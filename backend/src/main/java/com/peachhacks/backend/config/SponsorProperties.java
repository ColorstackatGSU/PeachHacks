package com.peachhacks.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** inbox is where sponsor form submissions are emailed. */
@ConfigurationProperties(prefix = "app.sponsor")
public record SponsorProperties(String inbox) {

	public SponsorProperties {
		inbox = (inbox != null && !inbox.isBlank()) ? inbox.strip() : "sponsors@peachhacks.com";
	}

}
