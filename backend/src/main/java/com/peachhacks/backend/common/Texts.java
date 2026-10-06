package com.peachhacks.backend.common;

import java.util.Locale;

public final class Texts {

	private Texts() {
	}

	public static String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** Emails are stored trimmed and lower-cased so equality is case-insensitive everywhere. */
	public static String email(String value) {
		String cleaned = clean(value);
		return (cleaned != null) ? cleaned.toLowerCase(Locale.ROOT) : null;
	}

	public static String containsPattern(String query) {
		String cleaned = clean(query);
		if (cleaned == null) {
			return "%";
		}
		String escaped = cleaned.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escaped + "%";
	}

	public static String orEmpty(String value) {
		String cleaned = clean(value);
		return (cleaned != null) ? cleaned : "";
	}

}
