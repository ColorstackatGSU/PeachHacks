package com.peachhacks.backend.checkin;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.badge.BadgeRepository;
import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationRepository;
import com.peachhacks.backend.registration.RegistrationStatus;
import com.peachhacks.backend.ticket.Tickets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckInService {

	public record EventRef(UUID id, String name, boolean general) {

		public static EventRef of(Event event) {
			return new EventRef(event.getId(), event.getName(), event.isGeneral());
		}

	}

	public record CheckInPage(EventRef event, List<CheckInItem> items, long total, int page, int size,
			long checkedInTotal, long registrationTotal) {
	}

	public enum ScanOutcome {

		CHECKED_IN, ALREADY_CHECKED_IN, NOT_ACCEPTED, NOT_RECOGNISED

	}

	public record ScanResult(ScanOutcome result, EventRef event, CheckInItem item) {
	}

	public record EventCheckIn(UUID eventId, String name, boolean general, Instant checkedInAt, String checkedInBy) {
	}

	public static final String NOT_ACCEPTED_MESSAGE = "This person has not been accepted, so they can't be checked in."
			+ " An organizer has to accept them first.";

	private static final Logger log = LoggerFactory.getLogger(CheckInService.class);

	private final RegistrationRepository registrations;

	private final CheckInRepository checkIns;

	private final EventRepository eventRepository;

	private final EventService events;

	private final BadgeRepository badges;

	public CheckInService(RegistrationRepository registrations, CheckInRepository checkIns,
			EventRepository eventRepository, EventService events, BadgeRepository badges) {
		this.registrations = registrations;
		this.checkIns = checkIns;
		this.eventRepository = eventRepository;
		this.events = events;
		this.badges = badges;
	}

	@Transactional(readOnly = true)
	public CheckInPage search(UUID eventId, String q, Pageable pageable) {
		Event event = events.resolve(eventId);
		Page<Object[]> result = registrations.searchForCheckIn(event.getId(), Texts.containsPattern(q), pageable);
		Set<UUID> generalIds = event.isGeneral() ? Set.of()
				: general(result.getContent().stream().map(row -> ((Registration) row[0]).getId()).toList()).keySet();
		List<CheckInItem> items = result.getContent().stream().map(row -> {
			Registration registration = (Registration) row[0];
			CheckIn checkIn = (CheckIn) row[1];
			boolean general = event.isGeneral() ? checkIn != null : generalIds.contains(registration.getId());
			return CheckInItem.of(registration, checkIn, general);
		}).toList();
		return new CheckInPage(EventRef.of(event), items, result.getTotalElements(), pageable.getPageNumber(),
				pageable.getPageSize(), checkIns.countByEventId(event.getId()), registrations.count());
	}

	/** Only an accepted registration can be checked in, to any event; there is no way around it here. */
	@Transactional
	public CheckInItem checkIn(UUID registrationId, UUID eventId, AdminPrincipal by) {
		Event event = events.resolve(eventId);
		Registration registration = registration(registrationId);
		if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
			throw new ApiException(HttpStatus.CONFLICT, "NOT_ACCEPTED", NOT_ACCEPTED_MESSAGE);
		}
		record(registration, event, by);
		return item(registration, event);
	}

	/**
	 * Binding a badge is the general check-in, so undoing the general check-in takes the badge
	 * away as well; that is how a card bound to the wrong person is put right.
	 */
	@Transactional
	public CheckInItem undo(UUID registrationId, UUID eventId, AdminPrincipal by) {
		Event event = events.resolve(eventId);
		Registration registration = registration(registrationId);
		if (checkIns.deleteFor(registrationId, event.getId()) > 0) {
			log.info("Check-in of registration {} for \"{}\" undone by {}", registrationId, event.getName(), by.email());
		}
		if (event.isGeneral() && badges.revokeActive(registrationId, Instant.now(), by.name()) > 0) {
			log.info("Badge of registration {} revoked with its general check-in by {}", registrationId, by.email());
		}
		return item(registration, event);
	}

	/**
	 * A ticket whose registration is not ACCEPTED is reported and never checked in; an
	 * organizer has to accept the person first.
	 */
	@Transactional
	public ScanResult scan(String code, UUID eventId, AdminPrincipal by) {
		Event event = events.resolve(eventId);
		EventRef ref = EventRef.of(event);
		Optional<Registration> found = Tickets.tokenFrom(code).flatMap(registrations::findByTicketToken);
		if (found.isEmpty()) {
			return new ScanResult(ScanOutcome.NOT_RECOGNISED, ref, null);
		}
		Registration registration = found.get();
		if (checkIns.existsByRegistrationIdAndEventId(registration.getId(), event.getId())) {
			return new ScanResult(ScanOutcome.ALREADY_CHECKED_IN, ref, item(registration, event));
		}
		if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
			return new ScanResult(ScanOutcome.NOT_ACCEPTED, ref, item(registration, event));
		}
		boolean recorded = record(registration, event, by);
		return new ScanResult(recorded ? ScanOutcome.CHECKED_IN : ScanOutcome.ALREADY_CHECKED_IN, ref,
				item(registration, event));
	}

	public Map<UUID, CheckIn> general(Collection<UUID> registrationIds) {
		if (registrationIds.isEmpty()) {
			return Map.of();
		}
		return byRegistration(checkIns.findByEventIdAndRegistrationIdIn(events.general().getId(), registrationIds));
	}

	public Map<UUID, CheckIn> general() {
		return byRegistration(checkIns.findByEventId(events.general().getId()));
	}

	public boolean generalCheckedIn(UUID registrationId) {
		return checkIns.existsByRegistrationIdAndEventId(registrationId, events.general().getId());
	}

	public List<CheckIn> attendees(UUID eventId) {
		return checkIns.findByEventId(eventId)
			.stream()
			.sorted(Comparator.comparing(CheckIn::getCheckedInAt))
			.toList();
	}

	public List<EventCheckIn> forRegistration(UUID registrationId) {
		Map<UUID, Event> byId = new HashMap<>();
		for (Event event : eventRepository.findAll()) {
			byId.put(event.getId(), event);
		}
		return checkIns.findByRegistrationId(registrationId)
			.stream()
			.filter(checkIn -> byId.containsKey(checkIn.getEventId()))
			.sorted(Comparator.comparing(CheckIn::getCheckedInAt))
			.map(checkIn -> {
				Event event = byId.get(checkIn.getEventId());
				return new EventCheckIn(event.getId(), event.getName(), event.isGeneral(), checkIn.getCheckedInAt(),
						checkIn.getCheckedInBy());
			})
			.toList();
	}

	/** For a caller that has already decided the registration may be checked in. */
	@Transactional
	public boolean record(Registration registration, Event event, AdminPrincipal by) {
		boolean inserted = checkIns.insertIfAbsent(UUID.randomUUID(), registration.getId(), event.getId(),
				Instant.now(), by.name()) == 1;
		if (inserted) {
			log.info("Registration {} checked in for \"{}\" by {}", registration.getId(), event.getName(), by.email());
		}
		return inserted;
	}

	public CheckInItem item(Registration registration, Event event) {
		CheckIn checkIn = checkIns.findByRegistrationIdAndEventId(registration.getId(), event.getId()).orElse(null);
		boolean general = event.isGeneral() ? checkIn != null : generalCheckedIn(registration.getId());
		return CheckInItem.of(registration, checkIn, general);
	}

	private Registration registration(UUID id) {
		return registrations.findById(id).orElseThrow(() -> ApiException.notFound("Registration not found."));
	}

	private static Map<UUID, CheckIn> byRegistration(List<CheckIn> rows) {
		return rows.stream().collect(Collectors.toMap(CheckIn::getRegistrationId, checkIn -> checkIn));
	}

}
