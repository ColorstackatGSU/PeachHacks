package com.peachhacks.backend.checkin;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rows are written with an insert that ignores conflicts, so this entity is read-only. */
@Entity
@Table(name = "check_ins")
public class CheckIn {

	@Id
	private UUID id;

	private UUID registrationId;

	private UUID eventId;

	private Instant checkedInAt;

	private String checkedInBy;

	protected CheckIn() {
	}

	public UUID getId() {
		return id;
	}

	public UUID getRegistrationId() {
		return registrationId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public Instant getCheckedInAt() {
		return checkedInAt;
	}

	public String getCheckedInBy() {
		return checkedInBy;
	}

}
