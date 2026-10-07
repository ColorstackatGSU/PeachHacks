package com.peachhacks.backend.prereg;

import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.PageResponse;
import com.peachhacks.backend.common.RequestValidator;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.common.Tokens;
import com.peachhacks.backend.email.MailService;
import com.peachhacks.backend.schoolemail.SchoolEmailService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PreRegistrationService {

	private record Stored(String firstName, String schoolEmail) {
	}

	/** One statement, so concurrent submissions for the same email cannot both insert. */
	private static final String INSERT = """
			insert into pre_registrations (id, first_name, last_name, email, school, school_email, unsubscribe_token)
			values (:id, :firstName, :lastName, :email, :school, :schoolEmail, :unsubscribeToken)
			on conflict (lower(email)) do nothing
			""";

	private final JdbcClient jdbc;

	private final PreRegistrationRepository repository;

	private final RequestValidator validator;

	private final MailService mailService;

	private final SchoolEmailService schoolEmails;

	public PreRegistrationService(JdbcClient jdbc, PreRegistrationRepository repository, RequestValidator validator,
			MailService mailService, SchoolEmailService schoolEmails) {
		this.jdbc = jdbc;
		this.repository = repository;
		this.validator = validator;
		this.mailService = mailService;
		this.schoolEmails = schoolEmails;
	}

	/**
	 * The first submission for an email is the one that is kept: a repeat changes nothing in
	 * the stored row, because whoever sends it has not shown that the address is theirs.
	 * It is answered like a new sign-up, with an id that belongs to nothing, so the
	 * response does not say whether the email was already known. A repeat asks again for
	 * the stored school email to be confirmed; SchoolEmailService decides whether that
	 * mails a link, so repeats do not fill the school inbox.
	 */
	public UUID submit(PreRegistrationRequest request) {
		if (Texts.clean(request.website()) != null) {
			return UUID.randomUUID();
		}
		validator.validate(request);
		UUID id = UUID.randomUUID();
		String firstName = request.firstName().strip();
		String email = Texts.email(request.email());
		String schoolEmail = Texts.email(request.schoolEmail());
		boolean inserted = jdbc.sql(INSERT)
			.param("id", id)
			.param("firstName", firstName)
			.param("lastName", request.lastName().strip())
			.param("email", email)
			.param("school", request.school().strip())
			.param("schoolEmail", schoolEmail)
			.param("unsubscribeToken", Tokens.random())
			.update() == 1;
		if (inserted) {
			boolean unconfirmed = schoolEmails.requestConfirmation(email, schoolEmail, firstName);
			mailService.sendPreRegistrationConfirmation(email, firstName, unconfirmed ? schoolEmail : null);
			return id;
		}
		jdbc.sql("select first_name, school_email from pre_registrations where email = :email")
			.param("email", email)
			.query((rs, rowNum) -> new Stored(rs.getString("first_name"), rs.getString("school_email")))
			.optional()
			.filter(stored -> stored.schoolEmail() != null)
			.ifPresent(stored -> schoolEmails.requestConfirmation(email, stored.schoolEmail(), stored.firstName()));
		return UUID.randomUUID();
	}

	public Page<PreRegistrationView> search(String q, String school, Boolean schoolEmailConfirmed,
			Pageable pageable) {
		return repository.search(Texts.containsPattern(q), Texts.orEmpty(school), schoolEmailConfirmed == null,
				Boolean.TRUE.equals(schoolEmailConfirmed), pageable);
	}

	public PageResponse<PreRegistrationView> page(String q, String school, Boolean schoolEmailConfirmed, int page,
			int size) {
		Pageable pageable = PageResponse.pageable(page, size);
		return PageResponse.of(search(q, school, schoolEmailConfirmed, pageable), pageable, view -> view);
	}

	public void resendSchoolEmailConfirmation(UUID id) {
		PreRegistration preRegistration = get(id);
		schoolEmails.sendNow(preRegistration.getEmail(), preRegistration.getSchoolEmail(),
				preRegistration.getFirstName());
	}

	private PreRegistration get(UUID id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Pre-registration not found."));
	}

}
