package com.peachhacks.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * signUpPerWindow is per client address and signUpGlobalPerHour across all of them, both
 * for the two sign-up POSTs only. loginFailuresPerAccount is per email, whatever the
 * client address.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Integer publicPerMinute, Integer ticketPerMinute,
		Integer signUpPerWindow, Duration signUpWindow, Integer signUpGlobalPerHour, Integer loginFailuresPerAccount,
		Duration loginFailureWindow, boolean trustForwardedFor) {

	public RateLimitProperties {
		publicPerMinute = (publicPerMinute != null) ? publicPerMinute : 60;
		ticketPerMinute = (ticketPerMinute != null) ? ticketPerMinute : 300;
		signUpPerWindow = (signUpPerWindow != null) ? signUpPerWindow : 30;
		signUpWindow = (signUpWindow != null) ? signUpWindow : Duration.ofMinutes(10);
		signUpGlobalPerHour = (signUpGlobalPerHour != null) ? signUpGlobalPerHour : 1000;
		loginFailuresPerAccount = (loginFailuresPerAccount != null) ? loginFailuresPerAccount : 10;
		loginFailureWindow = (loginFailureWindow != null) ? loginFailureWindow : Duration.ofMinutes(15);
	}

}
