package com.peachhacks.backend.email;

public enum CampaignKind {

	/**
	 * Logistics for people who are coming. Always delivered, whatever the unsubscribed flag
	 * says, and without an unsubscribe link, so it may only go to people who registered.
	 */
	EVENT_UPDATE,

	/** Everything else. Skips people who unsubscribed and carries the unsubscribe link. */
	ANNOUNCEMENT;

	public boolean allows(Audience audience) {
		return this == ANNOUNCEMENT || audience.registered();
	}

}
