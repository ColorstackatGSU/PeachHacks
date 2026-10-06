package com.peachhacks.backend.admin;

import java.util.Locale;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;

public enum AdminRole {

	ADMIN, VOLUNTEER;

	public String authority() {
		return "ROLE_" + name();
	}

	public static AdminRole parseOrDefault(String value) {
		String cleaned = Texts.clean(value);
		if (cleaned == null) {
			return ADMIN;
		}
		try {
			return valueOf(cleaned.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.invalidField("role", "Role must be ADMIN or VOLUNTEER");
		}
	}

}
