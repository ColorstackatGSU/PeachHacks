package com.peachhacks.backend.prereg;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Rows are written by {@link PreRegistrationService} with an upsert; this mapping is read-only. */
@Entity
@Table(name = "pre_registrations")
public class PreRegistration {

	@Id
	private UUID id;

	private String firstName;

	private String lastName;

	private String email;

	private String school;

	private String schoolEmail;

	private boolean unsubscribed;

	private String unsubscribeToken;

	private Instant createdAt;

	private Instant updatedAt;

	protected PreRegistration() {
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

}
