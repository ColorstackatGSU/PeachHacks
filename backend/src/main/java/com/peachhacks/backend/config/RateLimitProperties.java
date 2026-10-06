package com.peachhacks.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Integer publicPerMinute, Integer loginPerMinute, boolean trustForwardedFor) {

	public RateLimitProperties {
		publicPerMinute = (publicPerMinute != null) ? publicPerMinute : 60;
		loginPerMinute = (loginPerMinute != null) ? loginPerMinute : 10;
	}

}
