package com.peachhacks.backend.email;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_campaigns")
public class EmailCampaign {

	public enum Status {

		QUEUED, SENDING, SENT, FAILED

	}

	@Id
	private UUID id;

	private String subject;

	private String body;

	@Enumerated(EnumType.STRING)
	private Audience audience;

	private String school;

	private int recipientCount;

	private int sentCount;

	private int failedCount;

	@Enumerated(EnumType.STRING)
	private Status status;

	private String createdBy;

	private Instant createdAt;

	private Instant completedAt;

	protected EmailCampaign() {
	}

	public EmailCampaign(String subject, String body, Audience audience, String school, int recipientCount,
			String createdBy) {
		this.id = UUID.randomUUID();
		this.subject = subject;
		this.body = body;
		this.audience = audience;
		this.school = school;
		this.recipientCount = recipientCount;
		this.status = Status.QUEUED;
		this.createdBy = createdBy;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getSubject() {
		return subject;
	}

	public String getBody() {
		return body;
	}

	public Audience getAudience() {
		return audience;
	}

	public String getSchool() {
		return school;
	}

	public int getRecipientCount() {
		return recipientCount;
	}

	public int getSentCount() {
		return sentCount;
	}

	public int getFailedCount() {
		return failedCount;
	}

	public Status getStatus() {
		return status;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

}
