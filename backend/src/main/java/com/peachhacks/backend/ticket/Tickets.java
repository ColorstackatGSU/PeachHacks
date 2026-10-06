package com.peachhacks.backend.ticket;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.peachhacks.backend.config.EmailProperties;

import org.springframework.stereotype.Component;

@Component
public class Tickets {

	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,64}");

	private static final Pattern TOKEN_PARAMETER = Pattern.compile("[?&]t=([^&#\\s]+)");

	private final EmailProperties properties;

	public Tickets(EmailProperties properties) {
		this.properties = properties;
	}

	/** What the QR code encodes: a page on the public site, so any phone camera can open it. */
	public String url(String token) {
		return properties.webBaseUrl() + "/ticket?t=" + token;
	}

	public byte[] qrPng(String token) {
		return QrCode.png(url(token));
	}

	/**
	 * Accepts what a scanner read, either the bare token or a ticket URL carrying it as the t
	 * parameter. The host is not checked: only the token identifies a ticket.
	 */
	public static Optional<String> tokenFrom(String scanned) {
		if (scanned == null) {
			return Optional.empty();
		}
		String candidate = scanned.strip();
		Matcher parameter = TOKEN_PARAMETER.matcher(candidate);
		if (parameter.find()) {
			try {
				candidate = URLDecoder.decode(parameter.group(1), StandardCharsets.UTF_8);
			}
			catch (IllegalArgumentException ex) {
				return Optional.empty();
			}
		}
		return TOKEN.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
	}

}
