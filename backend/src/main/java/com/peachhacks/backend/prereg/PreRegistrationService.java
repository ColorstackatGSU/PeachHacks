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

	private record Upserted(UUID id, String unsubscribeToken, boolean inserted) {
	}

	/**
	 * One statement so concurrent submissions for the same email cannot race. xmax is 0
	 * only for a freshly inserted row, which tells an insert from an update.
	 */
	private static final String UPSERT = """
			insert into pre_registrations (id, first_name, last_name, email, school, school_email, unsubscribe_token)
			values (:id, :firstName, :lastName, :email, :school, :schoolEmail, :unsubscribeToken)
			on conflict (lower(email)) do update set
				first_name = excluded.first_name,
				last_name = excluded.last_name,
				school = excluded.school,
				school_email = excluded.school_email,
				updated_at = now()
			returning id, unsubscribe_token, (xmax = 0) as inserted
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
	 * Idempotent on email: a repeat submission updates the existing row and returns its
	 * id, so the response is the same whether or not the email was already known. Every
	 * submission asks for the school email to be confirmed; SchoolEmailService decides
	 * whether that mails a link, so repeats do not fill the school inbox.
	 */
	public UUID submit(PreRegistrationRequest request) {
		if (Texts.clean(request.website()) != null) {
			return UUID.randomUUID();
		}
		validator.validate(request);
		String firstName = request.firstName().strip();
		String email = Texts.email(request.email());
		String schoolEmail = Texts.email(request.schoolEmail());
		Upserted row = jdbc.sql(UPSERT)
			.param("id", UUID.randomUUID())
			.param("firstName", firstName)
			.param("lastName", request.lastName().strip())
			.param("email", email)
			.param("school", request.school().strip())
			.param("schoolEmail", schoolEmail)
			.param("unsubscribeToken", Tokens.random())
			.query((rs, rowNum) -> new Upserted(rs.getObject("id", UUID.class), rs.getString("unsubscribe_token"),
					rs.getBoolean("inserted")))
			.single();
		boolean unconfirmed = schoolEmails.requestConfirmation(email, schoolEmail, firstName);
		if (row.inserted()) {
			mailService.sendPreRegistrationConfirmation(email, firstName, row.unsubscribeToken(),
					unconfirmed ? schoolEmail : null);
		}
		return row.id();
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

	public void delete(UUID id) {
		PreRegistration preRegistration = get(id);
		repository.deleteById(id);
		schoolEmails.forget(preRegistration.getEmail());
	}

	private PreRegistration get(UUID id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Pre-registration not found."));
	}

}
