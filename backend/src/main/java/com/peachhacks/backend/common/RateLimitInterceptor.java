package com.peachhacks.backend.common;

import java.time.Duration;
import java.util.Set;

import com.peachhacks.backend.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final Set<String> SIGN_UPS = Set.of("/public/registrations", "/public/pre-registrations",
			"/public/sponsor-inquiries");

	private static final String ALL_SIGN_UPS = "sign-up-all";

	private final RateLimiter rateLimiter;

	private final RateLimitProperties properties;

	private final ClientAddress clientAddress;

	public RateLimitInterceptor(RateLimiter rateLimiter, RateLimitProperties properties,
			ClientAddress clientAddress) {
		this.rateLimiter = rateLimiter;
		this.properties = properties;
		this.clientAddress = clientAddress;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// The mapping the request matched, not the URI as sent: /admin/%61uth/login reaches
		// the login handler and must count as a login.
		String route = (request
			.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String pattern) ? pattern : "";
		String client = clientAddress.of(request);
		if (route.startsWith("/public/tickets/")) {
			// Ticket pages are opened by everyone in the door queue, often from one venue
			// network address, so they get their own, larger allowance.
			acquire("ticket:" + client, properties.ticketPerMinute());
		}
		else if (!"POST".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		else if (route.startsWith("/admin/auth/") || route.startsWith("/platform/auth/")) {
			acquire("login:" + client, properties.loginPerMinute());
		}
		else if (SIGN_UPS.contains(route)) {
			if (!rateLimiter.tryAcquire("sign-up:" + client, properties.signUpPerWindow(), properties.signUpWindow())) {
				throw tooMany("Too many sign-ups from this network. Please wait a few minutes and try again.");
			}
			if (!rateLimiter.tryAcquire(ALL_SIGN_UPS, properties.signUpGlobalPerHour(), Duration.ofHours(1))) {
				throw tooMany("We are receiving a lot of sign-ups right now. Please try again shortly.");
			}
		}
		else {
			acquire("public:" + client, properties.publicPerMinute());
		}
		return true;
	}

	private void acquire(String key, int limit) {
		if (!rateLimiter.tryAcquire(key, limit)) {
			throw tooMany("Too many requests. Please wait a minute and try again.");
		}
	}

	private static ApiException tooMany(String message) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
	}

}
