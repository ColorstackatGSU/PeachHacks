package com.peachhacks.backend.admin;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
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

	private Instant createdAt;

	protected Admin() {
	}

	public Admin(String email, String name, String passwordHash) {
		this.id = UUID.randomUUID();
		this.email = email;
		this.name = name;
		this.passwordHash = passwordHash;
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

	public Instant getCreatedAt() {
		return createdAt;
	}

}
