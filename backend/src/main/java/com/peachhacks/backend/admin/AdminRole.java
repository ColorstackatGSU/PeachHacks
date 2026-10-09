package com.peachhacks.backend.admin;

import java.util.Locale;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;

public enum AdminRole {

	ADMIN, VOLUNTEER;

	public String authority() {
		return "ROLE_" + name();
	}

	/** There is no default: an account is never given a role nobody chose. */
	public static AdminRole parse(String value) {
		try {
			return valueOf(Texts.orEmpty(value).toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.invalidField("role", "Role must be ADMIN or VOLUNTEER");
		}
	}

}
