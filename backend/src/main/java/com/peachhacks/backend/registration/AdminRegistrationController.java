package com.peachhacks.backend.registration;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.peachhacks.backend.common.Csv;
import com.peachhacks.backend.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
			"shippingPostalCode", "majorFieldOfStudy", "majorOther", "linkedinUrl");

	private final RegistrationService service;

	public AdminRegistrationController(RegistrationService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<RegistrationSummary> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String q,
			@RequestParam(required = false) String school, @RequestParam(required = false) String status) {
		Pageable pageable = PageResponse.pageable(page, size);
		return PageResponse.of(service.search(q, school, status, pageable), pageable, RegistrationSummary::from);
	}

	@GetMapping("/export.csv")
	ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
			@RequestParam(required = false) String school, @RequestParam(required = false) String status) {
		Csv csv = new Csv(CSV_HEADER);
		for (Registration r : service.search(q, school, status, Pageable.unpaged())) {
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
					r.getMajorOther(), r.getLinkedinUrl()));
		}
		return csv.toResponse("peachhacks-registrations");
	}

	@GetMapping("/{id}")
	Registration get(@PathVariable UUID id) {
		return service.get(id);
	}

	@PatchMapping("/{id}")
	Registration updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
		return service.updateStatus(id, request.status());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
