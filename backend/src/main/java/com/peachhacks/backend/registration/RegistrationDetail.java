package com.peachhacks.backend.registration;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.peachhacks.backend.checkin.CheckInService;

/**
 * The registration's own fields stay at the top level of the JSON. checkedInAt and
 * checkedInBy are the general check-in; the ticket fields are null unless the status is
 * ACCEPTED, because no ticket exists for anyone else; googleWalletUrl is also null while
 * Google Wallet is not configured. resume is null when none was uploaded, and resumeOptIn
 * (sponsors may receive it) is then always false. schoolEmailConfirmed and
 * schoolEmailConfirmedAt come from the registration itself; an unconfirmed school email
 * does not stop any status change, it is there for the organizer to see. ageReview is the
 * same kind of information: under the minimum age for students of other schools and not
 * at the host school.
 */
public record RegistrationDetail(@JsonUnwrapped Registration registration, Instant checkedInAt, String checkedInBy,
		List<CheckInService.EventCheckIn> checkIns, String ticketToken, String ticketUrl,
		String googleWalletUrl, ResumeInfo resume, boolean resumeOptIn, boolean ageReview) {
}
