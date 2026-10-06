package com.peachhacks.backend.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

	private static final long WINDOW_MILLIS = 60_000;

	private static final int MAX_TRACKED_KEYS = 20_000;

	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public boolean tryAcquire(String key, int limit) {
		long now = System.currentTimeMillis();
		if (windows.size() > MAX_TRACKED_KEYS) {
			windows.entrySet().removeIf(entry -> now - entry.getValue().start >= WINDOW_MILLIS);
		}
		Window window = windows.compute(key,
				(k, current) -> (current == null || now - current.start >= WINDOW_MILLIS) ? new Window(now) : current);
		return window.count.incrementAndGet() <= limit;
	}

	private static final class Window {

		private final long start;

		private final AtomicInteger count = new AtomicInteger();

		private Window(long start) {
			this.start = start;
		}

	}

}
