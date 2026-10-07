package com.peachhacks.backend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.jayway.jsonpath.JsonPath;
import com.peachhacks.backend.admin.AdminPrincipal;
import com.peachhacks.backend.admin.AdminRole;
import com.peachhacks.backend.admin.AuthService;
import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Tokens;
import com.peachhacks.backend.email.CampaignService;
import com.peachhacks.backend.email.EmailMessage;
import com.peachhacks.backend.email.EmailSender;
import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationRepository;
import com.peachhacks.backend.registration.RegistrationStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.admin.bootstrap-email=Organizer@Test.local",
		"app.admin.bootstrap-password=correct-horse-battery", "app.admin.bootstrap-name=Test Organizer",
		"app.rate-limit.public-per-minute=100000", "app.rate-limit.login-per-minute=100000", "app.rate-limit.sign-up-per-window=100000",
		"app.rate-limit.sign-up-global-per-hour=100000",
		"app.email.campaign-delay=0ms" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RegistrationApiTests {

	private static final String ADMIN_EMAIL = "organizer@test.local";

	private static final String ADMIN_PASSWORD = "correct-horse-battery";

	@Autowired
	private MockMvc mockMvc;

	private static final String GENERAL_COUNT = "select count(*) from check_ins c join events e on e.id = c.event_id where e.general";

	private static final List<EmailMessage> sentEmails = new CopyOnWriteArrayList<>();

	private static final Set<String> rejectedByProvider = ConcurrentHashMap.newKeySet();

	/** While set, the provider holds every message whose subject starts with "Held". */
	private static volatile CountDownLatch providerGate;

	private static final Pattern CONFIRM_LINK = Pattern.compile("/confirm-email\\?token=([A-Za-z0-9_-]+)");

	private static final Pattern PASSWORD_LINK = Pattern.compile("/#/set-password\\?token=([A-Za-z0-9_-]+)");

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingEmail {

		@Bean
		@Primary
		EmailSender recordingEmailSender() {
			return message -> {
				CountDownLatch gate = providerGate;
				if (gate != null && message.subject().startsWith("Held")) {
					try {
						gate.await(10, TimeUnit.SECONDS);
					}
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
				if (rejectedByProvider.contains(message.to())) {
					throw new IllegalStateException("provider said no");
				}
				sentEmails.add(message);
			};
		}

	}

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private AuthService authService;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private CampaignService campaignService;

	@Autowired
	private RegistrationRepository registrationRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void aRepeatedPreRegistrationKeepsTheFirstSubmissionAndIsAnsweredLikeANewOne() throws Exception {
		String school = uniqueSchool();
		String email = unique() + "@example.com";

		String first = preRegister("Ada", "Lovelace", email, school, "A.Lovelace@School.edu").andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String second = preRegister("Augusta", "King", email.toUpperCase(), uniqueSchool(), "someone-else@school.edu")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String id = JsonPath.read(first, "$.id");
		assertThat((String) JsonPath.read(second, "$.id")).as("an id that belongs to nothing").isNotEqualTo(id);
		assertThat(jdbc.sql("select count(*) from pre_registrations where id = :id")
			.param("id", UUID.fromString(JsonPath.read(second, "$.id")))
			.query(Long.class)
			.single()).isZero();
		assertThat(emailsTo(email, "You're pre-registered", 1)).hasSize(1);
		assertThat(emailsTo("someone-else@school.edu", "Confirm", 0))
			.as("the repeat's school address is never written to")
			.isEmpty();
		mockMvc.perform(get("/admin/pre-registrations").param("school", school).header("Authorization", bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(25))
			.andExpect(jsonPath("$.items[0].id").value(id))
			.andExpect(jsonPath("$.items[0].firstName").value("Ada"))
			.andExpect(jsonPath("$.items[0].lastName").value("Lovelace"))
			.andExpect(jsonPath("$.items[0].email").value(email))
			.andExpect(jsonPath("$.items[0].schoolEmail").value("a.lovelace@school.edu"))
			.andExpect(jsonPath("$.items[0].registered").value(false))
			.andExpect(jsonPath("$.items[0].unsubscribed").value(false));
	}

	@Test
	void preRegistrationValidatesAndIgnoresHoneypotSubmissions() throws Exception {
		String school = uniqueSchool();

		preRegister("", "Lovelace", "not-an-email", school, "").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.schoolEmail").value("School email is required"))
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.firstName").value("First name is required"))
			.andExpect(jsonPath("$.fieldErrors.email").value("Must be a valid email"));
		preRegister("Ada", "Lovelace", unique() + "@example.com", school, "ada-at-school")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.schoolEmail").value("Must be a valid email"));

		mockMvc
			.perform(post("/public/pre-registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"Bot","lastName":"Bot","email":"%s@example.com","school":"%s","schoolEmail":"bot@school.edu","website":"http://spam.example"}
					""".formatted(unique(), school)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty());

		assertThat(jdbc.sql("select count(*) from pre_registrations where school = :school")
			.param("school", school)
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void registrationIsGatedUntilAnAdminOpensIt() throws Exception {
		String token = bearer();
		String email = unique() + "@example.com";
		setRegistrationOpen(token, false);

		mockMvc.perform(get("/public/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.registrationOpen").value(false));
		register(registrationJson(email, uniqueSchool(), true, true)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REGISTRATION_CLOSED"));

		setRegistrationOpen(token, true);
		mockMvc.perform(get("/public/status")).andExpect(jsonPath("$.registrationOpen").value(true));
		String school = uniqueSchool();
		String created = register(registrationJson(email, school, true, true)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(created, "$.id");

		String again = register(named(registrationJson(email.toUpperCase(), school, true, true), "Mallory", "Impostor"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat((String) JsonPath.read(again, "$.id")).as("a duplicate is answered like a new registration")
			.isNotEqualTo(id);
		register(registrationJson(email, school, true, true)).andExpect(status().isCreated());
		assertThat(jdbc.sql("select first_name from registrations where email = :email")
			.param("email", email)
			.query(String.class)
			.list()).as("nothing from the duplicate is stored").containsExactly("Ada");
		List<EmailMessage> notices = emailsTo(email, "You're already registered", 1);
		Thread.sleep(300);
		assertThat(emailsTo(email, "You're already registered", 1)).as("at most one notice an hour").hasSize(1);
		assertThat(notices.get(0).text()).contains("nothing was changed")
			.contains("You are receiving this because you registered for PeachHacks.")
			.doesNotContain("Mallory")
			.doesNotContainIgnoringCase("unsubscribe");

		mockMvc.perform(get("/admin/registrations").param("school", school).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(id))
			.andExpect(jsonPath("$.items[0].status").value("PENDING"))
			.andExpect(jsonPath("$.items[0].schoolEmail").value("ada.lovelace@school.edu"))
			.andExpect(jsonPath("$.items[0].levelOfStudy").value("Undergraduate University (3+ year)"));
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("q", "LOVELACE@school.edu")
				.header("Authorization", token))
			.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(get("/admin/registrations/" + id).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.schoolEmail").value("ada.lovelace@school.edu"))
			.andExpect(jsonPath("$.age").value(19))
			.andExpect(jsonPath("$.mlhCodeOfConduct").value(true))
			.andExpect(jsonPath("$.mlhEmailOptIn").value(false))
			.andExpect(jsonPath("$.dietaryRestrictions", hasSize(2)))
			.andExpect(jsonPath("$.raceEthnicity", hasSize(0)))
			.andExpect(jsonPath("$.shippingAddress").doesNotExist())
			.andExpect(jsonPath("$.unsubscribeToken").doesNotExist())
			.andExpect(jsonPath("$.website").doesNotExist());
		mockMvc
			.perform(patch("/admin/registrations/" + id).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACCEPTED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"));
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("status", "REJECTED")
				.header("Authorization", token))
			.andExpect(jsonPath("$.total").value(0));

		mockMvc
			.perform(get("/admin/registrations/export.csv").param("school", school).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
			.andExpect(content().string(containsString("id,status,createdAt,firstName")))
			// The phone number starts with "+" and must not reach a spreadsheet as a formula.
			.andExpect(content().string(containsString(",'+1 404 555 0100,")))
			.andExpect(content().string(containsString("Vegetarian; Halal")));

		mockMvc.perform(get("/admin/registrations/" + UUID.randomUUID()).header("Authorization", token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		setRegistrationOpen(token, false);
		deleteRegistrations(id);
	}

	@Test
	void registrationRequiresTheMlhCheckboxes() throws Exception {
		String token = bearer();
		setRegistrationOpen(token, true);

		register(registrationJson(unique() + "@example.com", uniqueSchool(), false, true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.mlhCodeOfConduct").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.mlhDataSharing").doesNotExist());
		register(registrationJson(unique() + "@example.com", uniqueSchool(), true, false))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.mlhDataSharing").isNotEmpty());
		register("{\"firstName\":\"Only\",\"age\":12,\"countryOfResidence\":\"usa\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.lastName").value("Last name is required"))
			.andExpect(jsonPath("$.fieldErrors.schoolEmail").value("School email is required"))
			.andExpect(jsonPath("$.fieldErrors.age").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.countryOfResidence").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.mlhCodeOfConduct").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.mlhDataSharing").isNotEmpty())
			.andExpect(jsonPath("$.fieldErrors.mlhEmailOptIn").isNotEmpty());

		setRegistrationOpen(token, false);
	}

	@Test
	void adminEndpointsRequireAValidToken() throws Exception {
		mockMvc.perform(get("/admin/stats"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/admin/stats").header("Authorization", "Bearer not-a-real-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		login(ADMIN_EMAIL, "wrong-password").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		login("nobody@test.local", ADMIN_PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		String body = login("ORGANIZER@test.local", ADMIN_PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andExpect(jsonPath("$.admin.email").value(ADMIN_EMAIL))
			.andExpect(jsonPath("$.admin.name").value("Test Organizer"))
			.andExpect(jsonPath("$.admin.role").value("ADMIN"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String rawToken = JsonPath.read(body, "$.token");
		String token = "Bearer " + rawToken;

		assertThat(jdbc.sql("select count(*) from admin_sessions where token_hash = :raw")
			.param("raw", rawToken)
			.query(Long.class)
			.single()).as("the raw token is never stored").isZero();
		assertThat(jdbc.sql("select password_hash from admins where email = :email")
			.param("email", ADMIN_EMAIL)
			.query(String.class)
			.single()).startsWith("$2").doesNotContain(ADMIN_PASSWORD);

		mockMvc.perform(get("/admin/auth/me").header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
		mockMvc.perform(get("/admin/stats").header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.registrationOpen").isBoolean())
			.andExpect(jsonPath("$.preRegistrations.total").isNumber())
			.andExpect(jsonPath("$.registrations.byStatus", hasSize(4)))
			.andExpect(jsonPath("$.preRegisteredNotRegistered").isNumber());

		mockMvc.perform(post("/admin/auth/logout").header("Authorization", token))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/admin/auth/me").header("Authorization", token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void expiredTokensAreRejected() throws Exception {
		String token = bearer();
		jdbc.sql("update admin_sessions set expires_at = now() - interval '1 minute' where token_hash = :hash")
			.param("hash", com.peachhacks.backend.common.Tokens.sha256(token.substring("Bearer ".length())))
			.update();

		mockMvc.perform(get("/admin/auth/me").header("Authorization", token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void adminAccountsCanBeManaged() throws Exception {
		String token = bearer();
		String email = unique() + "@test.local";

		mockMvc
			.perform(post("/admin/admins").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"name\":\"\"}".formatted(email)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
		mockMvc
			.perform(post("/admin/admins").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"name\":\"Second\"}".formatted(email)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.role").value("Choose a role"));
		assertThat(jdbc.sql("select count(*) from admins where email = :email")
			.param("email", email)
			.query(Long.class)
			.single()).as("an account is never created with a role nobody chose").isZero();
		String created = mockMvc
			.perform(post("/admin/admins").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"name\":\"Second\",\"role\":\"ADMIN\"}".formatted(email)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.pending").value(true))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(created, "$.id");
		choosePassword(created, "another-long-password");
		login(email, "another-long-password").andExpect(status().isOk());

		String me = mockMvc.perform(get("/admin/auth/me").header("Authorization", token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		mockMvc.perform(delete("/admin/admins/" + JsonPath.read(me, "$.id")).header("Authorization", token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(delete("/admin/admins/" + id).header("Authorization", token))
			.andExpect(status().isNoContent());
		login(email, "another-long-password").andExpect(status().isUnauthorized());
	}

	@Test
	void anInvitedAccountChoosesItsOwnPasswordFromAOneTimeLink() throws Exception {
		String admin = bearer();
		String email = unique() + "@test.local";
		String created = mockMvc
			.perform(post("/admin/admins").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"name\":\"Invited\",\"role\":\"VOLUNTEER\"}".formatted(email)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.pending").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(created, "$.id");
		String firstToken = passwordToken(JsonPath.read(created, "$.setPasswordUrl"));

		EmailMessage invite = awaitEmail(email, "check-in volunteer");
		assertThat(invite.text()).contains("Test Organizer added you as a check-in volunteer")
			.contains("Set your password: http://localhost:5174/#/set-password?token=" + firstToken)
			.contains("works for 7 days");
		login(email, "").andExpect(status().isBadRequest());
		login(email, "anything-at-all").andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/admins").header("Authorization", admin))
			.andExpect(jsonPath("$[?(@.id == '%s')].pending".formatted(id)).value(true))
			.andExpect(jsonPath("$[?(@.id == '%s')].setPasswordUrl".formatted(id)).value((Object) null));

		String resent = mockMvc.perform(post("/admin/admins/" + id + "/invite").header("Authorization", admin))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = passwordToken(JsonPath.read(resent, "$.setPasswordUrl"));
		assertThat(token).isNotEqualTo(firstToken);
		setPassword(firstToken, "replaced-link-password").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_LINK"));

		mockMvc
			.perform(post("/admin/auth/set-password/check").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\"}".formatted(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.name").value("Invited"))
			.andExpect(jsonPath("$.invite").value(true));
		setPassword(token, "short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
		setPassword(token, "my-own-password").andExpect(status().isNoContent());
		setPassword(token, "second-use-password").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_LINK"));
		login(email, "my-own-password").andExpect(status().isOk())
			.andExpect(jsonPath("$.admin.role").value("VOLUNTEER"));
		mockMvc.perform(get("/admin/admins").header("Authorization", admin))
			.andExpect(jsonPath("$[?(@.id == '%s')].pending".formatted(id)).value(false));
		mockMvc.perform(post("/admin/admins/" + id + "/invite").header("Authorization", admin))
			.andExpect(status().isBadRequest());

		mockMvc.perform(delete("/admin/admins/" + id).header("Authorization", admin))
			.andExpect(status().isNoContent());
	}

	@Test
	void aForgottenPasswordIsResetFromAnEmailedLinkAndEndsOtherSessions() throws Exception {
		String admin = bearer();
		Volunteer volunteer = createVolunteer(admin, "Forgetful");
		String email = jdbc.sql("select email from admins where id = :id")
			.param("id", UUID.fromString(volunteer.id()))
			.query(String.class)
			.single();
		int before = sentEmails.size();

		mockMvc
			.perform(post("/admin/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody-%s@test.local\"}".formatted(unique())))
			.andExpect(status().isNoContent());
		mockMvc
			.perform(post("/admin/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\"}".formatted(email.toUpperCase())))
			.andExpect(status().isNoContent());
		EmailMessage reset = awaitEmail(email, "Reset your PeachHacks admin password");
		assertThat(sentEmails.subList(before, sentEmails.size())).hasSize(1);
		assertThat(reset.text()).contains("works for 1 hour");
		String token = passwordToken(reset.text());

		jdbc.sql("update admins set password_token_expires_at = now() - interval '1 minute' where id = :id")
			.param("id", UUID.fromString(volunteer.id()))
			.update();
		setPassword(token, "too-late-password").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_LINK"));
		jdbc.sql("update admins set password_token_expires_at = now() + interval '1 hour' where id = :id")
			.param("id", UUID.fromString(volunteer.id()))
			.update();
		mockMvc
			.perform(post("/admin/auth/set-password/check").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\"}".formatted(token)))
			.andExpect(jsonPath("$.invite").value(false));
		setPassword(token, "remembered-password").andExpect(status().isNoContent());

		mockMvc.perform(get("/admin/auth/me").header("Authorization", volunteer.token()))
			.andExpect(status().isUnauthorized());
		login(email, "volunteer-password").andExpect(status().isUnauthorized());
		login(email, "remembered-password").andExpect(status().isOk());

		mockMvc.perform(delete("/admin/admins/" + volunteer.id()).header("Authorization", admin))
			.andExpect(status().isNoContent());
	}

	@Test
	void anyoneSignedInCanChangeTheirOwnPassword() throws Exception {
		String admin = bearer();
		Volunteer volunteer = createVolunteer(admin, "Changer");
		String email = jdbc.sql("select email from admins where id = :id")
			.param("id", UUID.fromString(volunteer.id()))
			.query(String.class)
			.single();
		String otherSession = "Bearer " + JsonPath.read(
				login(email, "volunteer-password").andReturn().getResponse().getContentAsString(), "$.token");

		mockMvc
			.perform(post("/admin/auth/change-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"volunteer-password\",\"newPassword\":\"a-brand-new-password\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc
			.perform(post("/admin/auth/change-password").header("Authorization", volunteer.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"not-my-password\",\"newPassword\":\"a-brand-new-password\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.currentPassword").isNotEmpty());
		mockMvc
			.perform(post("/admin/auth/change-password").header("Authorization", volunteer.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"volunteer-password\",\"newPassword\":\"short\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.newPassword").isNotEmpty());
		mockMvc
			.perform(post("/admin/auth/change-password").header("Authorization", volunteer.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"volunteer-password\",\"newPassword\":\"a-brand-new-password\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/admin/auth/me").header("Authorization", volunteer.token())).andExpect(status().isOk());
		mockMvc.perform(get("/admin/auth/me").header("Authorization", otherSession))
			.andExpect(status().isUnauthorized());
		login(email, "volunteer-password").andExpect(status().isUnauthorized());
		login(email, "a-brand-new-password").andExpect(status().isOk());

		mockMvc.perform(delete("/admin/admins/" + volunteer.id()).header("Authorization", admin))
			.andExpect(status().isNoContent());
	}

	@Test
	void volunteerCanSearchAndCheckInAndUndo() throws Exception {
		String admin = bearer();
		String prefix = unique();
		String school = uniqueSchool();
		String firstId = registerHacker(admin, prefix + "a@example.com", school);
		String secondId = registerHacker(admin, prefix + "b@example.com", school);
		Volunteer volunteer = createVolunteer(admin, "Door Volunteer");

		mockMvc.perform(get("/admin/check-in"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/admin/auth/me").header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("VOLUNTEER"));
		mockMvc.perform(get("/admin/events").header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].general").value(true))
			.andExpect(jsonPath("$[0].name").value("General check-in"))
			.andExpect(jsonPath("$[0].checkedIn").isNumber());

		String listed = mockMvc
			.perform(get("/admin/check-in").param("q", prefix.toUpperCase() + "A@")
				.header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.event.general").value(true))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(25))
			.andExpect(jsonPath("$.items[0].id").value(firstId))
			.andExpect(jsonPath("$.items[0].firstName").value("Ada"))
			.andExpect(jsonPath("$.items[0].lastName").value("Lovelace"))
			.andExpect(jsonPath("$.items[0].email").value(prefix + "a@example.com"))
			.andExpect(jsonPath("$.items[0].school").value(school))
			.andExpect(jsonPath("$.items[0].status").value("PENDING"))
			.andExpect(jsonPath("$.items[0].checkedInAt").value(nullValue()))
			.andExpect(jsonPath("$.items[0].checkedInBy").value(nullValue()))
			.andExpect(jsonPath("$.items[0].generalCheckedIn").value(false))
			.andExpect(content().string(not(containsString("404 555"))))

			.andExpect(content().string(not(containsString("Vegetarian"))))
			.andExpect(content().string(not(containsString(ticketToken(firstId)))))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Map<String, Object> item = JsonPath.read(listed, "$.items[0]");
		assertThat(item.keySet()).containsExactlyInAnyOrder("id", "firstName", "lastName", "email", "school", "status",
				"checkedInAt", "checkedInBy", "generalCheckedIn");
		assertThat(((Number) JsonPath.read(listed, "$.registrationTotal")).longValue())
			.isEqualTo(count("select count(*) from registrations"));
		mockMvc.perform(get("/admin/check-in").param("q", "no-such-" + prefix).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(0))
			.andExpect(jsonPath("$.items", hasSize(0)));

		long checkedInBefore = count(GENERAL_COUNT);
		for (String caller : List.of(volunteer.token(), admin)) {
			mockMvc.perform(post("/admin/check-in/" + firstId).header("Authorization", caller))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_ACCEPTED"))
				.andExpect(jsonPath("$.message").value(containsString("An organizer has to accept them first")));
			mockMvc
				.perform(post("/admin/check-in/" + firstId).param("override", "true").header("Authorization", caller))
				.andExpect(status().isConflict());
		}
		for (String status : List.of("WAITLISTED", "REJECTED")) {
			setStatus(admin, firstId, status);
			mockMvc.perform(post("/admin/check-in/" + firstId).header("Authorization", admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_ACCEPTED"));
		}
		assertThat(count(GENERAL_COUNT)).as("nobody who is not accepted was checked in").isEqualTo(checkedInBefore);
		setStatus(admin, firstId, "ACCEPTED");
		String first = mockMvc.perform(post("/admin/check-in/" + firstId).header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(firstId))
			.andExpect(jsonPath("$.checkedInAt").isNotEmpty())
			.andExpect(jsonPath("$.checkedInBy").value("Door Volunteer"))
			.andExpect(jsonPath("$.generalCheckedIn").value(true))
			.andExpect(jsonPath("$.phone").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String checkedInAt = JsonPath.read(first, "$.checkedInAt");
		mockMvc.perform(post("/admin/check-in/" + firstId).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkedInAt").value(checkedInAt))
			.andExpect(jsonPath("$.checkedInBy").value("Door Volunteer"));

		mockMvc.perform(get("/admin/check-in").param("q", prefix).header("Authorization", volunteer.token()))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.checkedInTotal").value(checkedInBefore + 1))
			.andExpect(jsonPath("$.items[0].id").value(secondId))
			.andExpect(jsonPath("$.items[1].id").value(firstId))
			.andExpect(jsonPath("$.items[1].checkedInAt").value(checkedInAt));

		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("checkedIn", "true")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(firstId))
			.andExpect(jsonPath("$.items[0].checkedInAt").value(checkedInAt));
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("checkedIn", "false")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(secondId))
			.andExpect(jsonPath("$.items[0].checkedInAt").value(nullValue()));
		mockMvc.perform(get("/admin/registrations").param("school", school).header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(2));
		mockMvc.perform(get("/admin/registrations").param("checkedIn", "maybe").header("Authorization", admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/admin/registrations/" + firstId).header("Authorization", admin))
			.andExpect(jsonPath("$.email").value(prefix + "a@example.com"))
			.andExpect(jsonPath("$.checkedInAt").value(checkedInAt))
			.andExpect(jsonPath("$.checkedInBy").value("Door Volunteer"))
			.andExpect(jsonPath("$.checkIns", hasSize(1)))
			.andExpect(jsonPath("$.checkIns[0].general").value(true));
		mockMvc.perform(get("/admin/stats").header("Authorization", admin))
			.andExpect(jsonPath("$.registrations.checkedIn").value(checkedInBefore + 1))
			.andExpect(jsonPath("$.events[0].name").value("General check-in"))
			.andExpect(jsonPath("$.events[0].checkedIn").value(checkedInBefore + 1));
		mockMvc
			.perform(get("/admin/registrations/export.csv").param("school", school).header("Authorization", admin))
			.andExpect(content()
				.string(containsString(",linkedinUrl,checked_in_at,has_resume,resume_opt_in,school_email,school_email_confirmed,age_review\r\n")))
			.andExpect(content().string(containsString(prefix + "a@example.com")))
			.andExpect(content().string(containsString(prefix + "b@example.com")))
			.andExpect(content()
				.string(containsString("," + checkedInAt + ",false,false,ada.lovelace@school.edu,false,false\r\n")));
		mockMvc
			.perform(get("/admin/registrations/export.csv").param("school", school)
				.param("checkedIn", "true")
				.header("Authorization", admin))
			.andExpect(content().string(containsString(prefix + "a@example.com")))
			.andExpect(content().string(not(containsString(prefix + "b@example.com"))));

		mockMvc.perform(delete("/admin/check-in/" + firstId).header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(firstId))
			.andExpect(jsonPath("$.checkedInAt").value(nullValue()))
			.andExpect(jsonPath("$.checkedInBy").value(nullValue()))
			.andExpect(jsonPath("$.generalCheckedIn").value(false));
		mockMvc.perform(delete("/admin/check-in/" + firstId).header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkedInAt").value(nullValue()));
		assertThat(count(GENERAL_COUNT)).isEqualTo(checkedInBefore);

		for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
				post("/admin/check-in/" + UUID.randomUUID()), delete("/admin/check-in/" + UUID.randomUUID()),
				post("/admin/check-in/not-a-uuid"),
				post("/admin/check-in/" + firstId).param("eventId", UUID.randomUUID().toString()),
				get("/admin/check-in").param("eventId", UUID.randomUUID().toString()) }) {
			mockMvc.perform(request.header("Authorization", volunteer.token()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}

		mockMvc.perform(delete("/admin/admins/" + volunteer.id()).header("Authorization", admin))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/admin/check-in").header("Authorization", volunteer.token()))
			.andExpect(status().isUnauthorized());
		deleteRegistrations(firstId, secondId);
	}

	@Test
	void workshopsHaveTheirOwnCheckInsAndReportMissingGeneralCheckIn() throws Exception {
		String admin = bearer();
		String prefix = unique();
		String registrationId = registerHacker(admin, prefix + "@example.com", uniqueSchool());
		setStatus(admin, registrationId, "ACCEPTED");
		String name = "Workshop " + unique();

		String created = mockMvc.perform(eventRequest(post("/admin/events"), admin,
				"{\"name\":\" %s \",\"startsAt\":\"2026-11-07T15:00:00Z\"}".formatted(name)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value(name))
			.andExpect(jsonPath("$.startsAt").value("2026-11-07T15:00:00Z"))
			.andExpect(jsonPath("$.general").value(false))
			.andExpect(jsonPath("$.checkedIn").value(0))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String eventId = JsonPath.read(created, "$.id");
		String generalId = jdbc.sql("select id from events where general").query(UUID.class).single().toString();

		mockMvc.perform(eventRequest(post("/admin/events"), admin, "{\"name\":\"%s\"}".formatted(name.toUpperCase())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
		mockMvc.perform(eventRequest(post("/admin/events"), admin, "{\"name\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
		mockMvc
			.perform(eventRequest(post("/admin/events"), admin,
					"{\"name\":\"%s\",\"startsAt\":\"tomorrow\"}".formatted("Other " + unique())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.startsAt").isNotEmpty());
		String renamed = name + " (room 2)";
		mockMvc.perform(eventRequest(patch("/admin/events/" + eventId), admin, "{\"name\":\"%s\"}".formatted(renamed)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(renamed))
			.andExpect(jsonPath("$.startsAt").value("2026-11-07T15:00:00Z"));
		mockMvc.perform(eventRequest(patch("/admin/events/" + eventId), admin, "{\"startsAt\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(renamed))
			.andExpect(jsonPath("$.startsAt").value(nullValue()));
		mockMvc.perform(delete("/admin/events/" + generalId).header("Authorization", admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		String workshop = mockMvc
			.perform(post("/admin/check-in/" + registrationId).param("eventId", eventId)
				.header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkedInAt").isNotEmpty())
			.andExpect(jsonPath("$.checkedInBy").value("Test Organizer"))
			.andExpect(jsonPath("$.generalCheckedIn").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String workshopAt = JsonPath.read(workshop, "$.checkedInAt");
		mockMvc.perform(get("/admin/check-in").param("q", prefix).header("Authorization", admin))
			.andExpect(jsonPath("$.items[0].checkedInAt").value(nullValue()))
			.andExpect(jsonPath("$.items[0].generalCheckedIn").value(false));
		mockMvc
			.perform(get("/admin/check-in").param("q", prefix).param("eventId", eventId).header("Authorization", admin))
			.andExpect(jsonPath("$.event.id").value(eventId))
			.andExpect(jsonPath("$.event.general").value(false))
			.andExpect(jsonPath("$.checkedInTotal").value(1))
			.andExpect(jsonPath("$.items[0].checkedInAt").value(workshopAt))
			.andExpect(jsonPath("$.items[0].generalCheckedIn").value(false));

		mockMvc.perform(post("/admin/check-in/" + registrationId).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generalCheckedIn").value(true));
		mockMvc
			.perform(get("/admin/check-in").param("q", prefix).param("eventId", eventId).header("Authorization", admin))
			.andExpect(jsonPath("$.items[0].checkedInAt").value(workshopAt))
			.andExpect(jsonPath("$.items[0].generalCheckedIn").value(true));
		mockMvc.perform(get("/admin/registrations/" + registrationId).header("Authorization", admin))
			.andExpect(jsonPath("$.checkedInAt").isNotEmpty())
			.andExpect(jsonPath("$.checkIns", hasSize(2)))
			.andExpect(jsonPath("$.checkIns[?(@.eventId == '%s')].name".formatted(eventId)).value(renamed))
			.andExpect(jsonPath("$.checkIns[?(@.eventId == '%s')].checkedInAt".formatted(eventId)).value(workshopAt));
		mockMvc.perform(get("/admin/stats").header("Authorization", admin))
			.andExpect(jsonPath("$.events[?(@.eventId == '%s')].checkedIn".formatted(eventId)).value(1))
			.andExpect(jsonPath("$.events[?(@.eventId == '%s')].name".formatted(eventId)).value(renamed));
		mockMvc.perform(get("/admin/events").header("Authorization", admin))
			.andExpect(jsonPath("$[0].id").value(generalId))
			.andExpect(jsonPath("$[?(@.id == '%s')].checkedIn".formatted(eventId)).value(1));
		mockMvc.perform(get("/admin/events/" + eventId + "/export.csv").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(content().string(containsString("event,registrationId,firstName,lastName,email,school,status,"
					+ "checked_in_at,checked_in_by\r\n")))
			.andExpect(content().string(containsString(prefix + "@example.com")))
			.andExpect(content().string(containsString(workshopAt + ",Test Organizer\r\n")));

		mockMvc
			.perform(delete("/admin/check-in/" + registrationId).param("eventId", eventId)
				.header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkedInAt").value(nullValue()))
			.andExpect(jsonPath("$.generalCheckedIn").value(true));
		mockMvc.perform(get("/admin/registrations/" + registrationId).header("Authorization", admin))
			.andExpect(jsonPath("$.checkIns", hasSize(1)))
			.andExpect(jsonPath("$.checkIns[0].general").value(true));

		mockMvc
			.perform(post("/admin/check-in/" + registrationId).param("eventId", eventId)
				.header("Authorization", admin))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/admin/events/" + eventId).header("Authorization", admin))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVENT_HAS_CHECK_INS"))
			.andExpect(jsonPath("$.message", containsString("1 check-in,")));
		mockMvc.perform(get("/admin/events").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '%s')].checkedIn".formatted(eventId)).value(1));
		assertThat(jdbc.sql("select count(*) from check_ins where registration_id = :id")
			.param("id", UUID.fromString(registrationId))
			.query(Long.class)
			.single()).as("a refused delete removes no check-in").isEqualTo(2);

		mockMvc
			.perform(delete("/admin/check-in/" + registrationId).param("eventId", eventId)
				.header("Authorization", admin))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/admin/events/" + eventId).header("Authorization", admin))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/admin/events/" + eventId).header("Authorization", admin))
			.andExpect(status().isNotFound());
		assertThat(jdbc.sql("select count(*) from check_ins where registration_id = :id")
			.param("id", UUID.fromString(registrationId))
			.query(Long.class)
			.single()).as("the general check-in is untouched").isEqualTo(1);
		deleteRegistrations(registrationId);
	}

	@Test
	void ticketsCanBeScannedByTokenOrUrlAndNonAcceptedTicketsAreNeverCheckedIn() throws Exception {
		String admin = bearer();
		String acceptedId = registerHacker(admin, unique() + "@example.com", uniqueSchool());
		String pendingId = registerHacker(admin, unique() + "@example.com", uniqueSchool());
		setStatus(admin, acceptedId, "ACCEPTED");
		Volunteer volunteer = createVolunteer(admin, "Scanner");
		String token = ticketToken(acceptedId);

		String first = scan(volunteer.token(), token, null).andExpect(jsonPath("$.result").value("CHECKED_IN"))
			.andExpect(jsonPath("$.event.general").value(true))
			.andExpect(jsonPath("$.item.id").value(acceptedId))
			.andExpect(jsonPath("$.item.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.item.checkedInBy").value("Scanner"))
			.andExpect(jsonPath("$.item.phone").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String checkedInAt = JsonPath.read(first, "$.item.checkedInAt");
		scan(admin, "http://localhost:5173/ticket?t=" + token, null)
			.andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
			.andExpect(jsonPath("$.item.checkedInAt").value(checkedInAt))
			.andExpect(jsonPath("$.item.checkedInBy").value("Scanner"));
		scan(volunteer.token(), "  https://www.peachhacks.com/ticket?utm=x&t=" + token + "#top ", null)
			.andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"));

		for (String unknown : new String[] { com.peachhacks.backend.common.Tokens.random(), "hello",
				"https://example.com/ticket?t=nope", "https://example.com/menu" }) {
			scan(volunteer.token(), unknown, null).andExpect(jsonPath("$.result").value("NOT_RECOGNISED"))
				.andExpect(jsonPath("$.item").value(nullValue()));
		}
		mockMvc
			.perform(post("/admin/check-in/scan").header("Authorization", volunteer.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.code").isNotEmpty());

		String pendingToken = ticketToken(pendingId);
		scan(volunteer.token(), pendingToken, null).andExpect(jsonPath("$.result").value("NOT_ACCEPTED"))
			.andExpect(jsonPath("$.item.id").value(pendingId))
			.andExpect(jsonPath("$.item.firstName").value("Ada"))
			.andExpect(jsonPath("$.item.status").value("PENDING"))
			.andExpect(jsonPath("$.item.checkedInAt").value(nullValue()));
		assertThat(checkInCount(pendingId)).isZero();
		mockMvc
			.perform(post("/admin/check-in/scan").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"%s\",\"override\":true}".formatted(pendingToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("NOT_ACCEPTED"));
		assertThat(checkInCount(pendingId)).as("there is no override, for an admin either").isZero();

		String event = mockMvc
			.perform(eventRequest(post("/admin/events"), admin, "{\"name\":\"Workshop %s\"}".formatted(unique())))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String eventId = JsonPath.read(event, "$.id");
		mockMvc.perform(delete("/admin/check-in/" + acceptedId).header("Authorization", admin))
			.andExpect(status().isOk());
		scan(volunteer.token(), token, eventId).andExpect(jsonPath("$.result").value("CHECKED_IN"))
			.andExpect(jsonPath("$.event.id").value(eventId))
			.andExpect(jsonPath("$.event.general").value(false))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(false));
		scan(volunteer.token(), token, null).andExpect(jsonPath("$.result").value("CHECKED_IN"));
		scan(volunteer.token(), token, eventId).andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(true));
		scan(volunteer.token(), token, UUID.randomUUID().toString()).andExpect(status().isNotFound());

		mockMvc.perform(delete("/admin/admins/" + volunteer.id()).header("Authorization", admin))
			.andExpect(status().isNoContent());
		deleteRegistrations(acceptedId, pendingId);
		jdbc.sql("delete from events where id = :id").param("id", UUID.fromString(eventId)).update();
	}

	@Test
	void publicTicketExistsOnlyWhileTheRegistrationIsAccepted() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		String registrationId = registerHacker(admin, unique() + "@example.com", school);
		String token = ticketToken(registrationId);

		for (String path : new String[] { "/public/tickets/" + token, "/public/tickets/" + token + "/qr.png",
				"/public/tickets/" + com.peachhacks.backend.common.Tokens.random(),
				"/public/tickets/" + com.peachhacks.backend.common.Tokens.random() + "/qr.png",
				"/public/tickets/short" }) {
			mockMvc.perform(get(path))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Ticket not found."));
		}
		mockMvc.perform(get("/admin/registrations/" + registrationId).header("Authorization", admin))
			.andExpect(jsonPath("$.ticketToken").value(nullValue()))
			.andExpect(jsonPath("$.ticketUrl").value(nullValue()));

		setStatus(admin, registrationId, "ACCEPTED");
		String ticketUrl = "http://localhost:5173/ticket?t=" + token;
		mockMvc.perform(get("/admin/registrations/" + registrationId).header("Authorization", admin))
			.andExpect(jsonPath("$.ticketToken").value(token))
			.andExpect(jsonPath("$.ticketUrl").value(ticketUrl))
			.andExpect(jsonPath("$.googleWalletUrl").value(nullValue()));
		String ticket = mockMvc
			.perform(get("/public/tickets/" + token).header(HttpHeaders.ORIGIN, "https://www.peachhacks.com"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.peachhacks.com"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
			.andExpect(jsonPath("$.firstName").value("Ada"))
			.andExpect(jsonPath("$.lastName").value("Lovelace"))
			.andExpect(jsonPath("$.school").value(school))
			.andExpect(jsonPath("$.checkedIn").value(false))
			.andExpect(jsonPath("$.googleWalletUrl").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Map<String, Object> fields = JsonPath.read(ticket, "$");
		assertThat(fields.keySet()).containsExactlyInAnyOrder("firstName", "lastName", "school", "checkedIn",
				"googleWalletUrl");

		byte[] png = mockMvc.perform(get("/public/tickets/" + token + "/qr.png"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(Arrays.copyOf(png, 8)).containsExactly(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n');
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(image.getWidth()).isEqualTo(image.getHeight()).isGreaterThanOrEqualTo(400);
		assertThat(image.getRGB(0, 0) & 0xFFFFFF).as("the quiet zone is white").isEqualTo(0xFFFFFF);
		int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		String decoded = new QRCodeReader()
			.decode(new BinaryBitmap(
					new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels))))
			.getText();
		assertThat(decoded).isEqualTo(ticketUrl);

		mockMvc.perform(post("/admin/check-in/" + registrationId).header("Authorization", admin))
			.andExpect(status().isOk());
		mockMvc.perform(get("/public/tickets/" + token)).andExpect(jsonPath("$.checkedIn").value(true));

		setStatus(admin, registrationId, "REJECTED");
		mockMvc.perform(get("/public/tickets/" + token)).andExpect(status().isNotFound());
		mockMvc.perform(get("/public/tickets/" + token + "/qr.png")).andExpect(status().isNotFound());
		deleteRegistrations(registrationId);
	}

	@Test
	void theTicketEmailIsSentOnRequestAndNeverByAccepting() throws Exception {
		String admin = bearer();
		String email = unique() + "@example.com";
		String registrationId = registerHacker(admin, email, uniqueSchool());
		String ticketUrl = "http://localhost:5173/ticket?t=" + ticketToken(registrationId);

		mockMvc.perform(post("/admin/registrations/" + registrationId + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		setStatus(admin, registrationId, "WAITLISTED");
		setStatus(admin, registrationId, "ACCEPTED");
		setStatus(admin, registrationId, "ACCEPTED");
		assertThat(ticketEmails(email, 0)).as("accepting does not send the ticket").isEmpty();

		mockMvc.perform(post("/admin/registrations/" + registrationId + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isNoContent());
		List<EmailMessage> sent = ticketEmails(email, 1);
		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.subject()).contains("You're in");
		assertThat(message.text()).contains("Hi Ada,").contains(ticketUrl).doesNotContainIgnoringCase("wallet");
		assertThat(message.html()).contains("cid:peachhacks-ticket").contains("ticket?t=");
		assertThat(message.attachments()).hasSize(1);
		assertThat(message.attachments().get(0).contentType()).isEqualTo("image/png");
		assertThat(Arrays.copyOf(message.attachments().get(0).content(), 4)).containsExactly(0x89, 'P', 'N', 'G');
		mockMvc.perform(get("/admin/registrations/" + registrationId).header("Authorization", admin))
			.andExpect(jsonPath("$.acceptanceNotifiedAt").isNotEmpty());

		mockMvc.perform(post("/admin/registrations/" + registrationId + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isNoContent());
		assertThat(ticketEmails(email, 2)).hasSize(2);
		mockMvc
			.perform(post("/admin/registrations/" + UUID.randomUUID() + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isNotFound());
		deleteRegistrations(registrationId);
	}

	@Test
	void volunteersAreForbiddenFromEverythingButCheckIn() throws Exception {
		String admin = bearer();
		String registrationId = registerHacker(admin, unique() + "@example.com", uniqueSchool());
		setStatus(admin, registrationId, "ACCEPTED");
		Volunteer volunteer = createVolunteer(admin, "Door Volunteer");
		String generalId = jdbc.sql("select id from events where general").query(UUID.class).single().toString();
		String json = "{}";

		MockHttpServletRequestBuilder[] adminOnly = { get("/admin/registrations"),
				get("/admin/registrations/" + registrationId),
				patch("/admin/registrations/" + registrationId).contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"REJECTED\"}"),
				get("/admin/registrations/export.csv"),
				post("/admin/registrations/" + registrationId + "/ticket-email"),
				post("/admin/registrations/status").contentType(MediaType.APPLICATION_JSON)
					.content("{\"ids\":[\"%s\"],\"status\":\"REJECTED\"}".formatted(registrationId)),
				get("/admin/acceptances/summary"), get("/admin/acceptances/waiting"),
				post("/admin/acceptances/send"),
				post("/admin/registrations/" + registrationId + "/school-email/resend"),
				post("/admin/pre-registrations/" + UUID.randomUUID() + "/school-email/resend"),
				get("/admin/registrations/" + registrationId + "/resume"),
				delete("/admin/registrations/" + registrationId + "/resume"), get("/admin/resumes/export.zip"),
				get("/admin/pre-registrations"),
				get("/admin/pre-registrations/export.csv"),
				get("/admin/stats"), get("/admin/settings"),
				put("/admin/settings").contentType(MediaType.APPLICATION_JSON).content("{\"registrationOpen\":true}"),
				get("/admin/emails"), post("/admin/emails").contentType(MediaType.APPLICATION_JSON).content(json),
				post("/admin/emails/test").contentType(MediaType.APPLICATION_JSON).content(json),
				post("/admin/emails/recipient-count").contentType(MediaType.APPLICATION_JSON).content(json),
				get("/admin/emails/" + UUID.randomUUID() + "/recipients"),
				get("/admin/admins"),
				post("/admin/admins").contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"%s@test.local\",\"name\":\"Sneaky\",\"password\":\"another-long-password\"}"
						.formatted(unique())),
				delete("/admin/admins/" + volunteer.id()),
				post("/admin/events").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sneaky\"}"),
				patch("/admin/events/" + generalId).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Sneaky\"}"),
				delete("/admin/events/" + generalId), get("/admin/events/" + generalId + "/export.csv"),
				get("/admin/events/" + generalId), get("/admin/a-route-added-later") };
		for (MockHttpServletRequestBuilder request : adminOnly) {
			mockMvc.perform(request.header("Authorization", volunteer.token()))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.message").isNotEmpty());
		}

		mockMvc.perform(get("/admin/registrations/" + registrationId).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"));
		mockMvc.perform(get("/public/status")).andExpect(jsonPath("$.registrationOpen").value(false));
		mockMvc.perform(get("/admin/events").header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("General check-in"));

		mockMvc.perform(post("/admin/auth/logout").header("Authorization", volunteer.token()))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/admin/admins/" + volunteer.id()).header("Authorization", admin))
			.andExpect(status().isNoContent());
		deleteRegistrations(registrationId);
	}

	@Test
	void accountRoleIsValidatedAndTheLastAdminRuleIgnoresVolunteers() throws Exception {
		String admin = bearer();
		mockMvc
			.perform(post("/admin/admins").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s@test.local\",\"name\":\"Odd\",\"password\":\"another-long-password\",\"role\":\"OWNER\"}"
					.formatted(unique())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.role").isNotEmpty());

		Volunteer volunteer = createVolunteer(admin, "Second Volunteer");
		mockMvc.perform(get("/admin/admins").header("Authorization", admin))
			.andExpect(jsonPath("$[?(@.id == '%s')].role".formatted(volunteer.id())).value("VOLUNTEER"))
			.andExpect(jsonPath("$[?(@.email == '%s')].role".formatted(ADMIN_EMAIL)).value("ADMIN"));

		assertThat(count("select count(*) from admins where role = 'ADMIN'")).isEqualTo(1);
		UUID adminId = jdbc.sql("select id from admins where email = :email")
			.param("email", ADMIN_EMAIL)
			.query(UUID.class)
			.single();
		// No signed-in admin can reach this state over HTTP (they cannot delete themselves), so
		// the rule is exercised directly: one ADMIN plus one VOLUNTEER is still "the last admin".
		AdminPrincipal other = new AdminPrincipal(UUID.fromString(volunteer.id()), "second@test.local",
				"Second Volunteer", AdminRole.VOLUNTEER, "unused");
		assertThatThrownBy(() -> authService.delete(adminId, other)).isInstanceOf(ApiException.class)
			.hasMessage("The last admin account cannot be deleted.");
		assertThat(count("select count(*) from admins where role = 'ADMIN'")).isEqualTo(1);

		mockMvc.perform(delete("/admin/admins/" + volunteer.id()).header("Authorization", admin))
			.andExpect(status().isNoContent());
	}

	@Test
	void announcementAudienceCountsSkipUnsubscribedAndRegistered() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		String registeredEmail = unique() + "@example.com";
		String unsubscribedEmail = unique() + "@example.com";
		preRegister("Grace", "Hopper", unique() + "@example.com", school, "grace@school.edu").andExpect(status().isCreated());
		preRegister("Katherine", "Johnson", registeredEmail, school, "katherine@school.edu").andExpect(status().isCreated());
		preRegister("Dorothy", "Vaughan", unsubscribedEmail, school, "dorothy@school.edu").andExpect(status().isCreated());
		preRegister("Mary", "Jackson", unique() + "@example.com", uniqueSchool(), "mary@school.edu").andExpect(status().isCreated());
		setRegistrationOpen(token, true);
		register(registrationJson(registeredEmail, school, true, true)).andExpect(status().isCreated());
		setRegistrationOpen(token, false);

		recipientCount(token, "ANNOUNCEMENT", "PRE_REGISTRANTS", school)
			.andExpect(jsonPath("$.recipientCount").value(3));

		mockMvc
			.perform(post("/public/unsubscribe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"no-such-token\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		String unsubscribeToken = jdbc.sql("select unsubscribe_token from pre_registrations where email = :email")
			.param("email", unsubscribedEmail)
			.query(String.class)
			.single();
		mockMvc
			.perform(post("/public/unsubscribe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\"}".formatted(unsubscribeToken)))
			.andExpect(status().isNoContent());

		recipientCount(token, "ANNOUNCEMENT", "PRE_REGISTRANTS", school)
			.andExpect(jsonPath("$.recipientCount").value(2));
		recipientCount(token, "ANNOUNCEMENT", "PRE_REGISTRANTS_NOT_REGISTERED", school)
			.andExpect(jsonPath("$.recipientCount").value(1));
		recipientCount(token, "ANNOUNCEMENT", "REGISTRANTS", school).andExpect(jsonPath("$.recipientCount").value(1));
		mockMvc
			.perform(post("/admin/emails/recipient-count").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"ANNOUNCEMENT\",\"audience\":\"PRE_REGISTRANTS\",\"school\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recipientCount").isNumber());
		mockMvc
			.perform(post("/admin/emails/recipient-count").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"audience\":\"PRE_REGISTRANTS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.kind").isNotEmpty());

		mockMvc
			.perform(post("/admin/emails/test").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"subject\":\"Hello {{firstName}}\",\"body\":\"Line one\\n\\nLine two\"}"))
			.andExpect(status().isNoContent());
		String campaign = mockMvc
			.perform(post("/admin/emails").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"kind":"ANNOUNCEMENT","audience":"PRE_REGISTRANTS","school":"%s","subject":"Registration is open","body":"Hi {{firstName}},\\n\\nCome register."}
						""".formatted(school)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.recipientCount").value(2))
			.andExpect(jsonPath("$.kind").value("ANNOUNCEMENT"))
			.andExpect(jsonPath("$.audience").value("PRE_REGISTRANTS"))
			.andExpect(jsonPath("$.createdBy").value(ADMIN_EMAIL))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String campaignId = JsonPath.read(campaign, "$.id");

		String status = null;
		for (int attempt = 0; attempt < 100 && !"SENT".equals(status); attempt++) {
			Thread.sleep(100);
			status = jdbc.sql("select status from email_campaigns where id = :id")
				.param("id", UUID.fromString(campaignId))
				.query(String.class)
				.single();
		}
		assertThat(status).isEqualTo("SENT");
		mockMvc.perform(get("/admin/emails").header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(campaignId))
			.andExpect(jsonPath("$[0].kind").value("ANNOUNCEMENT"))
			.andExpect(jsonPath("$[0].sentCount").value(2))
			.andExpect(jsonPath("$[0].failedCount").value(0))
			.andExpect(jsonPath("$[0].completedAt").isNotEmpty());
	}

	@Test
	void eventUpdatesGoOnlyToAcceptedHackersEvenUnsubscribedOnesAndAnnouncementsSkipThose() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		String staysEmail = unique() + "@example.com";
		String unsubscribedEmail = unique() + "@example.com";
		String preOnlyEmail = unique() + "@example.com";
		preRegister("Mary", "Jackson", preOnlyEmail, school, "mary@school.edu").andExpect(status().isCreated());
		setRegistrationOpen(token, true);
		String stays = registeredId(staysEmail, school);
		String unsubscribed = registeredId(unsubscribedEmail, school);
		setRegistrationOpen(token, false);
		mockMvc
			.perform(post("/public/unsubscribe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\"}".formatted(jdbc
					.sql("select unsubscribe_token from registrations where email = :email")
					.param("email", unsubscribedEmail)
					.query(String.class)
					.single())))
			.andExpect(status().isNoContent());

		tellAccepted(token, stays);
		tellAccepted(token, unsubscribed);

		recipientCount(token, "ANNOUNCEMENT", "REGISTRANTS", school).andExpect(jsonPath("$.recipientCount").value(1));
		recipientCount(token, "ANNOUNCEMENT", "ACCEPTED", school).andExpect(jsonPath("$.recipientCount").value(1));
		recipientCount(token, "EVENT_UPDATE", "ACCEPTED", school).andExpect(jsonPath("$.recipientCount").value(2));
		recipientCount(token, "ANNOUNCEMENT", "PRE_REGISTRANTS", school)
			.andExpect(jsonPath("$.recipientCount").value(1));

		for (String audience : List.of("PRE_REGISTRANTS", "PRE_REGISTRANTS_NOT_REGISTERED", "REGISTRANTS")) {
			String json = """
					{"kind":"EVENT_UPDATE","audience":"%s","school":"%s","subject":"Doors open at 9","body":"Bring a laptop."}
					""".formatted(audience, school);
			for (String path : List.of("/admin/emails/recipient-count", "/admin/emails")) {
				mockMvc
					.perform(post(path).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.fieldErrors.audience").value(containsString("accepted hackers")));
			}
		}
		mockMvc
			.perform(post("/admin/emails").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"audience\":\"REGISTRANTS\",\"subject\":\"Doors open at 9\",\"body\":\"Bring a laptop.\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.kind").isNotEmpty());

		sendCampaign(token, "EVENT_UPDATE", "ACCEPTED", school, "Doors open at 9", 2);
		for (String email : List.of(staysEmail, unsubscribedEmail)) {
			EmailMessage update = emailsTo(email, "Doors open at 9", 1).get(0);
			assertThat(update.headers()).doesNotContainKey("List-Unsubscribe");
			assertThat(update.html()).doesNotContainIgnoringCase("unsubscribe");
			assertThat(update.text()).doesNotContainIgnoringCase("unsubscribe")
				.contains("You are receiving this because you registered for PeachHacks.");
		}
		assertThat(emailsTo(preOnlyEmail, "Doors open at 9", 0)).isEmpty();

		sendCampaign(token, "ANNOUNCEMENT", "REGISTRANTS", school, "Sponsor news", 1);
		EmailMessage announcement = emailsTo(staysEmail, "Sponsor news", 1).get(0);
		assertThat(announcement.headers().get("List-Unsubscribe")).contains("/unsubscribe.html?token=");
		assertThat(announcement.html()).contains(">Unsubscribe</a>");
		assertThat(announcement.text()).contains("Unsubscribe: ");
		assertThat(emailsTo(unsubscribedEmail, "Sponsor news", 0)).isEmpty();

		mockMvc
			.perform(post("/admin/emails/test").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"EVENT_UPDATE\",\"subject\":\"Footer check update\",\"body\":\"Hello\"}"))
			.andExpect(status().isNoContent());
		mockMvc
			.perform(post("/admin/emails/test").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"ANNOUNCEMENT\",\"subject\":\"Footer check news\",\"body\":\"Hello\"}"))
			.andExpect(status().isNoContent());
		assertThat(emailsTo(ADMIN_EMAIL, "Footer check update", 1).get(0).text())
			.doesNotContainIgnoringCase("unsubscribe")
			.contains("because you registered for PeachHacks.");
		assertThat(emailsTo(ADMIN_EMAIL, "Footer check news", 1).get(0).text()).contains("Unsubscribe: ");

		mockMvc
			.perform(post("/admin/emails/preview").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"EVENT_UPDATE\",\"subject\":\"Preview only {{firstName}}\",\"body\":\"<b>Hello</b> https://www.peachhacks.com\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subject").value("Preview only Test"))
			.andExpect(jsonPath("$.html", containsString("&lt;b&gt;Hello&lt;/b&gt; <a ")))
			.andExpect(jsonPath("$.html", containsString("/assets/email-logo.png")))
			.andExpect(jsonPath("$.html", not(containsString("nsubscribe"))))
			.andExpect(jsonPath("$.text", containsString("because you registered for PeachHacks.")));
		mockMvc
			.perform(post("/admin/emails/preview").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"ANNOUNCEMENT\",\"subject\":\"Preview only\",\"body\":\"Hello\"}"))
			.andExpect(jsonPath("$.html", containsString(">Unsubscribe</a>")));
		assertThat(emailsTo(ADMIN_EMAIL, "Preview only", 0)).as("a preview sends nothing").isEmpty();

		mockMvc.perform(get("/admin/emails").header("Authorization", token))
			.andExpect(jsonPath("$[0].kind").value("ANNOUNCEMENT"))
			.andExpect(jsonPath("$[0].subject").value("Sponsor news"))
			.andExpect(jsonPath("$[1].kind").value("EVENT_UPDATE"));
		deleteRegistrations(stays, unsubscribed);
	}

	@Test
	void theAcceptedAudienceIsOnlyPeopleWhoHaveBeenToldTheyAreAccepted() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		String toldEmail = unique() + "@example.com";
		String waitingEmail = unique() + "@example.com";
		String pendingEmail = unique() + "@example.com";
		setRegistrationOpen(token, true);
		String told = registeredId(toldEmail, school);
		String waiting = registeredId(waitingEmail, school);
		String pending = registeredId(pendingEmail, school);
		setRegistrationOpen(token, false);
		setStatus(token, told, "ACCEPTED");
		setStatus(token, waiting, "ACCEPTED");
		mockMvc.perform(post("/admin/registrations/" + told + "/ticket-email").header("Authorization", token))
			.andExpect(status().isNoContent());

		recipientCount(token, "EVENT_UPDATE", "ACCEPTED", school).andExpect(jsonPath("$.recipientCount").value(1));
		recipientCount(token, "ANNOUNCEMENT", "ACCEPTED", school).andExpect(jsonPath("$.recipientCount").value(1));
		recipientCount(token, "ANNOUNCEMENT", "REGISTRANTS", school).andExpect(jsonPath("$.recipientCount").value(3));

		sendCampaign(token, "EVENT_UPDATE", "ACCEPTED", school, "Where to park", 1);
		assertThat(emailsTo(toldEmail, "Where to park", 1)).hasSize(1);
		assertThat(emailsTo(waitingEmail, "Where to park", 0)).as("accepted but not told yet").isEmpty();
		assertThat(emailsTo(pendingEmail, "Where to park", 0)).isEmpty();

		deleteRegistrations(told, waiting, pending);
	}

	@Test
	void campaignsSentBeforeTheKindExistedBecomeAnnouncements() {
		String schema = "v7_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Flyway.configure().dataSource(dataSource).schemas(schema).target("6").load().migrate();
		jdbc.sql("""
				insert into %s.email_campaigns (id, subject, body, audience, created_by)
				values (gen_random_uuid(), 'Old', 'Body', 'REGISTRANTS', 'someone@peachhacks.com')
				""".formatted(schema)).update();

		Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();

		assertThat(jdbc.sql("select kind from %s.email_campaigns".formatted(schema)).query(String.class).list())
			.containsExactly("ANNOUNCEMENT");
		jdbc.sql("drop schema %s cascade".formatted(schema)).update();
	}

	@Test
	void registrationsAndPreRegistrationsCannotBeDeletedThroughTheApi() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		String email = unique() + "@example.com";
		String created = preRegister("Ada", "Lovelace", email, school, "ada@school.edu")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String preRegistrationId = JsonPath.read(created, "$.id");
		setRegistrationOpen(token, true);
		String registrationId = registeredId(email, school);
		setRegistrationOpen(token, false);

		mockMvc.perform(delete("/admin/registrations/" + registrationId).header("Authorization", token))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mockMvc.perform(delete("/admin/pre-registrations/" + preRegistrationId).header("Authorization", token))
			.andExpect(status().is4xxClientError());

		mockMvc.perform(get("/admin/registrations/" + registrationId).header("Authorization", token))
			.andExpect(status().isOk());
		mockMvc.perform(get("/admin/pre-registrations").param("school", school).header("Authorization", token))
			.andExpect(jsonPath("$.total").value(1));

		deleteRegistrations(registrationId);
		jdbc.sql("delete from pre_registrations where id = :id")
			.param("id", UUID.fromString(preRegistrationId))
			.update();
	}

	@Test
	void corsPreflightIsAnsweredWithoutAuthentication() throws Exception {
		mockMvc
			.perform(options("/admin/stats").header(HttpHeaders.ORIGIN, "http://localhost:5174")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")));
		mockMvc
			.perform(options("/admin/stats").header(HttpHeaders.ORIGIN, "https://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		mockMvc.perform(get("/public/status").header(HttpHeaders.ORIGIN, "https://www.peachhacks.com"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.peachhacks.com"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void resumeRoundTripsThroughTheAdminDownloadAndCanBeRemoved() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		byte[] pdf = pdfBytes(40_000);
		setRegistrationOpen(admin, true);
		String created = register(withResume(registrationJson(unique() + "@example.com", school, true, true),
				"..\\\\..\\\\secret/My R\\u00E9\\u0000\\u202Esum\\u00E9: final?.PDF", pdf, true))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		setRegistrationOpen(admin, false);
		String id = JsonPath.read(created, "$.id");

		mockMvc.perform(get("/admin/registrations/" + id).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resume.fileName").value("My Résumé_ final_.pdf"))
			.andExpect(jsonPath("$.resume.size").value(pdf.length))
			.andExpect(jsonPath("$.resume.uploadedAt").isNotEmpty())
			.andExpect(jsonPath("$.resume.content").doesNotExist())
			.andExpect(jsonPath("$.resumeOptIn").value(true));
		byte[] downloaded = mockMvc.perform(get("/admin/registrations/" + id + "/resume").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(downloaded).isEqualTo(pdf);

		for (String filter : List.of("opted-in", "any")) {
			mockMvc
				.perform(get("/admin/registrations").param("school", school)
					.param("resume", filter)
					.header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].hasResume").value(true))
				.andExpect(jsonPath("$.items[0].resumeOptIn").value(true));
		}
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("resume", "none")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(0));
		mockMvc.perform(get("/admin/registrations").param("resume", "sometimes").header("Authorization", admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.resume").isNotEmpty());
		mockMvc
			.perform(get("/admin/registrations/export.csv").param("school", school).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(",true,true,ada.lovelace@school.edu,false,false\r\n")));
		mockMvc.perform(get("/admin/stats").header("Authorization", admin))
			.andExpect(jsonPath("$.registrations.withResume")
				.value((int) count("select count(*) from registration_resumes")))
			.andExpect(jsonPath("$.registrations.resumeOptIn")
				.value((int) count("select count(*) from registration_resumes where sponsor_opt_in")));

		mockMvc.perform(delete("/admin/registrations/" + id + "/resume").header("Authorization", admin))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/admin/registrations/" + id + "/resume").header("Authorization", admin))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(delete("/admin/registrations/" + id + "/resume").header("Authorization", admin))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/admin/registrations/" + id).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resume").value(nullValue()))
			.andExpect(jsonPath("$.resumeOptIn").value(false));
		mockMvc.perform(get("/admin/registrations").param("school", school).header("Authorization", admin))
			.andExpect(jsonPath("$.items[0].hasResume").value(false))
			.andExpect(jsonPath("$.items[0].resumeOptIn").value(false));
		deleteRegistrations(id);
	}

	@Test
	void invalidResumesAreRejectedWithoutCreatingARegistration() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		String json = registrationJson(unique() + "@example.com", school, true, true);
		byte[] tooLarge = pdfBytes(2 * 1024 * 1024 + 1);
		byte[] largerThanTheBodyLimit = pdfBytes(2_500_000);
		setRegistrationOpen(admin, true);

		register(withResume(json, "notes.pdf", "Plain text, not a PDF".getBytes(StandardCharsets.UTF_8), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.resume").value("Resume must be a PDF file"));
		for (byte[] oversized : List.of(tooLarge, largerThanTheBodyLimit)) {
			register(withResume(json, "resume.pdf", oversized, false)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.resume").value("Resume must be 2 MB or smaller"));
		}
		register(json.replace("\"website\": \"\"",
				"\"website\": \"\", \"resume\": {\"fileName\": \"resume.pdf\", \"contentBase64\": \"not base64!\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.resume").isNotEmpty());
		register(json.replace("\"website\": \"\"",
				"\"website\": \"\", \"resume\": {\"fileName\": \"resume.pdf\", \"contentBase64\": \"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.resume").isNotEmpty());

		assertThat(jdbc.sql("select count(*) from registrations where school = :school")
			.param("school", school)
			.query(Long.class)
			.single()).isZero();

		// The largest file allowed is accepted, so the limits above are not off by one.
		String created = register(withResume(json, "resume.pdf", pdfBytes(2 * 1024 * 1024), false))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		setRegistrationOpen(admin, false);
		deleteRegistrations(JsonPath.<String>read(created, "$.id"));
	}

	@Test
	void resumeOptInWithoutAFileIsStoredAsFalse() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		setRegistrationOpen(admin, true);
		String created = register(registrationJson(unique() + "@example.com", school, true, true)
			.replace("\"website\": \"\"", "\"website\": \"\", \"resume\": null, \"resumeOptIn\": true"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		setRegistrationOpen(admin, false);
		String id = JsonPath.read(created, "$.id");

		mockMvc.perform(get("/admin/registrations/" + id).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resume").value(nullValue()))
			.andExpect(jsonPath("$.resumeOptIn").value(false));
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("resume", "none")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].hasResume").value(false))
			.andExpect(jsonPath("$.items[0].resumeOptIn").value(false));
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("resume", "opted-in")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(0));
		mockMvc.perform(get("/admin/registrations/" + id + "/resume").header("Authorization", admin))
			.andExpect(status().isNotFound());
		deleteRegistrations(id);
	}

	@Test
	void resumeBookHoldsExactlyTheOptedInAcceptedResumes() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		byte[] sharedPdf = pdfBytes(3_000);
		byte[] attendedPdf = pdfBytes(5_000);
		String sharedEmail = unique() + "@example.com";
		setRegistrationOpen(admin, true);
		String shared = registerWithResume(named(registrationJson(sharedEmail, school, true, true), "Zoë", "O'Brien Smith")
			.replace("\"linkedinUrl\": \"\"",
					"\"linkedinUrl\": \"https://www.linkedin.com/in/zoe\", \"majorFieldOfStudy\": \"Computer science, computer engineering, or software engineering\""),
				sharedPdf, true);
		String attended = registerWithResume(
				named(registrationJson(unique() + "@example.com", school, true, true), "Grace", "Hopper"), attendedPdf,
				true);
		String pending = registerWithResume(
				named(registrationJson(unique() + "@example.com", school, true, true), "Pending", "Person"),
				pdfBytes(1_000), true);
		String notOptedIn = registerWithResume(
				named(registrationJson(unique() + "@example.com", school, true, true), "Private", "Person"),
				pdfBytes(1_000), false);
		String noResume = JsonPath.read(register(registrationJson(unique() + "@example.com", school, true, true))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
		setRegistrationOpen(admin, false);
		for (String id : List.of(shared, attended, notOptedIn, noResume)) {
			setStatus(admin, id, "ACCEPTED");
		}
		mockMvc.perform(post("/admin/check-in/" + attended).header("Authorization", admin)).andExpect(status().isOk());

		String sharedName = "OBrien-Smith_Zoe_" + shared.replace("-", "").substring(0, 8) + ".pdf";
		String attendedName = "Hopper_Grace_" + attended.replace("-", "").substring(0, 8) + ".pdf";

		Map<String, byte[]> book = unzip(mockMvc.perform(get("/admin/resumes/export.zip").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("peachhacks-resume-book-")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray());
		assertThat(book.keySet()).containsExactly(attendedName, sharedName, "index.csv");
		assertThat(book.get(sharedName)).isEqualTo(sharedPdf);
		assertThat(book.get(attendedName)).isEqualTo(attendedPdf);
		String index = new String(book.get("index.csv"), StandardCharsets.UTF_8);
		assertThat(index.split("\r\n")).hasSize(3);
		assertThat(index).startsWith("first_name,last_name,email,school_email,school,level_of_study,major,linkedin_url,file_name\r\n")
			.contains("Zoë,O'Brien Smith," + sharedEmail + ",ada.lovelace@school.edu," + school
					+ ",Undergraduate University (3+ year),\"Computer science, computer engineering, or software engineering\","
					+ "https://www.linkedin.com/in/zoe," + sharedName + "\r\n")
			.contains("," + attendedName + "\r\n");

		Map<String, byte[]> attendedOnly = unzip(mockMvc
			.perform(get("/admin/resumes/export.zip").param("checkedIn", "true").header("Authorization", admin))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray());
		assertThat(attendedOnly.keySet()).containsExactly(attendedName, "index.csv");

		mockMvc
			.perform(get("/admin/registrations").param("resume", "opted-in")
				.param("status", "ACCEPTED")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(2));

		deleteRegistrations(shared, attended, pending, notOptedIn, noResume);
		assertThat(count("select count(*) from registration_resumes")).isZero();
	}

	@Test
	void schoolEmailIsConfirmedByALinkSentToTheSchoolAddress() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		String email = unique() + "@example.com";
		String schoolEmail = unique() + "@school.edu";

		preRegister("Ada", "Lovelace", email, school, schoolEmail.toUpperCase()).andExpect(status().isCreated());

		List<EmailMessage> links = confirmationEmails(schoolEmail, 1);
		assertThat(links).hasSize(1);
		assertThat(links.get(0).subject()).isEqualTo("Confirm your school email for PeachHacks");
		assertThat(links.get(0).text()).contains("Hi Ada,").contains("http://localhost:5173/confirm-email?token=");
		assertThat(links.get(0).text()).contains("with the personal email " + email.charAt(0) + "***@example.com")
			.doesNotContain(email);
		assertThat(links.get(0).html()).contains("confirm-email?token=")
			.contains(">Confirm your school email</a>")
			.doesNotContain(email);
		assertThat(links.get(0).headers()).isEmpty();
		assertThat(confirmationEmails(email, 0)).as("nothing to confirm is sent to the personal address").isEmpty();
		assertThat(emailsTo(email, "You're pre-registered", 1).get(0).text())
			.contains("look in your school inbox (" + schoolEmail + ")");
		String token = confirmationToken(links.get(0));
		assertThat(count("select count(*) from school_email_tokens where token_hash = '" + token + "'"))
			.as("the token is stored hashed")
			.isZero();
		mockMvc.perform(get("/admin/pre-registrations").param("school", school).header("Authorization", admin))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmed").value(false))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmedAt").value(nullValue()));
		long confirmedBefore = JsonPath.parse(mockMvc.perform(get("/admin/stats").header("Authorization", admin))
			.andReturn()
			.getResponse()
			.getContentAsString()).read("$.preRegistrations.schoolEmailConfirmed", Long.class);

		mockMvc.perform(get("/public/school-email/confirm").param("token", token))
			.andExpect(status().is4xxClientError());
		mockMvc.perform(get("/admin/pre-registrations").param("school", school).header("Authorization", admin))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmed").value(false));
		confirm("no-such-token").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		confirm("").andExpect(status().isNotFound());
		mockMvc.perform(post("/public/school-email/confirm").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		confirm(token).andExpect(status().isOk()).andExpect(jsonPath("$.schoolEmail").value(schoolEmail));
		String confirmedAt = JsonPath.read(mockMvc
			.perform(get("/admin/pre-registrations").param("school", school).header("Authorization", admin))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmed").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.items[0].schoolEmailConfirmedAt");
		assertThat(confirmedAt).isNotEmpty();
		confirm(token).andExpect(status().isOk()).andExpect(jsonPath("$.schoolEmail").value(schoolEmail));
		mockMvc.perform(get("/admin/pre-registrations").param("school", school).header("Authorization", admin))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmedAt").value(confirmedAt));

		mockMvc
			.perform(get("/admin/pre-registrations").param("school", school)
				.param("schoolEmailConfirmed", "true")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(1));
		mockMvc
			.perform(get("/admin/pre-registrations").param("school", school)
				.param("schoolEmailConfirmed", "false")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(0));
		mockMvc
			.perform(get("/admin/pre-registrations/export.csv").param("school", school)
				.header("Authorization", admin))
			.andExpect(content().string(containsString("registered,createdAt,school_email_confirmed\r\n")))
			.andExpect(content().string(containsString(",true\r\n")));
		mockMvc
			.perform(get("/admin/pre-registrations/export.csv").param("school", school)
				.param("schoolEmailConfirmed", "false")
				.header("Authorization", admin))
			.andExpect(content().string(not(containsString(email))));
		mockMvc.perform(get("/admin/stats").header("Authorization", admin))
			.andExpect(jsonPath("$.preRegistrations.schoolEmailConfirmed").value(confirmedBefore + 1));
	}

	@Test
	void schoolEmailConfirmationCarriesToARegistrationWithTheSamePairOnly() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		String sameEmail = unique() + "@example.com";
		String sameSchoolEmail = unique() + "@school.edu";
		String changedEmail = unique() + "@example.com";
		String firstSchoolEmail = unique() + "@school.edu";
		String otherSchoolEmail = unique() + "@school.edu";
		preRegister("Ada", "Lovelace", sameEmail, school, sameSchoolEmail).andExpect(status().isCreated());
		preRegister("Grace", "Hopper", changedEmail, school, firstSchoolEmail).andExpect(status().isCreated());
		confirm(confirmationToken(confirmationEmails(sameSchoolEmail, 1).get(0))).andExpect(status().isOk());
		confirm(confirmationToken(confirmationEmails(firstSchoolEmail, 1).get(0))).andExpect(status().isOk());

		setRegistrationOpen(admin, true);
		String same = JsonPath.read(
				register(withSchoolEmail(registrationJson(sameEmail, school, true, true), sameSchoolEmail.toUpperCase()))
					.andExpect(status().isCreated())
					.andReturn()
					.getResponse()
					.getContentAsString(),
				"$.id");
		String changed = JsonPath.read(
				register(withSchoolEmail(registrationJson(changedEmail, school, true, true), otherSchoolEmail))
					.andExpect(status().isCreated())
					.andReturn()
					.getResponse()
					.getContentAsString(),
				"$.id");
		setRegistrationOpen(admin, false);

		assertThat(confirmationEmails(otherSchoolEmail, 1)).hasSize(1);
		assertThat(confirmationEmails(sameSchoolEmail, 1)).as("an already confirmed pair is not asked again").hasSize(1);
		assertThat(emailsTo(sameEmail, "We received your PeachHacks registration", 1).get(0).text())
			.doesNotContain("school inbox");
		assertThat(emailsTo(changedEmail, "We received your PeachHacks registration", 1).get(0).text())
			.contains("look in your school inbox (" + otherSchoolEmail + ")");
		mockMvc.perform(get("/admin/registrations/" + same).header("Authorization", admin))
			.andExpect(jsonPath("$.schoolEmailConfirmed").value(true))
			.andExpect(jsonPath("$.schoolEmailConfirmedAt").isNotEmpty());
		mockMvc.perform(get("/admin/registrations/" + changed).header("Authorization", admin))
			.andExpect(jsonPath("$.schoolEmail").value(otherSchoolEmail))
			.andExpect(jsonPath("$.schoolEmailConfirmed").value(false))
			.andExpect(jsonPath("$.schoolEmailConfirmedAt").value(nullValue()));
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("schoolEmailConfirmed", "true")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(same))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmed").value(true))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmedAt").isNotEmpty());
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("schoolEmailConfirmed", "false")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(changed))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmed").value(false));
		mockMvc.perform(get("/admin/registrations").param("school", school).header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(2));
		mockMvc
			.perform(get("/admin/registrations/export.csv").param("school", school)
				.param("schoolEmailConfirmed", "true")
				.header("Authorization", admin))
			.andExpect(content().string(containsString("resume_opt_in,school_email,school_email_confirmed,age_review\r\n")))
			.andExpect(content().string(containsString("," + sameSchoolEmail + ",true,false\r\n")))
			.andExpect(content().string(not(containsString(otherSchoolEmail))));
		mockMvc.perform(get("/admin/stats").header("Authorization", admin))
			.andExpect(jsonPath("$.registrations.schoolEmailConfirmed").isNumber());

		// Acceptance is the organizers' call: an unconfirmed school email does not block it.
		setStatus(admin, changed, "ACCEPTED");

		deleteRegistrations(same, changed);
	}

	@Test
	void expiredSchoolEmailLinksAreRejected() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		String schoolEmail = unique() + "@school.edu";
		preRegister("Ada", "Lovelace", unique() + "@example.com", school, schoolEmail).andExpect(status().isCreated());
		String token = confirmationToken(confirmationEmails(schoolEmail, 1).get(0));
		// A calendar day is 23 or 25 hours across a clock change, hence the hour either side.
		assertThat(jdbc.sql("""
				select expires_at - created_at between interval '335 hours' and interval '337 hours'
				from school_email_tokens where token_hash = :hash
				""").param("hash", Tokens.sha256(token)).query(Boolean.class).single()).isTrue();

		jdbc.sql("update school_email_tokens set expires_at = now() - interval '1 second' where token_hash = :hash")
			.param("hash", Tokens.sha256(token))
			.update();

		confirm(token).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/admin/pre-registrations").param("school", school).header("Authorization", admin))
			.andExpect(jsonPath("$.items[0].schoolEmailConfirmed").value(false));
	}

	@Test
	void schoolEmailLinksAreLimitedPerPairAndResendRevealsNothing() throws Exception {
		String school = uniqueSchool();
		String email = unique() + "@example.com";
		String schoolEmail = unique() + "@school.edu";
		preRegister("Ada", "Lovelace", email, school, schoolEmail).andExpect(status().isCreated());
		assertThat(confirmationEmails(schoolEmail, 1)).hasSize(1);

		preRegister("Ada", "Lovelace", email, school, schoolEmail).andExpect(status().isCreated());
		resend(email).andExpect(status().isNoContent()).andExpect(content().string(""));
		resend(unique() + "@example.com").andExpect(status().isNoContent()).andExpect(content().string(""));
		resend("").andExpect(status().isNoContent());
		mockMvc.perform(post("/public/school-email/resend").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isNoContent());
		assertThat(confirmationEmails(schoolEmail, 2)).as("one link per pair inside the interval").hasSize(1);

		jdbc.sql("update school_email_confirmations set last_sent_at = now() - interval '9 minutes' where school_email = :schoolEmail")
			.param("schoolEmail", schoolEmail)
			.update();
		resend(email).andExpect(status().isNoContent());
		assertThat(confirmationEmails(schoolEmail, 2)).hasSize(1);

		jdbc.sql("update school_email_confirmations set last_sent_at = now() - interval '11 minutes' where school_email = :schoolEmail")
			.param("schoolEmail", schoolEmail)
			.update();
		resend(email.toUpperCase()).andExpect(status().isNoContent());
		List<EmailMessage> links = confirmationEmails(schoolEmail, 2);
		assertThat(links).hasSize(2);
		assertThat(confirmationToken(links.get(1))).isNotEqualTo(confirmationToken(links.get(0)));

		confirm(confirmationToken(links.get(1))).andExpect(status().isOk());
		// The earlier link was not the one clicked, but it has not expired either.
		confirm(confirmationToken(links.get(0))).andExpect(status().isOk())
			.andExpect(jsonPath("$.schoolEmail").value(schoolEmail));
		jdbc.sql("update school_email_confirmations set last_sent_at = null where school_email = :schoolEmail")
			.param("schoolEmail", schoolEmail)
			.update();
		resend(email).andExpect(status().isNoContent());
		assertThat(confirmationEmails(schoolEmail, 3)).as("a confirmed address is not mailed again").hasSize(2);
	}

	@Test
	void adminsCanResendTheSchoolEmailLinkUntilItIsConfirmed() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		String schoolEmail = unique() + "@school.edu";
		String preSchoolEmail = unique() + "@school.edu";
		setRegistrationOpen(admin, true);
		String registrationId = JsonPath.read(
				register(withSchoolEmail(registrationJson(unique() + "@example.com", school, true, true), schoolEmail))
					.andExpect(status().isCreated())
					.andReturn()
					.getResponse()
					.getContentAsString(),
				"$.id");
		setRegistrationOpen(admin, false);
		String preRegistrationId = JsonPath.read(
				preRegister("Grace", "Hopper", unique() + "@example.com", school, preSchoolEmail)
					.andExpect(status().isCreated())
					.andReturn()
					.getResponse()
					.getContentAsString(),
				"$.id");
		assertThat(confirmationEmails(schoolEmail, 1)).hasSize(1);
		assertThat(confirmationEmails(preSchoolEmail, 1)).hasSize(1);

		String registrationResend = "/admin/registrations/" + registrationId + "/school-email/resend";
		String preRegistrationResend = "/admin/pre-registrations/" + preRegistrationId + "/school-email/resend";
		mockMvc.perform(post(registrationResend).header("Authorization", admin))
			.andExpect(status().isNoContent());
		mockMvc.perform(post(preRegistrationResend).header("Authorization", admin))
			.andExpect(status().isNoContent());
		List<EmailMessage> links = confirmationEmails(schoolEmail, 2);
		assertThat(links).as("an admin resend ignores the interval").hasSize(2);
		assertThat(links.get(1).text()).contains("Hi Ada,");
		List<EmailMessage> preLinks = confirmationEmails(preSchoolEmail, 2);
		assertThat(preLinks).hasSize(2);
		assertThat(preLinks.get(1).text()).contains("Hi Grace,");

		mockMvc.perform(post("/admin/registrations/" + UUID.randomUUID() + "/school-email/resend")
			.header("Authorization", admin)).andExpect(status().isNotFound());
		mockMvc.perform(post("/admin/pre-registrations/" + UUID.randomUUID() + "/school-email/resend")
			.header("Authorization", admin)).andExpect(status().isNotFound());
		mockMvc.perform(post(registrationResend)).andExpect(status().isUnauthorized());

		confirm(confirmationToken(links.get(1))).andExpect(status().isOk());
		confirm(confirmationToken(preLinks.get(0))).andExpect(status().isOk());
		mockMvc.perform(post(registrationResend).header("Authorization", admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.message").value("This school email is already confirmed."));
		mockMvc.perform(post(preRegistrationResend).header("Authorization", admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertThat(confirmationEmails(schoolEmail, 3)).hasSize(2);

		deleteRegistrations(registrationId);
		jdbc.sql("delete from pre_registrations where id = :id")
			.param("id", UUID.fromString(preRegistrationId))
			.update();
	}

	@Test
	void registrationsFromBeforeSchoolEmailsAreSimplyUnconfirmed() throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		String registrationId = registerHacker(admin, unique() + "@example.com", school);
		jdbc.sql("update registrations set school_email = null where id = :id")
			.param("id", UUID.fromString(registrationId))
			.update();

		mockMvc.perform(get("/admin/registrations/" + registrationId).header("Authorization", admin))
			.andExpect(jsonPath("$.schoolEmail").value(nullValue()))
			.andExpect(jsonPath("$.schoolEmailConfirmed").value(false));
		mockMvc
			.perform(get("/admin/registrations").param("school", school)
				.param("schoolEmailConfirmed", "false")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(1));
		mockMvc
			.perform(post("/admin/registrations/" + registrationId + "/school-email/resend").header("Authorization",
					admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		deleteRegistrations(registrationId);
	}

	private ResultActions confirm(String token) throws Exception {
		return mockMvc.perform(post("/public/school-email/confirm").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\"}".formatted(token)));
	}

	private ResultActions resend(String email) throws Exception {
		return mockMvc.perform(post("/public/school-email/resend").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\"}".formatted(email)));
	}

	private List<EmailMessage> confirmationEmails(String to, int expected) throws InterruptedException {
		return emailsTo(to, "Confirm your school email", expected);
	}

	/**
	 * Mail is sent on a background thread. Waits for the expected number; asking for one
	 * more than should exist, or for none, waits a moment and so shows that no extra arrived.
	 */
	private List<EmailMessage> emailsTo(String to, String subjectStart, int expected) throws InterruptedException {
		List<EmailMessage> matching = List.of();
		for (int attempt = 0; attempt < 15; attempt++) {
			matching = sentEmails.stream()
				.filter(message -> message.to().equals(to) && message.subject().startsWith(subjectStart))
				.toList();
			if (matching.size() >= expected && expected > 0) {
				break;
			}
			Thread.sleep(expected > 0 ? 100 : 20);
		}
		return matching;
	}

	private static String confirmationToken(EmailMessage message) {
		Matcher matcher = CONFIRM_LINK.matcher(message.text());
		assertThat(matcher.find()).as("confirmation link in %s", message.text()).isTrue();
		return matcher.group(1);
	}

	private static String withSchoolEmail(String json, String schoolEmail) {
		return json.replace("Ada.Lovelace@School.EDU", schoolEmail);
	}

	private ResultActions preRegister(String firstName, String lastName, String email, String school,
			String schoolEmail) throws Exception {
		return mockMvc.perform(post("/public/pre-registrations").contentType(MediaType.APPLICATION_JSON).content("""
				{"firstName":"%s","lastName":"%s","email":"%s","school":"%s","schoolEmail":"%s","website":""}
				""".formatted(firstName, lastName, email, school, schoolEmail)));
	}

	private ResultActions register(String json) throws Exception {
		return mockMvc.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	@Test
	void eventUpdatesNeverReachRejectedWaitlistedPendingOrNotYetToldRegistrants() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		String acceptedEmail = unique() + "@example.com";
		List<String> others = List.of(unique() + "@example.com", unique() + "@example.com",
				unique() + "@example.com", unique() + "@example.com");
		setRegistrationOpen(token, true);
		String accepted = registeredId(acceptedEmail, school);
		String notTold = registeredId(others.get(0), school);
		String pending = registeredId(others.get(1), school);
		String waitlisted = registeredId(others.get(2), school);
		String rejected = registeredId(others.get(3), school);
		setRegistrationOpen(token, false);
		tellAccepted(token, accepted);
		setStatus(token, notTold, "ACCEPTED");
		setStatus(token, waitlisted, "WAITLISTED");
		setStatus(token, rejected, "REJECTED");

		recipientCount(token, "EVENT_UPDATE", "ACCEPTED", school).andExpect(jsonPath("$.recipientCount").value(1));
		startCampaign(token, "EVENT_UPDATE", "REGISTRANTS", school, "Parking map").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.audience").isNotEmpty());
		sendCampaign(token, "EVENT_UPDATE", "ACCEPTED", school, "Parking map", 1);

		assertThat(emailsTo(acceptedEmail, "Parking map", 1)).hasSize(1);
		for (String email : others) {
			assertThat(emailsTo(email, "Parking map", 0)).as(email).isEmpty();
		}
		assertThat(sentEmails.stream().filter(message -> message.subject().startsWith("Parking map")).toList())
			.hasSize(1);
		deleteRegistrations(accepted, notTold, pending, waitlisted, rejected);
	}

	@Test
	void campaignRecipientsAreRecordedWithWhatHappenedToEach() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		String reachedEmail = unique() + "@example.com";
		String refusedEmail = unique() + "@example.com";
		preRegister("Grace", "Hopper", reachedEmail, school, "grace@school.edu").andExpect(status().isCreated());
		preRegister("Dorothy", "Vaughan", refusedEmail, school, "dorothy@school.edu").andExpect(status().isCreated());
		String subject = "Recorded " + unique();

		rejectedByProvider.add(refusedEmail);
		String campaign = startCampaign(token, "ANNOUNCEMENT", "PRE_REGISTRANTS", school, subject)
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.recipientCount").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(campaign, "$.id");
		awaitCampaign(UUID.fromString(id), "SENT");
		rejectedByProvider.remove(refusedEmail);

		String recipients = "/admin/emails/" + id + "/recipients";
		mockMvc.perform(get(recipients).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(25))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].email").value(reachedEmail))
			.andExpect(jsonPath("$.items[0].firstName").value("Grace"))
			.andExpect(jsonPath("$.items[0].lastName").value("Hopper"))
			.andExpect(jsonPath("$.items[0].status").value("SENT"))
			.andExpect(jsonPath("$.items[0].sentAt").isNotEmpty())
			.andExpect(jsonPath("$.items[0].unsubscribeToken").doesNotExist())
			.andExpect(jsonPath("$.items[1].email").value(refusedEmail))
			.andExpect(jsonPath("$.items[1].status").value("FAILED"))
			.andExpect(jsonPath("$.items[1].sentAt").value(nullValue()));
		mockMvc.perform(get(recipients).param("status", "FAILED").header("Authorization", token))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].email").value(refusedEmail));
		mockMvc.perform(get(recipients).param("status", "SENT").param("size", "1").header("Authorization", token))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.items[0].email").value(reachedEmail));
		mockMvc.perform(get(recipients).param("size", "1").param("page", "1").header("Authorization", token))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.items[0].email").value(refusedEmail));
		mockMvc.perform(get(recipients).param("status", "PENDING").header("Authorization", token))
			.andExpect(jsonPath("$.total").value(0))
			.andExpect(jsonPath("$.items", hasSize(0)));
		mockMvc.perform(get(recipients).param("status", "").header("Authorization", token))
			.andExpect(jsonPath("$.total").value(2));
		mockMvc.perform(get(recipients).param("status", "BOUNCED").header("Authorization", token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.status").isNotEmpty());
		mockMvc.perform(get("/admin/emails/" + UUID.randomUUID() + "/recipients").header("Authorization", token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get(recipients)).andExpect(status().isUnauthorized());

		mockMvc.perform(get("/admin/emails").header("Authorization", token))
			.andExpect(jsonPath("$[0].id").value(id))
			.andExpect(jsonPath("$[0].status").value("SENT"))
			.andExpect(jsonPath("$[0].recipientCount").value(2))
			.andExpect(jsonPath("$[0].sentCount").value(1))
			.andExpect(jsonPath("$[0].failedCount").value(1));
		assertThat(jdbc.sql("select error from campaign_recipients where email = :email")
			.param("email", refusedEmail)
			.query(String.class)
			.single()).contains("provider said no");
		assertThat(emailsTo(reachedEmail, subject, 1).get(0).idempotencyKey()).startsWith("campaign-" + id + "-");
	}

	@Test
	void aCampaignInterruptedByARestartCarriesOnWithThePeopleStillPending() throws Exception {
		String token = bearer();
		UUID id = UUID.randomUUID();
		UUID legacy = UUID.randomUUID();
		String subject = "Resumed " + unique();
		String alreadySent = unique() + "@example.com";
		String alreadyFailed = unique() + "@example.com";
		String stillPending = unique() + "@example.com";
		jdbc.sql("""
				insert into email_campaigns (id, kind, subject, body, audience, recipient_count, sent_count, status,
					created_by)
				values (:id, 'ANNOUNCEMENT', :subject, 'Hi {{firstName}} {{lastName}}', 'PRE_REGISTRANTS', 3, 1,
					'SENDING', 'someone@peachhacks.com'),
					(:legacy, 'ANNOUNCEMENT', :subject, 'Hi', 'PRE_REGISTRANTS', 9, 4, 'QUEUED',
					'someone@peachhacks.com')
				""").param("id", id).param("legacy", legacy).param("subject", subject).update();
		jdbc.sql("""
				insert into campaign_recipients (campaign_id, email, first_name, last_name, unsubscribe_token, status,
					sent_at, error)
				values (:id, :sent, 'Sam', 'Sent', 'tok-sent', 'SENT', now(), null),
					(:id, :failed, 'Fay', 'Failed', 'tok-failed', 'FAILED', null, 'provider said no'),
					(:id, :pending, 'Pat', 'Pending', 'tok-pending', 'PENDING', null, null)
				""")
			.param("id", id)
			.param("sent", alreadySent)
			.param("failed", alreadyFailed)
			.param("pending", stillPending)
			.update();

		campaignService.resumeInterrupted();
		awaitCampaign(id, "SENT");

		List<EmailMessage> resumed = emailsTo(stillPending, subject, 1);
		assertThat(resumed).hasSize(1);
		assertThat(resumed.get(0).text()).startsWith("Hi Pat Pending");
		assertThat(resumed.get(0).headers().get("List-Unsubscribe")).contains("token=tok-pending");
		assertThat(emailsTo(alreadySent, subject, 0)).as("marked SENT before the restart").isEmpty();
		assertThat(emailsTo(alreadyFailed, subject, 0)).as("a failure is not retried by a resume").isEmpty();
		mockMvc.perform(get("/admin/emails/" + id + "/recipients").header("Authorization", token))
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.items[2].status").value("SENT"));
		assertThat(jdbc.sql("select sent_count || '/' || failed_count from email_campaigns where id = :id")
			.param("id", id)
			.query(String.class)
			.single()).isEqualTo("2/1");

		assertThat(
				jdbc.sql("select status || ' ' || sent_count || ' ' || (completed_at is not null) from email_campaigns"
						+ " where id = :id")
					.param("id", legacy)
					.query(String.class)
					.single())
			.as("a campaign from before recipients were recorded cannot be resumed")
			.isEqualTo("FAILED 4 true");
		mockMvc.perform(get("/admin/emails/" + legacy + "/recipients").header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(0))
			.andExpect(jsonPath("$.items", hasSize(0)));
		jdbc.sql("delete from email_campaigns where id in (:id, :legacy)")
			.param("id", id)
			.param("legacy", legacy)
			.update();
	}

	@Test
	void anIdenticalCampaignIsRefusedWhileTheFirstIsStillSending() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		preRegister("Grace", "Hopper", unique() + "@example.com", school, "grace@school.edu")
			.andExpect(status().isCreated());
		String subject = "Held " + unique();
		CountDownLatch gate = new CountDownLatch(1);
		providerGate = gate;
		UUID first;
		UUID different;
		try {
			first = UUID.fromString(JsonPath.read(
					startCampaign(token, "ANNOUNCEMENT", "PRE_REGISTRANTS", school, subject)
						.andExpect(status().isAccepted())
						.andReturn()
						.getResponse()
						.getContentAsString(),
					"$.id"));
			startCampaign(token, "ANNOUNCEMENT", "PRE_REGISTRANTS", school, "  " + subject + " ")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_ALREADY_SENDING"))
				.andExpect(jsonPath("$.message").value(containsString("already being sent")));
			different = UUID.fromString(JsonPath.read(
					startCampaign(token, "ANNOUNCEMENT", "PRE_REGISTRANTS", school, subject + " again")
						.andExpect(status().isAccepted())
						.andReturn()
						.getResponse()
						.getContentAsString(),
					"$.id"));
		}
		finally {
			providerGate = null;
			gate.countDown();
		}
		awaitCampaign(first, "SENT");
		awaitCampaign(different, "SENT");
		assertThat(jdbc.sql("select count(*) from email_campaigns where subject = :subject")
			.param("subject", subject)
			.query(Long.class)
			.single()).isEqualTo(1);

		sendCampaign(token, "ANNOUNCEMENT", "PRE_REGISTRANTS", school, subject, 1);
	}

	@Test
	void registrationAppliesTheFormsRulesToPhoneLinkedinAndNames() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		String json = registrationJson(unique() + "@example.com", school, true, true);
		setRegistrationOpen(token, true);

		for (String phone : List.of("12345", "call 404 555 0100", "1234567890123456", "404-555-0100; now")) {
			register(json.replace("\"phone\": \"+1 404 555 0100\"", "\"phone\": \"" + phone + "\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.phone").value("Enter a valid phone number, with area code"));
		}
		for (String url : List.of("https://evil.example/in/ada", "https://linkedin.com.evil.example/in/ada",
				"https://evil.example/linkedin.com", "https://linkedin.com@evil.example/", "javascript:alert(1)",
				"ftp://linkedin.com/in/ada", "linkedin.com/in/ada")) {
			register(json.replace("\"linkedinUrl\": \"\"", "\"linkedinUrl\": \"" + url + "\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.linkedinUrl").value(containsString("LinkedIn")));
		}
		for (String name : List.of("https://evil.example", "www.evil.example/x", "Ada2", "ada@example.com", "a:b")) {
			register(named(json, name, "Lovelace")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.firstName")
					.value("Use letters, spaces, apostrophes, periods and hyphens only"));
			register(named(json, "Ada", name)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.lastName").isNotEmpty());
			preRegister(name, "Lovelace", unique() + "@example.com", school, "ada@school.edu")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.firstName").isNotEmpty());
		}
		preRegister("Ada", "Lovelace", "ada lovelace@example.com", school, "ada@school.edu")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.email").value("Must be a valid email"));
		assertThat(jdbc.sql("select count(*) from registrations where school = :school")
			.param("school", school)
			.query(Long.class)
			.single()).isZero();

		String created = register(named(json, "José María", "O’Neil-St. John")
			.replace("\"phone\": \"+1 404 555 0100\"", "\"phone\": \"(404) 555-0100 x12\"")
			.replace("\"linkedinUrl\": \"\"", "\"linkedinUrl\": \"HTTPS://uk.LinkedIn.com/in/ada?trk=x\""))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		setRegistrationOpen(token, false);
		deleteRegistrations(JsonPath.<String>read(created, "$.id"));
	}

	@Test
	void aPasswordOfMoreThan72BytesIsAFieldErrorNotAServerError() throws Exception {
		String admin = bearer();
		String email = unique() + "@test.local";
		String fits = "é".repeat(36);
		String tooLong = "é".repeat(40);
		String created = mockMvc
			.perform(post("/admin/admins").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"name\":\"Bytes\",\"role\":\"VOLUNTEER\"}".formatted(email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String linkToken = passwordToken(JsonPath.read(created, "$.setPasswordUrl"));

		setPassword(linkToken, tooLong).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.password").value(containsString("too long")));
		setPassword(linkToken, fits).andExpect(status().isNoContent());
		String session = "Bearer "
				+ JsonPath.read(login(email, fits).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
						"$.token");
		mockMvc
			.perform(post("/admin/auth/change-password").header("Authorization", session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(fits, tooLong)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.newPassword").value(containsString("too long")));
		login(email, tooLong).andExpect(status().isUnauthorized());
		login(email, fits).andExpect(status().isOk());

		mockMvc.perform(delete("/admin/admins/" + JsonPath.read(created, "$.id")).header("Authorization", admin))
			.andExpect(status().isNoContent());
	}

	@Test
	void oversizedBodiesAreRefusedOnThePublicAndSignInRoutes() throws Exception {
		String filler = "a".repeat(70_000);

		preRegister(filler, "Lovelace", unique() + "@example.com", uniqueSchool(), "ada@school.edu")
			.andExpect(status().is(413))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
			.andExpect(jsonPath("$.message").value("The request is too large."));
		login(unique() + "@test.local", filler).andExpect(status().is(413))
			.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
		mockMvc
			.perform(post("/public/unsubscribe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\"}".formatted(filler)))
			.andExpect(status().is(413));
		preRegister("a".repeat(60_000), "Lovelace", unique() + "@example.com", uniqueSchool(), "ada@school.edu")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.firstName").isNotEmpty());
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void everyCsvExportIsLoggedWithWhoRanItAndHowManyRowsButNothingFromTheRows(CapturedOutput output)
			throws Exception {
		String admin = bearer();
		String school = uniqueSchool();
		String email = unique() + "@example.com";
		preRegister("Ada", "Lovelace", email, school, "ada@school.edu").andExpect(status().isCreated());
		String registrationId = registerHacker(admin, email, school);
		setStatus(admin, registrationId, "ACCEPTED");
		mockMvc.perform(post("/admin/check-in/" + registrationId).header("Authorization", admin))
			.andExpect(status().isOk());
		String generalId = jdbc.sql("select id from events where general").query(UUID.class).single().toString();

		mockMvc
			.perform(get("/admin/registrations/export.csv").param("school", school)
				.param("status", "ACCEPTED")
				.param("checkedIn", "true")
				.header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(email)));
		mockMvc
			.perform(get("/admin/pre-registrations/export.csv").param("school", school).header("Authorization", admin))
			.andExpect(status().isOk());
		mockMvc.perform(get("/admin/pre-registrations/export.csv").param("q", "no-such-person-" + unique())
			.header("Authorization", admin)).andExpect(status().isOk());
		mockMvc.perform(get("/admin/events/" + generalId + "/export.csv").header("Authorization", admin))
			.andExpect(status().isOk());

		assertThat(output.getOut())
			.contains("Registrations CSV of 1 rows (school=" + school + ", status=ACCEPTED, checkedIn=true)"
					+ " exported by " + ADMIN_EMAIL)
			.contains("Pre-registrations CSV of 1 rows (school=" + school + ") exported by " + ADMIN_EMAIL)
			.containsPattern("Pre-registrations CSV of 0 rows \\(q=no-such-person-\\w+\\) exported by " + ADMIN_EMAIL)
			.containsPattern("Attendees CSV of \\d+ rows for event " + generalId
					+ " \\(\"General check-in\"\\) exported by " + ADMIN_EMAIL);
		assertThat(output.getOut().lines().filter(line -> line.contains(" CSV of ")).toList()).hasSize(4)
			.noneMatch(line -> line.contains(email) || line.contains("Lovelace"));
		deleteRegistrations(registrationId);
	}

	@Test
	void aStatusChangeDoesNotUndoAnUnsubscribeMadeWhileTheRowWasLoaded() throws Exception {
		String admin = bearer();
		UUID id = UUID.fromString(registerHacker(admin, unique() + "@example.com", uniqueSchool()));
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		TransactionTemplate separate = new TransactionTemplate(transactionManager);
		separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		transaction.executeWithoutResult(status -> {
			Registration registration = registrationRepository.findById(id).orElseThrow();
			separate.executeWithoutResult(inner -> jdbc
				.sql("update registrations set unsubscribed = true where id = :id")
				.param("id", id)
				.update());
			registration.changeStatus(RegistrationStatus.WAITLISTED, Instant.now());
		});

		assertThat(jdbc.sql("select status || ' ' || unsubscribed from registrations where id = :id")
			.param("id", id)
			.query(String.class)
			.single()).isEqualTo("WAITLISTED true");
		deleteRegistrations(id.toString());
	}

	private void tellAccepted(String adminToken, String registrationId) throws Exception {
		setStatus(adminToken, registrationId, "ACCEPTED");
		mockMvc
			.perform(post("/admin/registrations/" + registrationId + "/ticket-email").header("Authorization",
					adminToken))
			.andExpect(status().isNoContent());
	}

	private ResultActions startCampaign(String token, String kind, String audience, String school, String subject)
			throws Exception {
		return mockMvc.perform(post("/admin/emails").header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"kind":"%s","audience":"%s","school":"%s","subject":"%s","body":"Hi {{firstName}},\\n\\nDetails inside."}
					""".formatted(kind, audience, school, subject)));
	}

	private void awaitCampaign(UUID id, String expected) throws InterruptedException {
		String status = null;
		for (int attempt = 0; attempt < 100 && !expected.equals(status); attempt++) {
			Thread.sleep(100);
			status = jdbc.sql("select status from email_campaigns where id = :id")
				.param("id", id)
				.query(String.class)
				.single();
		}
		assertThat(status).isEqualTo(expected);
	}

	private static String passwordToken(String link) {
		Matcher matcher = PASSWORD_LINK.matcher(link);
		assertThat(matcher.find()).as("a set-password link in: " + link).isTrue();
		return matcher.group(1);
	}

	private ResultActions setPassword(String token, String password) throws Exception {
		return mockMvc.perform(post("/admin/auth/set-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password)));
	}

	private void choosePassword(String createdAccountJson, String password) throws Exception {
		setPassword(passwordToken(JsonPath.read(createdAccountJson, "$.setPasswordUrl")), password)
			.andExpect(status().isNoContent());
	}

	private EmailMessage awaitEmail(String to, String subjectPart) throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			for (EmailMessage message : sentEmails) {
				if (message.to().equals(to) && message.subject().contains(subjectPart)) {
					return message;
				}
			}
			Thread.sleep(50);
		}
		throw new AssertionError("No \"" + subjectPart + "\" email to " + to);
	}

	private String bearer() throws Exception {
		String body = login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.token");
	}

	private record Volunteer(String id, String token) {
	}

	private Volunteer createVolunteer(String adminToken, String name) throws Exception {
		String email = unique() + "@test.local";
		String created = mockMvc
			.perform(post("/admin/admins").header("Authorization", adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"name\":\"%s\",\"role\":\"VOLUNTEER\"}".formatted(email, name)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("VOLUNTEER"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		choosePassword(created, "volunteer-password");
		String session = login(email, "volunteer-password").andExpect(status().isOk())
			.andExpect(jsonPath("$.admin.role").value("VOLUNTEER"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new Volunteer(JsonPath.read(created, "$.id"), "Bearer " + JsonPath.read(session, "$.token"));
	}

	private String registerHacker(String adminToken, String email, String school) throws Exception {
		setRegistrationOpen(adminToken, true);
		String created = register(registrationJson(email, school, true, true)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		setRegistrationOpen(adminToken, false);
		return JsonPath.read(created, "$.id");
	}

	/** Test clean-up only: the API has no way to delete a registration. */
	private void deleteRegistrations(String... ids) {
		for (String id : ids) {
			jdbc.sql("delete from registrations where id = :id").param("id", UUID.fromString(id)).update();
		}
	}

	private void setStatus(String adminToken, String registrationId, String status) throws Exception {
		mockMvc
			.perform(patch("/admin/registrations/" + registrationId).header("Authorization", adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"%s\"}".formatted(status)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(status));
	}

	private ResultActions scan(String token, String code, String eventId) throws Exception {
		String event = (eventId != null) ? "\"" + eventId + "\"" : "null";
		return mockMvc.perform(post("/admin/check-in/scan").header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"%s\",\"eventId\":%s}".formatted(code, event)));
	}

	private static MockHttpServletRequestBuilder eventRequest(MockHttpServletRequestBuilder request, String token,
			String json) {
		return request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private String ticketToken(String registrationId) {
		return jdbc.sql("select ticket_token from registrations where id = :id")
			.param("id", UUID.fromString(registrationId))
			.query(String.class)
			.single();
	}

	private long checkInCount(String registrationId) {
		return jdbc.sql("select count(*) from check_ins where registration_id = :id")
			.param("id", UUID.fromString(registrationId))
			.query(Long.class)
			.single();
	}

	/** Mail is sent on a background thread, so wait for the expected number before reading. */
	private List<EmailMessage> ticketEmails(String to, int expected) throws InterruptedException {
		List<EmailMessage> matching = List.of();
		for (int attempt = 0; attempt < 50; attempt++) {
			matching = sentEmails.stream()
				.filter(message -> message.to().equals(to) && !message.attachments().isEmpty())
				.toList();
			if (matching.size() >= expected && expected > 0) {
				break;
			}
			Thread.sleep(expected > 0 ? 100 : 10);
		}
		return matching;
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private void setRegistrationOpen(String token, boolean open) throws Exception {
		mockMvc
			.perform(put("/admin/settings").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"registrationOpen\":" + open + "}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.registrationOpen").value(open));
	}

	private ResultActions recipientCount(String token, String kind, String audience, String school)
			throws Exception {
		return mockMvc
			.perform(post("/admin/emails/recipient-count").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\":\"%s\",\"audience\":\"%s\",\"school\":\"%s\"}".formatted(kind, audience,
						school)))
			.andExpect(status().isOk());
	}

	private String registeredId(String email, String school) throws Exception {
		String created = register(registrationJson(email, school, true, true)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(created, "$.id");
	}

	/** Starts a campaign to one school and waits until it has been sent. */
	private void sendCampaign(String token, String kind, String audience, String school, String subject,
			int recipients) throws Exception {
		String campaign = mockMvc
			.perform(post("/admin/emails").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"kind":"%s","audience":"%s","school":"%s","subject":"%s","body":"Hi {{firstName}},\\n\\nDetails inside."}
						""".formatted(kind, audience, school, subject)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.kind").value(kind))
			.andExpect(jsonPath("$.recipientCount").value(recipients))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(campaign, "$.id"));
		String status = null;
		for (int attempt = 0; attempt < 100 && !"SENT".equals(status); attempt++) {
			Thread.sleep(100);
			status = jdbc.sql("select status from email_campaigns where id = :id")
				.param("id", id)
				.query(String.class)
				.single();
		}
		assertThat(status).isEqualTo("SENT");
	}

	private static String registrationJson(String email, String school, boolean codeOfConduct, boolean dataSharing) {
		return """
				{
				  "firstName": "Ada", "lastName": "Lovelace", "age": 19, "phone": "+1 404 555 0100",
				  "email": "%s", "schoolEmail": "Ada.Lovelace@School.EDU", "school": "%s",
				  "levelOfStudy": "Undergraduate University (3+ year)", "countryOfResidence": "US",
				  "mlhCodeOfConduct": %s, "mlhDataSharing": %s, "mlhEmailOptIn": false,
				  "dietaryRestrictions": ["Vegetarian", "Halal"], "dietaryDetails": "",
				  "gender": "", "raceEthnicity": [], "tshirtSize": "M",
				  "linkedinUrl": "", "website": ""
				}
				""".formatted(email, school, codeOfConduct, dataSharing);
	}

	private String registerWithResume(String json, byte[] pdf, boolean optIn) throws Exception {
		String created = register(withResume(json, "resume.pdf", pdf, optIn)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(created, "$.id");
	}

	/** fileName is inserted into the JSON as written, so it may contain JSON escapes. */
	private static String withResume(String json, String fileName, byte[] content, boolean optIn) {
		return json.replace("\"website\": \"\"",
				"\"website\": \"\", \"resume\": {\"fileName\": \"%s\", \"contentBase64\": \"%s\"}, \"resumeOptIn\": %s"
					.formatted(fileName, Base64.getEncoder().encodeToString(content), optIn));
	}

	private static String named(String json, String firstName, String lastName) {
		return json.replace("\"firstName\": \"Ada\", \"lastName\": \"Lovelace\"",
				"\"firstName\": \"%s\", \"lastName\": \"%s\"".formatted(firstName, lastName));
	}

	/** A PDF header followed by random bytes, so a round trip that alters any byte value fails. */
	private static byte[] pdfBytes(int length) {
		byte[] bytes = new byte[length];
		ThreadLocalRandom.current().nextBytes(bytes);
		byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(header, 0, bytes, 0, header.length);
		return bytes;
	}

	private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				assertThat(entries.put(entry.getName(), zip.readAllBytes())).isNull();
			}
		}
		return entries;
	}

	private static String unique() {
		return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	private static String uniqueSchool() {
		return "School " + unique();
	}

}
