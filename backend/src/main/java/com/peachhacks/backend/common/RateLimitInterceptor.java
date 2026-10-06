package com.peachhacks.backend.common;

import com.peachhacks.backend.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private final RateLimiter rateLimiter;

	private final RateLimitProperties properties;

	public RateLimitInterceptor(RateLimiter rateLimiter, RateLimitProperties properties) {
		this.rateLimiter = rateLimiter;
		this.properties = properties;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		boolean login = request.getRequestURI().endsWith("/admin/auth/login");
		String key = (login ? "login:" : "public:") + clientAddress(request);
		int limit = login ? properties.loginPerMinute() : properties.publicPerMinute();
		if (!rateLimiter.tryAcquire(key, limit)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
					"Too many requests. Please wait a minute and try again.");
		}
		return true;
	}

	/**
	 * Behind the hosting proxy the socket address is the proxy, so the last
	 * X-Forwarded-For entry (the one the proxy itself appended) is used instead. Earlier
	 * entries are client supplied and ignored.
	 */
	private String clientAddress(HttpServletRequest request) {
		if (properties.trustForwardedFor()) {
			String forwarded = request.getHeader("X-Forwarded-For");
			if (forwarded != null && !forwarded.isBlank()) {
				String[] parts = forwarded.split(",");
				String last = parts[parts.length - 1].trim();
				if (!last.isEmpty()) {
					return last;
				}
			}
		}
		return request.getRemoteAddr();
	}

}
