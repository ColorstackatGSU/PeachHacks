package com.peachhacks.backend.badge;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rows are written by the repository's own statements, so this entity is read-only. */
@Entity
@Table(name = "badges")
public class Badge {

	@Id
	private UUID id;

	private String uid;

	/** Null for a sponsor badge, and only then. */
	private UUID registrationId;

	@Enumerated(EnumType.STRING)
	private BadgeKind kind;

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

	public BadgeKind getKind() {
		return kind;
	}

	public boolean isSponsor() {
		return kind == BadgeKind.SPONSOR;
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
