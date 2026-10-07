package com.peachhacks.backend.admin;

import java.util.concurrent.ConcurrentHashMap;

import com.peachhacks.backend.config.RateLimitProperties;

import org.springframework.stereotype.Component;

/**
 * Counts failed sign-ins per email, whatever address they come from, so a password cannot
 * be guessed at from many addresses. It counts for emails that have no account too, so
 * being throttled says nothing about whether an account exists. In memory, per instance.
 */
@Component
public class LoginThrottle {

	private static final int MAX_TRACKED_EMAILS = 20_000;

	private static final int MAX_EMAIL_LENGTH = 255;

	private record Failures(long since, int count) {
	}

	private final ConcurrentHashMap<String, Failures> failures = new ConcurrentHashMap<>();

	private final int limit;

	private final long windowMillis;

	public LoginThrottle(RateLimitProperties properties) {
		this.limit = properties.loginFailuresPerAccount();
		this.windowMillis = properties.loginFailureWindow().toMillis();
	}

	public long windowMinutes() {
		return Math.max(1, windowMillis / 60_000);
	}

	public boolean blocked(String email) {
		Failures current = failures.get(email);
		return current != null && !expired(current, System.currentTimeMillis()) && current.count() >= limit;
	}

	public void failed(String email) {
		// No account has a longer email, so there is nothing to protect and nothing to store.
		if (email.length() > MAX_EMAIL_LENGTH) {
			return;
		}
		long now = System.currentTimeMillis();
		if (failures.size() > MAX_TRACKED_EMAILS) {
			failures.values().removeIf(entry -> expired(entry, now));
		}
		failures.compute(email, (key, current) -> (current == null || expired(current, now)) ? new Failures(now, 1)
				: new Failures(current.since(), current.count() + 1));
	}

	public void clear(String email) {
		failures.remove(email);
	}

	private boolean expired(Failures entry, long now) {
		return now - entry.since() >= windowMillis;
	}

}
