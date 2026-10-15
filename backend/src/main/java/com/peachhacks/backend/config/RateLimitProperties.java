package com.peachhacks.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * signUpPerWindow is per client address and signUpGlobalPerHour across all of them, both
 * for the sign-up and sponsor form POSTs only. loginFailuresPerAccount is per email, whatever the
 * client address. platformWritesPerHacker is per signed-in hacker and platformWritesPerAddress a backstop
 * per client address, both for the hacker platform's POST, PUT, PATCH and DELETE routes outside
 * /platform/auth. googleStartsPerAddress and googleStartsPerMinute (across all addresses) hold back
 * the Google sign-in starts.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Integer publicPerMinute, Integer ticketPerMinute,
		Integer signUpPerWindow, Duration signUpWindow, Integer signUpGlobalPerHour, Integer loginFailuresPerAccount,
		Duration loginFailureWindow, Integer platformWritesPerHacker, Integer platformWritesPerAddress,
		Integer googleStartsPerAddress, Integer googleStartsPerMinute, boolean trustForwardedFor) {

	public RateLimitProperties {
		publicPerMinute = (publicPerMinute != null) ? publicPerMinute : 60;
		ticketPerMinute = (ticketPerMinute != null) ? ticketPerMinute : 300;
		signUpPerWindow = (signUpPerWindow != null) ? signUpPerWindow : 30;
		signUpWindow = (signUpWindow != null) ? signUpWindow : Duration.ofMinutes(10);
		signUpGlobalPerHour = (signUpGlobalPerHour != null) ? signUpGlobalPerHour : 1000;
		loginFailuresPerAccount = (loginFailuresPerAccount != null) ? loginFailuresPerAccount : 10;
		loginFailureWindow = (loginFailureWindow != null) ? loginFailureWindow : Duration.ofMinutes(15);
		platformWritesPerHacker = (platformWritesPerHacker != null) ? platformWritesPerHacker : 30;
		platformWritesPerAddress = (platformWritesPerAddress != null) ? platformWritesPerAddress : 1000;
		googleStartsPerAddress = (googleStartsPerAddress != null) ? googleStartsPerAddress : 300;
		googleStartsPerMinute = (googleStartsPerMinute != null) ? googleStartsPerMinute : 1000;
	}

}
