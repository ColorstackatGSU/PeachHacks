package com.peachhacks.backend.common;

/** Regular expressions shared by the request records. They mirror the checks in web/src/forms/validation.js. */
public final class Patterns {

	public static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

	/**
	 * Letters and combining marks of any script, spaces, apostrophes, periods and hyphens.
	 * Names are placed in emails, so nothing that could form a URL is accepted.
	 */
	public static final String NAME = "^[\\p{L}\\p{M} .'’-]*$";

	public static final String NAME_MESSAGE = "Use letters, spaces, apostrophes, periods and hyphens only";

	private Patterns() {
	}

}
