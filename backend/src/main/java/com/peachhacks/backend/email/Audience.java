package com.peachhacks.backend.email;

public enum Audience {

	PRE_REGISTRANTS, PRE_REGISTRANTS_NOT_REGISTERED, REGISTRANTS,

	/** Registrations that are ACCEPTED and whose acceptance email has been sent. */
	ACCEPTED;

	boolean registered() {
		return this == REGISTRANTS || this == ACCEPTED;
	}

}
