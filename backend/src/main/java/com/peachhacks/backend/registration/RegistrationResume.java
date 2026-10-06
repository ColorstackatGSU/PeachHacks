package com.peachhacks.backend.registration;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * What is known about a stored resume. The content column is deliberately not mapped:
 * the bytes are written and read only by ResumeService, so no query through this entity
 * can load them.
 */
@Entity
@Immutable
@Table(name = "registration_resumes")
public class RegistrationResume {

	@Id
	private UUID registrationId;

	private String fileName;

	@Column(name = "size_bytes")
	private int size;

	private boolean sponsorOptIn;

	private Instant uploadedAt;

	protected RegistrationResume() {
	}

	public UUID getRegistrationId() {
		return registrationId;
	}

	public String getFileName() {
		return fileName;
	}

	public int getSize() {
		return size;
	}

	public boolean isSponsorOptIn() {
		return sponsorOptIn;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

}
