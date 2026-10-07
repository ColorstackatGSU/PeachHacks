package com.peachhacks.backend.checkin;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class Event {

	@Id
	private UUID id;

	private String name;

	private Instant startsAt;

	/** True only for the built-in event that the migration seeds; it stands for attendance. */
	private boolean general;

	private Instant createdAt;

	protected Event() {
	}

	public Event(String name, Instant startsAt) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.startsAt = startsAt;
		this.general = false;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	void setStartsAt(Instant startsAt) {
		this.startsAt = startsAt;
	}

	public boolean isGeneral() {
		return general;
	}

}
