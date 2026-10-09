package com.peachhacks.backend.admin;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admins")
public class Admin {

	@Id
	private UUID id;

	private String email;

	private String name;

	private String passwordHash;

	@Enumerated(EnumType.STRING)
	private AdminRole role;

	private Instant createdAt;

	private String passwordTokenHash;

	private Instant passwordTokenExpiresAt;

	protected Admin() {
	}

	public Admin(String email, String name, String passwordHash, AdminRole role) {
		this.id = UUID.randomUUID();
		this.email = email;
		this.name = name;
		this.passwordHash = passwordHash;
		this.role = role;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getName() {
		return name;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	/** Invited, and the owner has not chosen a password yet. */
	public boolean isPending() {
		return passwordHash == null;
	}

	void issuePasswordToken(String tokenHash, Instant expiresAt) {
		this.passwordTokenHash = tokenHash;
		this.passwordTokenExpiresAt = expiresAt;
	}

	void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
		this.passwordTokenHash = null;
		this.passwordTokenExpiresAt = null;
	}

	public AdminRole getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
