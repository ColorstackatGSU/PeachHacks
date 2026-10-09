package com.peachhacks.backend.common;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

	private static final Duration MINUTE = Duration.ofMinutes(1);

	private static final int MAX_TRACKED_KEYS = 20_000;

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public boolean tryAcquire(String key, int limit) {
		return tryAcquire(key, limit, MINUTE);
	}

	/** A key must always be used with the same window. */
	public boolean tryAcquire(String key, int limit, Duration window) {
		long now = System.currentTimeMillis();
		long length = window.toMillis();
		if (windows.size() > MAX_TRACKED_KEYS) {
			windows.values().removeIf(entry -> entry.over(now));
		}
		Window current = windows.compute(key,
				(k, existing) -> (existing == null || existing.over(now)) ? new Window(now, length) : existing);
		return current.count.incrementAndGet() <= limit;
	}

	private static final class Window {

		private final long start;

		private final long length;

		private final AtomicInteger count = new AtomicInteger();

		private Window(long start, long length) {
			this.start = start;
			this.length = length;
		}

		private boolean over(long now) {
			return now - start >= length;
		}

	}

}
