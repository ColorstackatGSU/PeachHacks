package com.peachhacks.backend.checkin;

import java.time.Instant;
import java.util.UUID;

import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationStatus;

/**
 * The only registration fields a check-in volunteer may see; keep personal details out.
 * checkedInAt and checkedInBy are for the event that was asked about, generalCheckedIn is
 * always about the general event.
 */
public record CheckInItem(UUID id, String firstName, String lastName, String email, String school,
		RegistrationStatus status, Instant checkedInAt, String checkedInBy, boolean generalCheckedIn) {

	static CheckInItem of(Registration r, CheckIn checkIn, boolean generalCheckedIn) {
		return new CheckInItem(r.getId(), r.getFirstName(), r.getLastName(), r.getEmail(), r.getSchool(),
				r.getStatus(), (checkIn != null) ? checkIn.getCheckedInAt() : null,
				(checkIn != null) ? checkIn.getCheckedInBy() : null, generalCheckedIn);
	}

}
