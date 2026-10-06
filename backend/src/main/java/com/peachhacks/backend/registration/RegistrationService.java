package com.peachhacks.backend.registration;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.RequestValidator;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.email.MailService;
import com.peachhacks.backend.stats.SettingsService;
import com.peachhacks.backend.ticket.GoogleWallet;
import com.peachhacks.backend.ticket.Tickets;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

	private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

	private final RegistrationRepository repository;

	private final SettingsService settings;

	private final RequestValidator validator;

	private final MailService mailService;

	private final Tickets tickets;

	private final GoogleWallet googleWallet;

	public RegistrationService(RegistrationRepository repository, SettingsService settings,
			RequestValidator validator, MailService mailService, Tickets tickets, GoogleWallet googleWallet) {
		this.repository = repository;
		this.settings = settings;
		this.validator = validator;
		this.mailService = mailService;
		this.tickets = tickets;
		this.googleWallet = googleWallet;
	}

	public UUID submit(RegistrationRequest request) {
		if (!settings.isRegistrationOpen()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED",
					"Registration is not open yet. Pre-register to hear from us when it opens.");
		}
		if (Texts.clean(request.website()) != null) {
			return UUID.randomUUID();
		}
		validator.validate(request);
		if (!ISO_COUNTRIES.contains(request.countryOfResidence())) {
			throw ApiException.invalidField("countryOfResidence", "Choose a country from the list");
		}
		Registration registration = Registration.from(request);
		if (repository.existsByEmail(registration.getEmail())) {
			throw alreadyRegistered();
		}
		try {
			repository.saveAndFlush(registration);
		}
		catch (DataIntegrityViolationException ex) {
			// Two submissions for one email at the same moment: the unique index decides.
			throw alreadyRegistered();
		}
		mailService.sendRegistrationConfirmation(registration.getEmail(), registration.getFirstName(),
				registration.getUnsubscribeToken());
		return registration.getId();
	}

	public Page<Registration> search(String q, String school, String status, Boolean checkedIn, Pageable pageable) {
		RegistrationStatus parsed = parseStatus(status);
		return repository.search(Texts.containsPattern(q), Texts.orEmpty(school), parsed == null,
				(parsed != null) ? parsed : RegistrationStatus.PENDING, checkedIn == null,
				Boolean.TRUE.equals(checkedIn), pageable);
	}

	public Registration get(UUID id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Registration not found."));
	}

	public Registration updateStatus(UUID id, RegistrationStatus status) {
		Registration registration = get(id);
		boolean newlyAccepted = status == RegistrationStatus.ACCEPTED
				&& registration.getStatus() != RegistrationStatus.ACCEPTED;
		registration.setStatus(status);
		Registration saved = repository.save(registration);
		if (newlyAccepted) {
			sendTicketEmail(saved);
		}
		return saved;
	}

	public void resendTicketEmail(UUID id) {
		Registration registration = get(id);
		if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
			throw ApiException.validation("Only accepted registrations have a ticket to send.", null);
		}
		sendTicketEmail(registration);
	}

	private void sendTicketEmail(Registration registration) {
		String token = registration.getTicketToken();
		String ticketUrl = tickets.url(token);
		mailService.sendTicket(registration.getEmail(), registration.getFirstName(), ticketUrl, tickets.qrPng(token),
				googleWallet.saveUrl(registration, ticketUrl).orElse(null), registration.getUnsubscribeToken());
	}

	public void delete(UUID id) {
		if (!repository.existsById(id)) {
			throw ApiException.notFound("Registration not found.");
		}
		repository.deleteById(id);
	}

	private static RegistrationStatus parseStatus(String status) {
		String cleaned = Texts.clean(status);
		if (cleaned == null) {
			return null;
		}
		try {
			return RegistrationStatus.valueOf(cleaned.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.invalidField("status", "Status must be PENDING, ACCEPTED, WAITLISTED or REJECTED");
		}
	}

	private static ApiException alreadyRegistered() {
		return new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED",
				"This email address is already registered for PeachHacks.");
	}

}
