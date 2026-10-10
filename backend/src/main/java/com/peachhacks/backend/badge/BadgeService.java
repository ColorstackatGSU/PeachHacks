package com.peachhacks.backend.badge;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.peachhacks.backend.acceptance.AcceptanceService;
import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.checkin.CheckInItem;
import com.peachhacks.backend.checkin.CheckInService;
import com.peachhacks.backend.checkin.CheckInService.EventRef;
import com.peachhacks.backend.checkin.Event;
import com.peachhacks.backend.checkin.EventService;
import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.config.AcceptanceProperties;
import com.peachhacks.backend.config.BadgeProperties;
import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationRepository;
import com.peachhacks.backend.registration.RegistrationStatus;
import com.peachhacks.backend.ticket.Tickets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A badge is an NFC card bound to one registration by its UID; nothing is written to the
 * card. Binding is the front desk's check-in, so it also records the general check-in.
 */
@Service
public class BadgeService {

	public enum LanyardGroup {

		HOST, OTHER

	}

	/** color is null until the lanyard colours are configured. */
	public record Lanyard(LanyardGroup group, String label, String color) {
	}

	public enum ResolveOutcome {

		FOUND, NOT_ACCEPTED, NOT_RECOGNISED

	}

	public record ResolveResult(ResolveOutcome result, CheckInItem item, BadgeView badge, Lanyard lanyard) {
	}

	public enum BindOutcome {

		BOUND, ALREADY_BOUND, REPLACED

	}

	public record BindResult(BindOutcome result, CheckInItem item, BadgeView badge, Lanyard lanyard) {
	}

	public enum TapOutcome {

		CHECKED_IN, ALREADY_CHECKED_IN, NOT_ACCEPTED, REVOKED_BADGE, UNKNOWN_BADGE

	}

	public record TapResult(TapOutcome result, EventRef event, CheckInItem item) {
	}

	public enum LookupOutcome {

		FOUND, REVOKED_BADGE, UNKNOWN_BADGE

	}

	/** Everything a lookup account may learn about a badge's holder; keep it to these five. */
	public record Holder(String firstName, String lastName, String school, boolean accepted, boolean checkedIn) {
	}

	public record LookupResult(LookupOutcome result, Holder holder) {
	}

	static final Duration TAP_MAX_AGE = Duration.ofHours(72);

	static final Duration TAP_CLOCK_SKEW = Duration.ofMinutes(1);

	private static final int BIND_ATTEMPTS = 3;

	private static final Logger log = LoggerFactory.getLogger(BadgeService.class);

	private final BadgeRepository badges;

	private final RegistrationRepository registrations;

	private final CheckInService checkIns;

	private final EventService events;

	private final AcceptanceService acceptances;

	private final AcceptanceProperties acceptanceProperties;

	private final BadgeProperties properties;

	public BadgeService(BadgeRepository badges, RegistrationRepository registrations, CheckInService checkIns,
			EventService events, AcceptanceService acceptances, AcceptanceProperties acceptanceProperties,
			BadgeProperties properties) {
		this.badges = badges;
		this.registrations = registrations;
		this.checkIns = checkIns;
		this.events = events;
		this.acceptances = acceptances;
		this.acceptanceProperties = acceptanceProperties;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public ResolveResult resolve(String code, UUID registrationId) {
		Optional<Registration> found = (registrationId != null) ? registrations.findById(registrationId)
				: Tickets.tokenFrom(code).flatMap(registrations::findByTicketToken);
		if (found.isEmpty()) {
			return new ResolveResult(ResolveOutcome.NOT_RECOGNISED, null, null, null);
		}
		Registration registration = found.get();
		CheckInItem item = checkIns.item(registration, events.general());
		BadgeView badge = active(registration.getId());
		if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
			return new ResolveResult(ResolveOutcome.NOT_ACCEPTED, item, badge, null);
		}
		return new ResolveResult(ResolveOutcome.FOUND, item, badge, lanyard(registration));
	}

	/**
	 * Any refusal is an exception, so the transaction rolls back and neither a badge, a
	 * revocation nor a check-in is left behind.
	 */
	@Transactional
	public BindResult bind(UUID registrationId, String rawUid, boolean replace, AdminPrincipal by) {
		String uid = BadgeUid.normalise(rawUid);
		Registration registration = registrations.findById(registrationId)
			.orElseThrow(() -> ApiException.notFound("Registration not found."));
		if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
			throw new ApiException(HttpStatus.CONFLICT, "NOT_ACCEPTED", CheckInService.NOT_ACCEPTED_MESSAGE);
		}
		Lanyard lanyard = lanyard(registration);
		// Losing a race to another volunteer inserts nothing; the next pass reads what the
		// winner committed and answers from that.
		for (int attempt = 0; attempt < BIND_ATTEMPTS; attempt++) {
			Optional<Badge> holder = badges.findByUidAndRevokedAtIsNull(uid);
			if (holder.isPresent()) {
				if (!holder.get().getRegistrationId().equals(registrationId)) {
					throw inUse();
				}
				return bound(BindOutcome.ALREADY_BOUND, registration, lanyard, by);
			}
			boolean replacing = badges.findByRegistrationIdAndRevokedAtIsNull(registrationId).isPresent();
			if (replacing && !replace) {
				throw new ApiException(HttpStatus.CONFLICT, "HAS_BADGE",
						"This person already has a badge. Replace it only if the old one is lost.");
			}
			Instant now = Instant.now();
			if (replacing) {
				badges.revokeActive(registrationId, now, by.name());
			}
			if (badges.insertIfAbsent(UUID.randomUUID(), uid, registrationId, now, by.name()) == 1) {
				log.info("Badge {} bound to registration {} by {}{}", uid, registrationId, by.email(),
						replacing ? ", replacing the previous badge" : "");
				return bound(replacing ? BindOutcome.REPLACED : BindOutcome.BOUND, registration, lanyard, by);
			}
		}
		throw inUse();
	}

	@Transactional
	public TapResult tap(String rawUid, UUID eventId, Instant tappedAt, AdminPrincipal by) {
		String uid = BadgeUid.normalise(rawUid);
		Event event = events.resolve(eventId);
		EventRef ref = EventRef.of(event);
		Optional<Registration> holder = holder(uid);
		if (holder.isEmpty()) {
			return new TapResult(badges.existsByUid(uid) ? TapOutcome.REVOKED_BADGE : TapOutcome.UNKNOWN_BADGE, ref,
					null);
		}
		Registration registration = holder.get();
		if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
			return new TapResult(TapOutcome.NOT_ACCEPTED, ref, checkIns.item(registration, event));
		}
		boolean recorded = checkIns.record(registration, event, by, tapTime(tappedAt, Instant.now()));
		return new TapResult(recorded ? TapOutcome.CHECKED_IN : TapOutcome.ALREADY_CHECKED_IN, ref,
				checkIns.item(registration, event));
	}

	@Transactional(readOnly = true)
	public LookupResult lookup(String rawUid) {
		String uid = BadgeUid.normalise(rawUid);
		Optional<Registration> holder = holder(uid);
		if (holder.isEmpty()) {
			return new LookupResult(
					badges.existsByUid(uid) ? LookupOutcome.REVOKED_BADGE : LookupOutcome.UNKNOWN_BADGE, null);
		}
		Registration registration = holder.get();
		return new LookupResult(LookupOutcome.FOUND,
				new Holder(registration.getFirstName(), registration.getLastName(), registration.getSchool(),
						registration.getStatus() == RegistrationStatus.ACCEPTED,
						checkIns.generalCheckedIn(registration.getId())));
	}

	@Transactional
	public void revoke(UUID registrationId, AdminPrincipal by) {
		if (!registrations.existsById(registrationId)) {
			throw ApiException.notFound("Registration not found.");
		}
		if (badges.revokeActive(registrationId, Instant.now(), by.name()) == 0) {
			throw ApiException.notFound("This person has no badge.");
		}
		log.info("Badge of registration {} revoked by {}", registrationId, by.email());
	}

	public BadgeView active(UUID registrationId) {
		return badges.findByRegistrationIdAndRevokedAtIsNull(registrationId).map(BadgeView::of).orElse(null);
	}

	/**
	 * A phone that was offline sends its taps later with the time it read the card. That time
	 * is believed only inside a window, so a wrong phone clock cannot write a check-in far in
	 * the past or in the future.
	 */
	static Instant tapTime(Instant tappedAt, Instant now) {
		if (tappedAt == null || tappedAt.isAfter(now.plus(TAP_CLOCK_SKEW))
				|| tappedAt.isBefore(now.minus(TAP_MAX_AGE))) {
			return now;
		}
		return tappedAt;
	}

	private BindResult bound(BindOutcome outcome, Registration registration, Lanyard lanyard, AdminPrincipal by) {
		Event general = events.general();
		checkIns.record(registration, general, by, Instant.now());
		return new BindResult(outcome, checkIns.item(registration, general), active(registration.getId()), lanyard);
	}

	private Optional<Registration> holder(String uid) {
		return badges.findByUidAndRevokedAtIsNull(uid)
			.flatMap(badge -> registrations.findById(badge.getRegistrationId()));
	}

	private Lanyard lanyard(Registration registration) {
		if (acceptances.isHostSchool(registration.getId())) {
			return new Lanyard(LanyardGroup.HOST, acceptanceProperties.hostSchoolName() + " hacker",
					properties.lanyardHostColor());
		}
		return new Lanyard(LanyardGroup.OTHER, "Hacker from another school", properties.lanyardOtherColor());
	}

	private static ApiException inUse() {
		return new ApiException(HttpStatus.CONFLICT, "BADGE_IN_USE",
				"This badge already belongs to someone else. Use a different badge, or ask an organizer to revoke"
						+ " it.");
	}

}
