package com.peachhacks.backend.admin;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Only the SHA-256 of the bearer token is stored. */
@Entity
@Table(name = "admin_sessions")
public class AdminSession {

	@Id
	private UUID id;

	private UUID adminId;

	private String tokenHash;

	private Instant expiresAt;

	private Instant createdAt;

	protected AdminSession() {
	}

	public AdminSession(UUID adminId, String tokenHash, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.adminId = adminId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

}
