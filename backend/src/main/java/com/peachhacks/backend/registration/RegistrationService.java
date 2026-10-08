package com.peachhacks.backend.registration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.peachhacks.backend.acceptance.AgeReview;
import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.RateLimiter;
import com.peachhacks.backend.common.RequestValidator;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.discord.DiscordVerification;
import com.peachhacks.backend.email.MailService;
import com.peachhacks.backend.registration.ResumeUpload.ResumeFile;
import com.peachhacks.backend.schoolemail.SchoolEmailService;
import com.peachhacks.backend.stats.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RegistrationService {

	private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

	private static final Set<String> RESUME_FILTERS = Set.of("any", "none");

	private static final Duration ALREADY_REGISTERED_EMAIL_INTERVAL = Duration.ofHours(1);

	private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

	private final RegistrationRepository repository;

	private final SettingsService settings;

	private final RequestValidator validator;

	private final MailService mailService;

	private final ResumeService resumes;

	private final SchoolEmailService schoolEmails;

	private final AgeReview ageReview;

	private final RateLimiter rateLimiter;

	private final DiscordVerification discord;

	private final TransactionTemplate transaction;

	public RegistrationService(RegistrationRepository repository, SettingsService settings,
			RequestValidator validator, MailService mailService, ResumeService resumes,
			SchoolEmailService schoolEmails, AgeReview ageReview, RateLimiter rateLimiter,
			DiscordVerification discord, PlatformTransactionManager transactionManager) {
		this.discord = discord;
		this.ageReview = ageReview;
		this.rateLimiter = rateLimiter;
		this.repository = repository;
		this.settings = settings;
		this.validator = validator;
		this.mailService = mailService;
		this.resumes = resumes;
		this.schoolEmails = schoolEmails;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * A submission for an email that is already registered is answered exactly like a new
	 * one, with an id that belongs to nothing, so the form cannot be used to find out who
	 * has registered. Nothing is stored from it; the address's owner is told by email.
	 */
	public UUID submit(RegistrationRequest request) {
		return submit(request, null);
	}

	/** previewKey lets an organizer with the preview link register while the gate is closed. */
	public UUID submit(RegistrationRequest request, String previewKey) {
		if (!settings.isRegistrationOpenFor(previewKey)) {
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
		ResumeFile resume = (request.resume() != null) ? request.resume().toFile() : null;
		Registration registration = Registration.from(request);
		if (repository.existsByEmail(registration.getEmail())) {
			return alreadyRegistered(registration.getEmail());
		}
		try {
			transaction.executeWithoutResult(status -> {
				repository.saveAndFlush(registration);
				if (resume != null) {
					resumes.store(registration.getId(), resume);
				}
			});
		}
		catch (DataIntegrityViolationException ex) {
			// Two submissions for one email at the same moment: the unique index decides.
			return alreadyRegistered(registration.getEmail());
		}
		try {
			// A pair confirmed at pre-registration stays confirmed, and then nothing is mailed.
			boolean unconfirmed = schoolEmails.requestConfirmation(registration.getEmail(),
					registration.getSchoolEmail(), registration.getFirstName());
			mailService.sendRegistrationConfirmation(registration.getEmail(), registration.getFirstName(),
					unconfirmed ? registration.getSchoolEmail() : null);
		}
		catch (RuntimeException ex) {
			// The registration is committed; the person must not be told that it failed.
			log.error("Registration {} was saved, but its confirmation emails could not be prepared",
					registration.getId(), ex);
		}
		return registration.getId();
	}

	private UUID alreadyRegistered(String email) {
		if (rateLimiter.tryAcquire("already-registered:" + email, 1, ALREADY_REGISTERED_EMAIL_INTERVAL)) {
			mailService.sendAlreadyRegistered(email);
		}
		return UUID.randomUUID();
	}

	public Page<Registration> search(String q, String school, String status, Boolean checkedIn, String resume,
			Boolean schoolEmailConfirmed, Boolean needsAgeReview, Pageable pageable) {
		RegistrationStatus parsed = parseStatus(status);
		return repository.search(Texts.containsPattern(q), Texts.orEmpty(school), parsed == null,
				(parsed != null) ? parsed : RegistrationStatus.PENDING, checkedIn == null,
				Boolean.TRUE.equals(checkedIn), parseResumeFilter(resume), schoolEmailConfirmed == null,
				Boolean.TRUE.equals(schoolEmailConfirmed), needsAgeReview == null,
				Boolean.TRUE.equals(needsAgeReview), ageReview.minimumAge(), ageReview.host(),
				ageReview.hostLength(), pageable);
	}

	public Registration get(UUID id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Registration not found."));
	}

	/**
	 * acceptedAgeReview is how many of the registrations this request moved to ACCEPTED need
	 * an age review; it is 0 for any other status.
	 */
	public record BulkStatusResult(int changed, int unchanged, int notFound, int acceptedAgeReview) {
	}

	/**
	 * Nothing is emailed here: an accepted registration waits until the acceptance emails are
	 * sent. A linked Discord account gains or loses the Hacker role right away.
	 */
	public Registration updateStatus(UUID id, RegistrationStatus status) {
		Registration updated = transaction.execute(tx -> {
			Registration registration = get(id);
			registration.changeStatus(status, Instant.now());
			return registration;
		});
		discord.syncRoles(List.of(id));
		return updated;
	}

	/** Ids that no longer exist are counted, not refused, so one deleted row does not block the rest. */
	public BulkStatusResult updateStatuses(List<UUID> ids, RegistrationStatus status) {
		Set<UUID> distinct = new LinkedHashSet<>(ids);
		BulkStatusResult result = transaction.execute(tx -> {
			Instant now = Instant.now();
			List<Registration> found = repository.findAllById(distinct);
			List<UUID> changed = new ArrayList<>();
			for (Registration registration : found) {
				if (registration.changeStatus(status, now)) {
					changed.add(registration.getId());
				}
			}
			int acceptedAgeReview = (status == RegistrationStatus.ACCEPTED) ? ageReview.among(changed).size() : 0;
			return new BulkStatusResult(changed.size(), found.size() - changed.size(),
					distinct.size() - found.size(), acceptedAgeReview);
		});
		discord.syncRoles(distinct);
		return result;
	}

	public void resendSchoolEmailConfirmation(UUID id) {
		Registration registration = get(id);
		schoolEmails.sendNow(registration.getEmail(), registration.getSchoolEmail(), registration.getFirstName());
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

	private static String parseResumeFilter(String resume) {
		String cleaned = Texts.clean(resume);
		if (cleaned == null) {
			return "";
		}
		String filter = cleaned.toLowerCase(Locale.ROOT);
		if (!RESUME_FILTERS.contains(filter)) {
			throw ApiException.invalidField("resume", "Resume filter must be any or none");
		}
		return filter;
	}

}
