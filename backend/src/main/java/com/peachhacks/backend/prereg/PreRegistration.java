package com.peachhacks.backend.prereg;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Formula;

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

	/** Read from the confirmation of this row's (email, school email) pair; null until confirmed. */
	@Formula("(select c.confirmed_at from school_email_confirmations c"
			+ " where c.email = email and c.school_email = school_email)")
	private Instant schoolEmailConfirmedAt;

	protected PreRegistration() {
	}

	public UUID getId() {
		return id;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getEmail() {
		return email;
	}

	public String getSchoolEmail() {
		return schoolEmail;
	}

}
