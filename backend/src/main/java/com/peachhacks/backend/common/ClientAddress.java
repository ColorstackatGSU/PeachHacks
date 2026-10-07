package com.peachhacks.backend.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.peachhacks.backend.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

@Component
public class ClientAddress {

	private static final Logger log = LoggerFactory.getLogger(ClientAddress.class);

	private final boolean trustForwardedFor;

	private final AtomicBoolean described = new AtomicBoolean();

	public ClientAddress(RateLimitProperties properties) {
		this.trustForwardedFor = properties.trustForwardedFor();
	}

	/**
	 * Behind the hosting proxy the socket address is the proxy, so the last
	 * X-Forwarded-For entry (the one the proxy itself appended) is used instead. Earlier
	 * entries are client supplied and ignored.
	 */
	public String of(HttpServletRequest request) {
		if (!trustForwardedFor) {
			return request.getRemoteAddr();
		}
		List<String> entries = new ArrayList<>();
		for (String header : Collections.list(request.getHeaders("X-Forwarded-For"))) {
			for (String entry : header.split(",")) {
				if (!entry.isBlank()) {
					entries.add(entry.trim());
				}
			}
		}
		String address = entries.isEmpty() ? request.getRemoteAddr() : entries.get(entries.size() - 1);
		// Once, so the proxy setup can be checked against a request whose origin is known.
		if (described.compareAndSet(false, true)) {
			log.info("X-Forwarded-For carries {} entr{}; the client address used for rate limiting is {} ({})",
					entries.size(), (entries.size() == 1) ? "y" : "ies", address,
					entries.isEmpty() ? "the socket address, because the header is absent" : "the last entry");
		}
		return address;
	}

}
