package com.peachhacks.backend.badge;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rows are written by the repository's own statements, so this entity is read-only. */
@Entity
@Table(name = "badges")
public class Badge {

	@Id
	private UUID id;

	private String uid;

	private UUID registrationId;

	private Instant boundAt;

	private String boundBy;

	private Instant revokedAt;

	private String revokedBy;

	protected Badge() {
	}

	public UUID getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public UUID getRegistrationId() {
		return registrationId;
	}

	public Instant getBoundAt() {
		return boundAt;
	}

	public String getBoundBy() {
		return boundBy;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public String getRevokedBy() {
		return revokedBy;
	}

}
