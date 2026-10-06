package com.peachhacks.backend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

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
import com.peachhacks.backend.email.EmailMessage;
import com.peachhacks.backend.email.EmailSender;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
		"app.rate-limit.public-per-minute=100000", "app.rate-limit.login-per-minute=100000",
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

	private static final Pattern CONFIRM_LINK = Pattern.compile("/confirm-email\\?token=([A-Za-z0-9_-]+)");

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingEmail {

		@Bean
		@Primary
		EmailSender recordingEmailSender() {
			return sentEmails::add;
		}

	}

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private AuthService authService;

	@Test
	void preRegistrationIsIdempotentOnEmail() throws Exception {
		String school = uniqueSchool();
		String email = unique() + "@example.com";

		String first = preRegister("Ada", "Lovelace", email, school, "A.Lovelace@School.edu").andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String second = preRegister("Augusta", "King", email.toUpperCase(), school, "ada@school.edu")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
		mockMvc.perform(get("/admin/pre-registrations").param("school", school).header("Authorization", bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(25))
			.andExpect(jsonPath("$.items[0].firstName").value("Augusta"))
			.andExpect(jsonPath("$.items[0].email").value(email))
			.andExpect(jsonPath("$.items[0].schoolEmail").value("ada@school.edu"))
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

		register(registrationJson(email.toUpperCase(), school, true, true)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));

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
			.andExpect(jsonPath("$.shippingAddress.city").value("Atlanta"))
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

		mockMvc.perform(delete("/admin/registrations/" + id).header("Authorization", token))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/admin/registrations/" + id).header("Authorization", token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		setRegistrationOpen(token, false);
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
				.content("{\"email\":\"%s\",\"name\":\"Second\",\"password\":\"short\"}".formatted(email)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
		String created = mockMvc
			.perform(post("/admin/admins").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"name\":\"Second\",\"password\":\"another-long-password\"}"
					.formatted(email)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(created, "$.id");
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
			.andExpect(content().string(not(containsString("Peachtree"))))
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
				.string(containsString(",linkedinUrl,checked_in_at,has_resume,resume_opt_in,school_email,school_email_confirmed\r\n")))
			.andExpect(content().string(containsString(prefix + "a@example.com")))
			.andExpect(content().string(containsString(prefix + "b@example.com")))
			.andExpect(content()
				.string(containsString("," + checkedInAt + ",false,false,ada.lovelace@school.edu,false\r\n")));
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
		deleteRegistrations(admin, firstId, secondId);
	}

	@Test
	void workshopsHaveTheirOwnCheckInsAndReportMissingGeneralCheckIn() throws Exception {
		String admin = bearer();
		String prefix = unique();
		String registrationId = registerHacker(admin, prefix + "@example.com", uniqueSchool());
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
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/admin/events/" + eventId).header("Authorization", admin))
			.andExpect(status().isNotFound());
		assertThat(jdbc.sql("select count(*) from check_ins where registration_id = :id")
			.param("id", UUID.fromString(registrationId))
			.query(Long.class)
			.single()).as("only the general check-in survives the workshop").isEqualTo(1);
		deleteRegistrations(admin, registrationId);
	}

	@Test
	void ticketsCanBeScannedByTokenOrUrlAndNonAcceptedTicketsNeedAnOverride() throws Exception {
		String admin = bearer();
		String acceptedId = registerHacker(admin, unique() + "@example.com", uniqueSchool());
		String pendingId = registerHacker(admin, unique() + "@example.com", uniqueSchool());
		setStatus(admin, acceptedId, "ACCEPTED");
		Volunteer volunteer = createVolunteer(admin, "Scanner");
		String token = ticketToken(acceptedId);

		String first = scan(volunteer.token(), token, null, false).andExpect(jsonPath("$.result").value("CHECKED_IN"))
			.andExpect(jsonPath("$.event.general").value(true))
			.andExpect(jsonPath("$.item.id").value(acceptedId))
			.andExpect(jsonPath("$.item.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.item.checkedInBy").value("Scanner"))
			.andExpect(jsonPath("$.item.phone").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String checkedInAt = JsonPath.read(first, "$.item.checkedInAt");
		scan(admin, "http://localhost:5173/ticket?t=" + token, null, false)
			.andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
			.andExpect(jsonPath("$.item.checkedInAt").value(checkedInAt))
			.andExpect(jsonPath("$.item.checkedInBy").value("Scanner"));
		scan(volunteer.token(), "  https://www.peachhacks.com/ticket?utm=x&t=" + token + "#top ", null, false)
			.andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"));

		for (String unknown : new String[] { com.peachhacks.backend.common.Tokens.random(), "hello",
				"https://example.com/ticket?t=nope", "https://example.com/menu" }) {
			scan(volunteer.token(), unknown, null, false).andExpect(jsonPath("$.result").value("NOT_RECOGNISED"))
				.andExpect(jsonPath("$.item").value(nullValue()));
		}
		mockMvc
			.perform(post("/admin/check-in/scan").header("Authorization", volunteer.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.code").isNotEmpty());

		String pendingToken = ticketToken(pendingId);
		scan(volunteer.token(), pendingToken, null, false).andExpect(jsonPath("$.result").value("NOT_ACCEPTED"))
			.andExpect(jsonPath("$.item.id").value(pendingId))
			.andExpect(jsonPath("$.item.firstName").value("Ada"))
			.andExpect(jsonPath("$.item.status").value("PENDING"))
			.andExpect(jsonPath("$.item.checkedInAt").value(nullValue()));
		assertThat(checkInCount(pendingId)).isZero();
		scan(volunteer.token(), pendingToken, null, true).andExpect(jsonPath("$.result").value("CHECKED_IN"))
			.andExpect(jsonPath("$.item.status").value("PENDING"))
			.andExpect(jsonPath("$.item.checkedInAt").isNotEmpty());
		assertThat(checkInCount(pendingId)).isEqualTo(1);

		String event = mockMvc
			.perform(eventRequest(post("/admin/events"), admin, "{\"name\":\"Workshop %s\"}".formatted(unique())))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String eventId = JsonPath.read(event, "$.id");
		mockMvc.perform(delete("/admin/check-in/" + acceptedId).header("Authorization", admin))
			.andExpect(status().isOk());
		scan(volunteer.token(), token, eventId, false).andExpect(jsonPath("$.result").value("CHECKED_IN"))
			.andExpect(jsonPath("$.event.id").value(eventId))
			.andExpect(jsonPath("$.event.general").value(false))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(false));
		scan(volunteer.token(), token, null, false).andExpect(jsonPath("$.result").value("CHECKED_IN"));
		scan(volunteer.token(), token, eventId, false).andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(true));
		scan(volunteer.token(), token, UUID.randomUUID().toString(), false).andExpect(status().isNotFound());

		mockMvc.perform(delete("/admin/events/" + eventId).header("Authorization", admin))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/admin/admins/" + volunteer.id()).header("Authorization", admin))
			.andExpect(status().isNoContent());
		deleteRegistrations(admin, acceptedId, pendingId);
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
		deleteRegistrations(admin, registrationId);
	}

	@Test
	void acceptanceSendsTheTicketEmailOncePerTransition() throws Exception {
		String admin = bearer();
		String email = unique() + "@example.com";
		String registrationId = registerHacker(admin, email, uniqueSchool());
		String ticketUrl = "http://localhost:5173/ticket?t=" + ticketToken(registrationId);

		mockMvc.perform(post("/admin/registrations/" + registrationId + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		setStatus(admin, registrationId, "WAITLISTED");
		assertThat(ticketEmails(email, 0)).isEmpty();

		setStatus(admin, registrationId, "ACCEPTED");
		List<EmailMessage> sent = ticketEmails(email, 1);
		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.subject()).contains("You're in");
		assertThat(message.text()).contains("Hi Ada,").contains(ticketUrl).doesNotContainIgnoringCase("wallet");
		assertThat(message.html()).contains("cid:peachhacks-ticket").contains("ticket?t=");
		assertThat(message.attachments()).hasSize(1);
		assertThat(message.attachments().get(0).contentType()).isEqualTo("image/png");
		assertThat(Arrays.copyOf(message.attachments().get(0).content(), 4)).containsExactly(0x89, 'P', 'N', 'G');

		setStatus(admin, registrationId, "ACCEPTED");
		assertThat(ticketEmails(email, 1)).as("staying ACCEPTED does not send again").hasSize(1);
		setStatus(admin, registrationId, "PENDING");
		setStatus(admin, registrationId, "ACCEPTED");
		assertThat(ticketEmails(email, 2)).hasSize(2);

		mockMvc.perform(post("/admin/registrations/" + registrationId + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isNoContent());
		assertThat(ticketEmails(email, 3)).hasSize(3);
		mockMvc
			.perform(post("/admin/registrations/" + UUID.randomUUID() + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isNotFound());
		deleteRegistrations(admin, registrationId);
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
				delete("/admin/registrations/" + registrationId), get("/admin/registrations/export.csv"),
				post("/admin/registrations/" + registrationId + "/ticket-email"),
				post("/admin/registrations/" + registrationId + "/school-email/resend"),
				post("/admin/pre-registrations/" + UUID.randomUUID() + "/school-email/resend"),
				get("/admin/registrations/" + registrationId + "/resume"),
				delete("/admin/registrations/" + registrationId + "/resume"), get("/admin/resumes/export.zip"),
				get("/admin/pre-registrations"),
				get("/admin/pre-registrations/export.csv"), delete("/admin/pre-registrations/" + UUID.randomUUID()),
				get("/admin/stats"), get("/admin/settings"),
				put("/admin/settings").contentType(MediaType.APPLICATION_JSON).content("{\"registrationOpen\":true}"),
				get("/admin/emails"), post("/admin/emails").contentType(MediaType.APPLICATION_JSON).content(json),
				post("/admin/emails/test").contentType(MediaType.APPLICATION_JSON).content(json),
				post("/admin/emails/recipient-count").contentType(MediaType.APPLICATION_JSON).content(json),
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
		deleteRegistrations(admin, registrationId);
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
	void campaignAudienceCountsSkipUnsubscribedAndRegistered() throws Exception {
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

		recipientCount(token, "PRE_REGISTRANTS", school).andExpect(jsonPath("$.recipientCount").value(3));

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

		recipientCount(token, "PRE_REGISTRANTS", school).andExpect(jsonPath("$.recipientCount").value(2));
		recipientCount(token, "PRE_REGISTRANTS_NOT_REGISTERED", school)
			.andExpect(jsonPath("$.recipientCount").value(1));
		recipientCount(token, "REGISTRANTS", school).andExpect(jsonPath("$.recipientCount").value(1));
		mockMvc
			.perform(post("/admin/emails/recipient-count").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"audience\":\"PRE_REGISTRANTS\",\"school\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recipientCount").isNumber());

		mockMvc
			.perform(post("/admin/emails/test").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"subject\":\"Hello {{firstName}}\",\"body\":\"Line one\\n\\nLine two\"}"))
			.andExpect(status().isNoContent());
		String campaign = mockMvc
			.perform(post("/admin/emails").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"audience":"PRE_REGISTRANTS","school":"%s","subject":"Registration is open","body":"Hi {{firstName}},\\n\\nCome register."}
						""".formatted(school)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.recipientCount").value(2))
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
			.andExpect(jsonPath("$[0].sentCount").value(2))
			.andExpect(jsonPath("$[0].failedCount").value(0))
			.andExpect(jsonPath("$[0].completedAt").isNotEmpty());
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
			.andExpect(content().string(containsString(",true,true,ada.lovelace@school.edu,false\r\n")));
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
		deleteRegistrations(admin, id);
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
		deleteRegistrations(admin, JsonPath.<String>read(created, "$.id"));
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
		deleteRegistrations(admin, id);
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

		deleteRegistrations(admin, shared, attended, pending, notOptedIn, noResume);
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
		assertThat(links.get(0).html()).contains("confirm-email?token=").contains("Confirm my school email");
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
			.andExpect(content().string(containsString("resume_opt_in,school_email,school_email_confirmed\r\n")))
			.andExpect(content().string(containsString("," + sameSchoolEmail + ",true\r\n")))
			.andExpect(content().string(not(containsString(otherSchoolEmail))));
		mockMvc.perform(get("/admin/stats").header("Authorization", admin))
			.andExpect(jsonPath("$.registrations.schoolEmailConfirmed").isNumber());

		// Acceptance is the organizers' call: an unconfirmed school email does not block it.
		setStatus(admin, changed, "ACCEPTED");

		deleteRegistrations(admin, same, changed);
		assertThat(pairCount(changedEmail, otherSchoolEmail)).as("the registration's pair goes with it").isZero();
		assertThat(pairCount(sameEmail, sameSchoolEmail)).as("the pre-registration still uses this pair").isEqualTo(1);
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

		deleteRegistrations(admin, registrationId);
		mockMvc.perform(delete("/admin/pre-registrations/" + preRegistrationId).header("Authorization", admin))
			.andExpect(status().isNoContent());
		assertThat(count("select count(*) from school_email_confirmations where school_email in ('" + schoolEmail
				+ "', '" + preSchoolEmail + "')")).isZero();
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
		deleteRegistrations(admin, registrationId);
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

	private long pairCount(String email, String schoolEmail) {
		return jdbc.sql("select count(*) from school_email_confirmations where email = :email and school_email = :schoolEmail")
			.param("email", email)
			.param("schoolEmail", schoolEmail)
			.query(Long.class)
			.single();
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
				.content("{\"email\":\"%s\",\"name\":\"%s\",\"password\":\"volunteer-password\",\"role\":\"VOLUNTEER\"}"
					.formatted(email, name)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("VOLUNTEER"))
			.andReturn()
			.getResponse()
			.getContentAsString();
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

	private void deleteRegistrations(String adminToken, String... ids) throws Exception {
		for (String id : ids) {
			mockMvc.perform(delete("/admin/registrations/" + id).header("Authorization", adminToken))
				.andExpect(status().isNoContent());
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

	private ResultActions scan(String token, String code, String eventId, boolean override) throws Exception {
		String event = (eventId != null) ? "\"" + eventId + "\"" : "null";
		return mockMvc.perform(post("/admin/check-in/scan").header("Authorization", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"%s\",\"eventId\":%s,\"override\":%s}".formatted(code, event, override)));
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

	private ResultActions recipientCount(String token, String audience, String school) throws Exception {
		return mockMvc
			.perform(post("/admin/emails/recipient-count").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"audience\":\"%s\",\"school\":\"%s\"}".formatted(audience, school)))
			.andExpect(status().isOk());
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
				  "shippingAddress": { "line1": "1 Peachtree St", "line2": "", "city": "Atlanta", "state": "GA", "country": "US", "postalCode": "30303" },
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
