package com.peachhacks.backend.registration;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.common.Tokens;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Serialized as-is for the admin detail view. Updates write only the columns that changed,
 * so saving a status cannot undo an unsubscribe made while the row was loaded.
 */
@Entity
@DynamicUpdate
@Table(name = "registrations")
public class Registration {

	@Id
	private UUID id;

	private String firstName;

	private String lastName;

	private Integer age;

	private String phone;

	private String email;

	private String schoolEmail;

	/** Read from the confirmation of this row's (email, school email) pair; null until confirmed. */
	@Formula("(select c.confirmed_at from school_email_confirmations c"
			+ " where c.email = email and c.school_email = school_email)")
	private Instant schoolEmailConfirmedAt;

	private String school;

	private String levelOfStudy;

	private String countryOfResidence;

	private boolean mlhCodeOfConduct;

	private boolean mlhDataSharing;

	private boolean mlhEmailOptIn;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private String[] dietaryRestrictions;

	private String dietaryDetails;

	private String underrepresentedGroup;

	private String gender;

	private String genderSelfDescribe;

	private String pronouns;

	private String pronounsOther;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private String[] raceEthnicity;

	private String raceEthnicityOther;

	private String sexualOrientation;

	private String sexualOrientationOther;

	private String highestEducation;

	private String highestEducationOther;

	private String tshirtSize;

	private String majorFieldOfStudy;

	private String majorOther;

	private String linkedinUrl;

	@Enumerated(EnumType.STRING)
	private RegistrationStatus status;

	private boolean unsubscribed;

	private String unsubscribeToken;

	private String ticketToken;

	private Instant acceptedAt;

	private Instant acceptanceNotifiedAt;

	private Instant createdAt;

	protected Registration() {
	}

	/** Expects an already validated request. */
	static Registration from(RegistrationRequest request) {
		Registration r = new Registration();
		r.id = UUID.randomUUID();
		r.firstName = request.firstName().strip();
		r.lastName = request.lastName().strip();
		r.age = request.age();
		r.phone = request.phone().strip();
		r.email = Texts.email(request.email());
		r.schoolEmail = Texts.email(request.schoolEmail());
		r.school = request.school().strip();
		r.levelOfStudy = request.levelOfStudy().strip();
		r.countryOfResidence = request.countryOfResidence();
		r.mlhCodeOfConduct = request.mlhCodeOfConduct();
		r.mlhDataSharing = request.mlhDataSharing();
		r.mlhEmailOptIn = request.mlhEmailOptIn();
		r.dietaryRestrictions = cleanList(request.dietaryRestrictions());
		r.dietaryDetails = Texts.clean(request.dietaryDetails());
		r.underrepresentedGroup = Texts.clean(request.underrepresentedGroup());
		r.gender = Texts.clean(request.gender());
		r.genderSelfDescribe = Texts.clean(request.genderSelfDescribe());
		r.pronouns = Texts.clean(request.pronouns());
		r.pronounsOther = Texts.clean(request.pronounsOther());
		r.raceEthnicity = cleanList(request.raceEthnicity());
		r.raceEthnicityOther = Texts.clean(request.raceEthnicityOther());
		r.sexualOrientation = Texts.clean(request.sexualOrientation());
		r.sexualOrientationOther = Texts.clean(request.sexualOrientationOther());
		r.highestEducation = Texts.clean(request.highestEducation());
		r.highestEducationOther = Texts.clean(request.highestEducationOther());
		r.tshirtSize = Texts.clean(request.tshirtSize());
		r.majorFieldOfStudy = Texts.clean(request.majorFieldOfStudy());
		r.majorOther = Texts.clean(request.majorOther());
		r.linkedinUrl = Texts.clean(request.linkedinUrl());
		r.status = RegistrationStatus.PENDING;
		r.unsubscribed = false;
		r.unsubscribeToken = Tokens.random();
		r.ticketToken = Tokens.random();
		r.createdAt = Instant.now();
		return r;
	}

	private static String[] cleanList(List<String> values) {
		if (values == null) {
			return new String[0];
		}
		return values.stream().map(Texts::clean).filter(Objects::nonNull).distinct().toArray(String[]::new);
	}

	public UUID getId() {
		return id;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public Integer getAge() {
		return age;
	}

	public String getPhone() {
		return phone;
	}

	public String getEmail() {
		return email;
	}

	/** Null for registrations made before the form asked for it. */
	public String getSchoolEmail() {
		return schoolEmail;
	}

	public Instant getSchoolEmailConfirmedAt() {
		return schoolEmailConfirmedAt;
	}

	/** False also for registrations that have no school email at all. */
	public boolean isSchoolEmailConfirmed() {
		return schoolEmailConfirmedAt != null;
	}

	public String getSchool() {
		return school;
	}

	public String getLevelOfStudy() {
		return levelOfStudy;
	}

	public String getCountryOfResidence() {
		return countryOfResidence;
	}

	public boolean isMlhCodeOfConduct() {
		return mlhCodeOfConduct;
	}

	public boolean isMlhDataSharing() {
		return mlhDataSharing;
	}

	public boolean isMlhEmailOptIn() {
		return mlhEmailOptIn;
	}

	public String[] getDietaryRestrictions() {
		return dietaryRestrictions;
	}

	public String getDietaryDetails() {
		return dietaryDetails;
	}

	public String getUnderrepresentedGroup() {
		return underrepresentedGroup;
	}

	public String getGender() {
		return gender;
	}

	public String getGenderSelfDescribe() {
		return genderSelfDescribe;
	}

	public String getPronouns() {
		return pronouns;
	}

	public String getPronounsOther() {
		return pronounsOther;
	}

	public String[] getRaceEthnicity() {
		return raceEthnicity;
	}

	public String getRaceEthnicityOther() {
		return raceEthnicityOther;
	}

	public String getSexualOrientation() {
		return sexualOrientation;
	}

	public String getSexualOrientationOther() {
		return sexualOrientationOther;
	}

	public String getHighestEducation() {
		return highestEducation;
	}

	public String getHighestEducationOther() {
		return highestEducationOther;
	}

	public String getTshirtSize() {
		return tshirtSize;
	}

	public String getMajorFieldOfStudy() {
		return majorFieldOfStudy;
	}

	public String getMajorOther() {
		return majorOther;
	}

	public String getLinkedinUrl() {
		return linkedinUrl;
	}

	public RegistrationStatus getStatus() {
		return status;
	}

	/**
	 * A change of status starts the acceptance over: someone accepted again after being
	 * moved out has to be told again. Saving the status it already has changes nothing.
	 */
	public boolean changeStatus(RegistrationStatus status, Instant now) {
		if (this.status == status) {
			return false;
		}
		this.status = status;
		this.acceptedAt = (status == RegistrationStatus.ACCEPTED) ? now : null;
		this.acceptanceNotifiedAt = null;
		return true;
	}

	/** Null unless ACCEPTED, and for rows accepted before this was recorded. */
	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	/** When the acceptance email was handed to the mail provider; null while ACCEPTED means not told yet. */
	public Instant getAcceptanceNotifiedAt() {
		return acceptanceNotifiedAt;
	}

	/** Identifies the ticket in its QR code; exposed only through RegistrationDetail. */
	@JsonIgnore
	public String getTicketToken() {
		return ticketToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
