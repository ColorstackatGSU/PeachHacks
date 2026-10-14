package com.peachhacks.backend.common;

/** Regular expressions shared by the request records. They mirror the checks in web/src/forms/validation.js. */
public final class Patterns {

	public static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

	/**
	 * A well-formed address must end in .edu, in any letter case. A malformed one passes
	 * here and is left to EMAIL, so it gets that message instead of either at random.
	 */
	public static final String EDU_EMAIL = "(?s)(?!" + EMAIL + ").*|[^@\\s]+@[^@\\s]+\\.(?i:edu)";

	public static final String EDU_EMAIL_MESSAGE = "Use your school email, ending in .edu";

	/**
	 * Letters and combining marks of any script, spaces, apostrophes, periods and hyphens.
	 * Names are placed in emails, so nothing that could form a URL is accepted.
	 */
	public static final String NAME = "^[\\p{L}\\p{M} .'’-]*$";

	public static final String NAME_MESSAGE = "Use letters, spaces, apostrophes, periods and hyphens only";

	private Patterns() {
	}

}
