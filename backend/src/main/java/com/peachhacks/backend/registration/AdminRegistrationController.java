package com.peachhacks.backend.registration;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.peachhacks.backend.acceptance.AcceptanceMailer;
import com.peachhacks.backend.acceptance.AgeReview;
import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.checkin.CheckIn;
import com.peachhacks.backend.checkin.CheckInService;
import com.peachhacks.backend.common.Csv;
import com.peachhacks.backend.common.PageResponse;
import com.peachhacks.backend.ticket.Tickets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/registrations")
public class AdminRegistrationController {

	public record StatusRequest(@NotNull(message = "Status is required") RegistrationStatus status) {
	}

	public record BulkStatusRequest(
			@NotEmpty(message = "Choose at least one registration") @Size(max = 500,
					message = "At most 500 registrations at a time") List<@NotNull(
							message = "Ids must not be null") UUID> ids,
			@NotNull(message = "Status is required") RegistrationStatus status) {
	}

	private static final List<String> CSV_HEADER = List.of("id", "status", "createdAt", "firstName", "lastName", "age",
			"phone", "email", "school", "levelOfStudy", "graduationYear", "graduationMonth",
			"countryOfResidence", "mlhCodeOfConduct", "mlhDataSharing",
			"mlhEmailOptIn", "dietaryRestrictions", "dietaryDetails", "underrepresentedGroup", "gender",
			"genderSelfDescribe", "pronouns", "pronounsOther", "raceEthnicity", "raceEthnicityOther",
			"sexualOrientation", "sexualOrientationOther", "highestEducation", "highestEducationOther", "tshirtSize",
			"majorFieldOfStudy", "majorOther", "linkedinUrl", "githubUrl", "checked_in_at",
			"has_resume", "school_email", "school_email_confirmed", "age_review");

	private static final Logger log = LoggerFactory.getLogger(AdminRegistrationController.class);

	private final RegistrationService service;

	private final CheckInService checkIns;

	private final Tickets tickets;

	private final ResumeService resumes;

	private final AcceptanceMailer acceptanceMailer;

	private final AgeReview ageReview;

	public AdminRegistrationController(RegistrationService service, CheckInService checkIns, Tickets tickets,
			ResumeService resumes, AcceptanceMailer acceptanceMailer, AgeReview ageReview) {
		this.ageReview = ageReview;
		this.service = service;
		this.checkIns = checkIns;
		this.tickets = tickets;
		this.resumes = resumes;
		this.acceptanceMailer = acceptanceMailer;
	}

	@GetMapping
	PageResponse<RegistrationSummary> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String q,
			@RequestParam(required = false) String school, @RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean checkedIn, @RequestParam(required = false) String resume,
			@RequestParam(required = false) Boolean schoolEmailConfirmed,
			@RequestParam(name = "ageReview", required = false) Boolean ageReviewFilter) {
		Pageable pageable = PageResponse.pageable(page, size);
		Page<Registration> result = service.search(q, school, status, checkedIn, resume, schoolEmailConfirmed,
				ageReviewFilter, pageable);
		List<UUID> ids = result.getContent().stream().map(Registration::getId).toList();
		Map<UUID, CheckIn> general = checkIns.general(ids);
		Map<UUID, RegistrationResume> uploaded = resumes.byRegistration(ids);
		Set<UUID> flagged = ageReview.among(ids);
		return PageResponse.of(result, pageable, r -> RegistrationSummary.from(r, checkedInAt(general, r),
				uploaded.get(r.getId()), flagged.contains(r.getId())));
	}

	@GetMapping("/export.csv")
	ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
			@RequestParam(required = false) String school, @RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean checkedIn, @RequestParam(required = false) String resume,
			@RequestParam(required = false) Boolean schoolEmailConfirmed,
			@RequestParam(name = "ageReview", required = false) Boolean ageReviewFilter,
			@AuthenticationPrincipal AdminPrincipal admin) {
		Map<UUID, CheckIn> general = checkIns.general();
		Map<UUID, RegistrationResume> uploaded = resumes.byRegistration();
		Set<UUID> flagged = ageReview.all();
		Csv csv = new Csv(CSV_HEADER);
		for (Registration r : service.search(q, school, status, checkedIn, resume, schoolEmailConfirmed,
				ageReviewFilter, Pageable.unpaged())) {
			RegistrationResume file = uploaded.get(r.getId());
			csv.row(Arrays.asList(r.getId(), r.getStatus(), r.getCreatedAt(), r.getFirstName(), r.getLastName(),
					r.getAge(), r.getPhone(), r.getEmail(), r.getSchool(), r.getLevelOfStudy(), r.getGraduationYear(), r.getGraduationMonth(),
					r.getCountryOfResidence(), r.isMlhCodeOfConduct(), r.isMlhDataSharing(), r.isMlhEmailOptIn(),
					String.join("; ", r.getDietaryRestrictions()), r.getDietaryDetails(),
					r.getUnderrepresentedGroup(), r.getGender(), r.getGenderSelfDescribe(), r.getPronouns(),
					r.getPronounsOther(), String.join("; ", r.getRaceEthnicity()), r.getRaceEthnicityOther(),
					r.getSexualOrientation(), r.getSexualOrientationOther(), r.getHighestEducation(),
					r.getHighestEducationOther(), r.getTshirtSize(), r.getMajorFieldOfStudy(), r.getMajorOther(),
					r.getLinkedinUrl(), r.getGithubUrl(), checkedInAt(general, r), file != null,
					r.getSchoolEmail(), r.isSchoolEmailConfirmed(), flagged.contains(r.getId())));
		}
		log.info("Registrations CSV of {} rows ({}) exported by {}", csv.rows(),
				Csv.filters("q", q, "school", school, "status", status, "checkedIn", checkedIn, "resume", resume,
						"schoolEmailConfirmed", schoolEmailConfirmed, "ageReview", ageReviewFilter),
				admin.email());
		return csv.toResponse("peachhacks-registrations");
	}

	@GetMapping("/{id}")
	RegistrationDetail get(@PathVariable UUID id) {
		return detail(service.get(id));
	}

	@PatchMapping("/{id}")
	RegistrationDetail updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
		return detail(service.updateStatus(id, request.status()));
	}

	@PostMapping("/status")
	RegistrationService.BulkStatusResult updateStatuses(@Valid @RequestBody BulkStatusRequest request) {
		return service.updateStatuses(request.ids(), request.status());
	}

	@PostMapping("/{id}/ticket-email")
	ResponseEntity<Void> sendTicketEmail(@PathVariable UUID id) {
		acceptanceMailer.sendTicketEmail(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/school-email/resend")
	ResponseEntity<Void> resendSchoolEmailConfirmation(@PathVariable UUID id) {
		service.resendSchoolEmailConfirmation(id);
		return ResponseEntity.noContent().build();
	}

	private RegistrationDetail detail(Registration r) {
		List<CheckInService.EventCheckIn> all = checkIns.forRegistration(r.getId());
		CheckInService.EventCheckIn general = all.stream()
			.filter(CheckInService.EventCheckIn::general)
			.findFirst()
			.orElse(null);
		boolean hasTicket = r.getStatus() == RegistrationStatus.ACCEPTED;
		Tickets.Links links = hasTicket ? tickets.links(r) : new Tickets.Links(null, null);
		RegistrationResume resume = resumes.find(r.getId()).orElse(null);
		return new RegistrationDetail(r, (general != null) ? general.checkedInAt() : null,
				(general != null) ? general.checkedInBy() : null, all, hasTicket ? r.getTicketToken() : null,
				links.url(), links.googleWalletUrl(),
				(resume != null) ? ResumeInfo.from(resume) : null,
				ageReview.needed(r.getId()));
	}

	private static Instant checkedInAt(Map<UUID, CheckIn> general, Registration r) {
		CheckIn checkIn = general.get(r.getId());
		return (checkIn != null) ? checkIn.getCheckedInAt() : null;
	}

}
