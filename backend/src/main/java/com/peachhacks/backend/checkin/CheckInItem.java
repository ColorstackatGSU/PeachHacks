package com.peachhacks.backend.checkin;

import java.time.Instant;
import java.util.UUID;

import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.admin.AdminRole;
import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationStatus;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The only registration fields a check-in volunteer may see; keep personal details out.
 * checkedInAt and checkedInBy are for the event that was asked about, generalCheckedIn is
 * always about the general event. staff marks e-board and other staff, who are checked in
 * like everyone else. checkedInBy names a staff member, so only an admin is shown it.
 */
public record CheckInItem(UUID id, String firstName, String lastName, String email, String school,
		RegistrationStatus status, Instant checkedInAt, String checkedInBy, boolean generalCheckedIn,
		boolean staff) {

	static CheckInItem of(Registration r, CheckIn checkIn, boolean generalCheckedIn) {
		return new CheckInItem(r.getId(), r.getFirstName(), r.getLastName(), r.getEmail(), r.getSchool(),
				r.getStatus(), (checkIn != null) ? checkIn.getCheckedInAt() : null,
				(checkIn != null && viewerIsAdmin()) ? checkIn.getCheckedInBy() : null, generalCheckedIn, r.isStaff());
	}

	private static boolean viewerIsAdmin() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal
				&& principal.role() == AdminRole.ADMIN;
	}

}
