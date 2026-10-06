package com.peachhacks.backend;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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

	@Autowired
	private JdbcClient jdbc;

	@Test
	void preRegistrationIsIdempotentOnEmail() throws Exception {
		String school = uniqueSchool();
		String email = unique() + "@example.com";

		String first = preRegister("Ada", "Lovelace", email, school, "").andExpect(status().isCreated())
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
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.firstName").value("First name is required"))
			.andExpect(jsonPath("$.fieldErrors.email").value("Must be a valid email"));

		mockMvc
			.perform(post("/public/pre-registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"Bot","lastName":"Bot","email":"%s@example.com","school":"%s","website":"http://spam.example"}
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
			.andExpect(jsonPath("$.items[0].levelOfStudy").value("Undergraduate University (3+ year)"));
		mockMvc.perform(get("/admin/registrations/" + id).header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
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
	void campaignAudienceCountsSkipUnsubscribedAndRegistered() throws Exception {
		String token = bearer();
		String school = uniqueSchool();
		String registeredEmail = unique() + "@example.com";
		String unsubscribedEmail = unique() + "@example.com";
		preRegister("Grace", "Hopper", unique() + "@example.com", school, "").andExpect(status().isCreated());
		preRegister("Katherine", "Johnson", registeredEmail, school, "").andExpect(status().isCreated());
		preRegister("Dorothy", "Vaughan", unsubscribedEmail, school, "").andExpect(status().isCreated());
		preRegister("Mary", "Jackson", unique() + "@example.com", uniqueSchool(), "").andExpect(status().isCreated());
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
				  "email": "%s", "school": "%s",
				  "levelOfStudy": "Undergraduate University (3+ year)", "countryOfResidence": "US",
				  "mlhCodeOfConduct": %s, "mlhDataSharing": %s, "mlhEmailOptIn": false,
				  "dietaryRestrictions": ["Vegetarian", "Halal"], "dietaryDetails": "",
				  "gender": "", "raceEthnicity": [], "tshirtSize": "M",
				  "shippingAddress": { "line1": "1 Peachtree St", "line2": "", "city": "Atlanta", "state": "GA", "country": "US", "postalCode": "30303" },
				  "linkedinUrl": "", "website": ""
				}
				""".formatted(email, school, codeOfConduct, dataSharing);
	}

	private static String unique() {
		return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	private static String uniqueSchool() {
		return "School " + unique();
	}

}
