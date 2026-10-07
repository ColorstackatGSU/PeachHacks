package com.peachhacks.backend.registration;

import java.util.List;

import com.peachhacks.backend.common.Patterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Choice fields carry the label text shown on the form; only presence and length are
 * checked here, the form owns the option lists. The phone and LinkedIn rules are the ones
 * in web/src/forms/validation.js.
 */
public record RegistrationRequest(
		@NotBlank(message = "First name is required") @Size(max = 100,
				message = "First name must be at most 100 characters") @Pattern(regexp = Patterns.NAME,
							message = Patterns.NAME_MESSAGE) String firstName,
		@NotBlank(message = "Last name is required") @Size(max = 100,
				message = "Last name must be at most 100 characters") @Pattern(regexp = Patterns.NAME,
							message = Patterns.NAME_MESSAGE) String lastName,
		@NotNull(message = "Age is required") @Min(value = 13, message = "You must be at least 13") @Max(value = 120,
				message = "Enter a valid age") Integer age,
		@NotBlank(message = "Phone number is required") @Size(max = 40,
				message = "Phone number must be at most 40 characters") @Pattern(
							regexp = RegistrationRequest.PHONE,
							message = "Enter a valid phone number, with area code") String phone,
		@NotBlank(message = "Email is required") @Email(regexp = Patterns.EMAIL,
				message = "Must be a valid email") @Size(max = 255,
						message = "Email must be at most 255 characters") String email,
		@NotBlank(message = "School email is required") @Email(regexp = Patterns.EMAIL,
				message = "Must be a valid email") @Size(max = 255,
						message = "School email must be at most 255 characters") String schoolEmail,
		@NotBlank(message = "School is required") @Size(max = 255,
				message = "School must be at most 255 characters") String school,
		@NotBlank(message = "Level of study is required") @Size(max = 255,
				message = "Must be at most 255 characters") String levelOfStudy,
		@NotBlank(message = "Country of residence is required") @Pattern(regexp = "[A-Z]{2}",
				message = "Must be a two-letter country code") String countryOfResidence,
		@NotNull(message = "You must agree to the MLH Code of Conduct") @AssertTrue(
				message = "You must agree to the MLH Code of Conduct") Boolean mlhCodeOfConduct,
		@NotNull(message = "You must agree to the MLH data sharing terms") @AssertTrue(
				message = "You must agree to the MLH data sharing terms") Boolean mlhDataSharing,
		@NotNull(message = "Choose whether to receive MLH emails") Boolean mlhEmailOptIn,

		@Size(max = 30, message = "Choose at most 30 options") List<@Size(max = 100,
				message = "Each option must be at most 100 characters") String> dietaryRestrictions,
		@Size(max = 1000, message = "Must be at most 1000 characters") String dietaryDetails,
		@Size(max = 255, message = "Must be at most 255 characters") String underrepresentedGroup,
		@Size(max = 255, message = "Must be at most 255 characters") String gender,
		@Size(max = 255, message = "Must be at most 255 characters") String genderSelfDescribe,
		@Size(max = 255, message = "Must be at most 255 characters") String pronouns,
		@Size(max = 255, message = "Must be at most 255 characters") String pronounsOther,
		@Size(max = 30, message = "Choose at most 30 options") List<@Size(max = 100,
				message = "Each option must be at most 100 characters") String> raceEthnicity,
		@Size(max = 255, message = "Must be at most 255 characters") String raceEthnicityOther,
		@Size(max = 255, message = "Must be at most 255 characters") String sexualOrientation,
		@Size(max = 255, message = "Must be at most 255 characters") String sexualOrientationOther,
		@Size(max = 255, message = "Must be at most 255 characters") String highestEducation,
		@Size(max = 255, message = "Must be at most 255 characters") String highestEducationOther,
		@Size(max = 255, message = "Must be at most 255 characters") String tshirtSize,
		@Size(max = 255, message = "Must be at most 255 characters") String majorFieldOfStudy,
		@Size(max = 255, message = "Must be at most 255 characters") String majorOther,
		@Size(max = 255, message = "Must be at most 255 characters") @Pattern(regexp = RegistrationRequest.LINKEDIN_URL,
				message = "Enter a LinkedIn link, like https://www.linkedin.com/in/yourname, or leave this blank") String linkedinUrl,
		/* Checked by ResumeUpload.toFile, not by bean validation. */
		ResumeUpload resume,
		/* Consent to pass the resume to sponsors; ignored without a resume. */
		Boolean resumeOptIn,
		/* Honeypot: real visitors never see or fill this field. */
		String website) {

	/** 7 to 15 digits, written with digits, spaces and + ( ) - . x # only. */
	static final String PHONE = "^(?=(?:[^0-9]*[0-9]){7,15}[^0-9]*$)[0-9+()\\-.\\sxX#]+$";

	/** Blank, or an http(s) URL whose host is linkedin.com or one of its subdomains. */
	static final String LINKEDIN_URL = "^\\s*(?:(?i)https?://(?:[a-z0-9-]+\\.)*linkedin\\.com(?::[0-9]+)?(?:[/?#]\\S*)?)?\\s*$";

}
