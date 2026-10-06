package com.peachhacks.backend.registration;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.peachhacks.backend.checkin.CheckIn;
import com.peachhacks.backend.checkin.CheckInService;
import com.peachhacks.backend.common.Csv;
import com.peachhacks.backend.common.PageResponse;
import com.peachhacks.backend.ticket.GoogleWallet;
import com.peachhacks.backend.ticket.Tickets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

	private static final List<String> CSV_HEADER = List.of("id", "status", "createdAt", "firstName", "lastName", "age",
			"phone", "email", "school", "levelOfStudy", "countryOfResidence", "mlhCodeOfConduct", "mlhDataSharing",
			"mlhEmailOptIn", "dietaryRestrictions", "dietaryDetails", "underrepresentedGroup", "gender",
			"genderSelfDescribe", "pronouns", "pronounsOther", "raceEthnicity", "raceEthnicityOther",
			"sexualOrientation", "sexualOrientationOther", "highestEducation", "highestEducationOther", "tshirtSize",
			"shippingLine1", "shippingLine2", "shippingCity", "shippingState", "shippingCountry",
			"shippingPostalCode", "majorFieldOfStudy", "majorOther", "linkedinUrl", "checked_in_at",
			"has_resume", "resume_opt_in", "school_email");

	private final RegistrationService service;

	private final CheckInService checkIns;

	private final Tickets tickets;

	private final GoogleWallet googleWallet;

	private final ResumeService resumes;

	public AdminRegistrationController(RegistrationService service, CheckInService checkIns, Tickets tickets,
			GoogleWallet googleWallet, ResumeService resumes) {
		this.service = service;
		this.checkIns = checkIns;
		this.tickets = tickets;
		this.googleWallet = googleWallet;
		this.resumes = resumes;
	}

	@GetMapping
	PageResponse<RegistrationSummary> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String q,
			@RequestParam(required = false) String school, @RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean checkedIn, @RequestParam(required = false) String resume) {
		Pageable pageable = PageResponse.pageable(page, size);
		Page<Registration> result = service.search(q, school, status, checkedIn, resume, pageable);
		List<UUID> ids = result.getContent().stream().map(Registration::getId).toList();
		Map<UUID, CheckIn> general = checkIns.general(ids);
		Map<UUID, RegistrationResume> uploaded = resumes.byRegistration(ids);
		return PageResponse.of(result, pageable,
				r -> RegistrationSummary.from(r, checkedInAt(general, r), uploaded.get(r.getId())));
	}

	@GetMapping("/export.csv")
	ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
			@RequestParam(required = false) String school, @RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean checkedIn, @RequestParam(required = false) String resume) {
		Map<UUID, CheckIn> general = checkIns.general();
		Map<UUID, RegistrationResume> uploaded = resumes.byRegistration();
		Csv csv = new Csv(CSV_HEADER);
		for (Registration r : service.search(q, school, status, checkedIn, resume, Pageable.unpaged())) {
			RegistrationResume file = uploaded.get(r.getId());
			ShippingAddress address = (r.getShippingAddress() != null) ? r.getShippingAddress()
					: new ShippingAddress(null, null, null, null, null, null);
			csv.row(Arrays.asList(r.getId(), r.getStatus(), r.getCreatedAt(), r.getFirstName(), r.getLastName(),
					r.getAge(), r.getPhone(), r.getEmail(), r.getSchool(), r.getLevelOfStudy(),
					r.getCountryOfResidence(), r.isMlhCodeOfConduct(), r.isMlhDataSharing(), r.isMlhEmailOptIn(),
					String.join("; ", r.getDietaryRestrictions()), r.getDietaryDetails(),
					r.getUnderrepresentedGroup(), r.getGender(), r.getGenderSelfDescribe(), r.getPronouns(),
					r.getPronounsOther(), String.join("; ", r.getRaceEthnicity()), r.getRaceEthnicityOther(),
					r.getSexualOrientation(), r.getSexualOrientationOther(), r.getHighestEducation(),
					r.getHighestEducationOther(), r.getTshirtSize(), address.line1(), address.line2(), address.city(),
					address.state(), address.country(), address.postalCode(), r.getMajorFieldOfStudy(),
					r.getMajorOther(), r.getLinkedinUrl(), checkedInAt(general, r), file != null,
					file != null && file.isSponsorOptIn(), r.getSchoolEmail()));
		}
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

	@PostMapping("/{id}/ticket-email")
	ResponseEntity<Void> resendTicketEmail(@PathVariable UUID id) {
		service.resendTicketEmail(id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	private RegistrationDetail detail(Registration r) {
		List<CheckInService.EventCheckIn> all = checkIns.forRegistration(r.getId());
		CheckInService.EventCheckIn general = all.stream()
			.filter(CheckInService.EventCheckIn::general)
			.findFirst()
			.orElse(null);
		boolean hasTicket = r.getStatus() == RegistrationStatus.ACCEPTED;
		String ticketUrl = hasTicket ? tickets.url(r.getTicketToken()) : null;
		RegistrationResume resume = resumes.find(r.getId()).orElse(null);
		return new RegistrationDetail(r, (general != null) ? general.checkedInAt() : null,
				(general != null) ? general.checkedInBy() : null, all, hasTicket ? r.getTicketToken() : null,
				ticketUrl, hasTicket ? googleWallet.saveUrl(r, ticketUrl).orElse(null) : null,
				(resume != null) ? ResumeInfo.from(resume) : null, resume != null && resume.isSponsorOptIn());
	}

	private static Instant checkedInAt(Map<UUID, CheckIn> general, Registration r) {
		CheckIn checkIn = general.get(r.getId());
		return (checkIn != null) ? checkIn.getCheckedInAt() : null;
	}

}
