package com.peachhacks.backend.badge;

import java.util.Locale;
import java.util.regex.Pattern;

import com.peachhacks.backend.common.ApiException;

/**
 * A card's UID as the reader reported it, in any of the usual spellings. NFC UIDs are 4, 7
 * or 10 bytes long.
 */
public final class BadgeUid {

	private static final Pattern SEPARATORS = Pattern.compile("[:\\-\\s]");

	private static final Pattern HEX = Pattern.compile("(?:[0-9A-F]{8}|[0-9A-F]{14}|[0-9A-F]{20})");

	private static final int MAX_RAW_LENGTH = 64;

	private BadgeUid() {
	}

	public static String normalise(String raw) {
		if (raw == null || raw.isBlank()) {
			throw ApiException.invalidField("uid", "Badge UID is required");
		}
		if (raw.length() <= MAX_RAW_LENGTH) {
			String uid = SEPARATORS.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
			if (HEX.matcher(uid).matches()) {
				return uid;
			}
		}
		throw ApiException.invalidField("uid", "Badge UID must be 8, 14 or 20 hexadecimal characters");
	}

}
