package com.peachhacks.backend.admin;

import java.util.UUID;

public record AdminPrincipal(UUID id, String email, String name, String tokenHash) {

	@Override
	public String toString() {
		return "AdminPrincipal[id=" + id + ", email=" + email + "]";
	}

}
