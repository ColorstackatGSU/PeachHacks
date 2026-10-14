package com.peachhacks.backend;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.admin.bootstrap-email=attendance@test.local",
		"app.admin.bootstrap-password=correct-horse-battery", "app.admin.bootstrap-name=Test Organizer",
		"app.rate-limit.public-per-minute=100000", "app.rate-limit.sign-up-per-window=100000",
		"app.rate-limit.sign-up-global-per-hour=100000" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AttendanceApiTests {

	private static final String PASSWORD = "a-long-test-password";

	private static final Pattern PASSWORD_LINK = Pattern.compile("/#/set-password\\?token=([A-Za-z0-9_-]+)");

	private static final Instant BASE = Instant.now().minus(Duration.ofHours(10)).truncatedTo(ChronoUnit.SECONDS);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void arrivalsCarryNamesSchoolStaffAndTheirEventsAndNothingElse() throws Exception {
		String admin = bearer();
		String volunteer = account(admin, "VOLUNTEER");
		String token = letters();
		String ada = accepted(admin, "Ada", "Lovelace" + token, "Example University");
		String grace = accepted(admin, "Grace", "Hopper" + token, "Other University");
		String workshopOnly = accepted(admin, "Alan", "Turing" + token, "Third University");
		String later = event(admin, "Dinner " + token);
		String earlier = event(admin, "Workshop " + token);
		checkIn(admin, ada, null, BASE);
		checkIn(admin, ada, later, BASE.plus(Duration.ofHours(3)));
		checkIn(admin, ada, earlier, BASE.plus(Duration.ofHours(1)));
		checkIn(admin, grace, null, BASE.plus(Duration.ofMinutes(5)));
		checkIn(admin, workshopOnly, earlier, BASE.plus(Duration.ofHours(2)));
		json(patch("/admin/registrations/" + grace), admin, "{\"staff\":true}").andExpect(status().isOk());
		json(patch("/admin/registrations/" + grace), admin, "{\"status\":\"WAITLISTED\"}").andExpect(status().isOk());

		for (String caller : List.of(admin, volunteer)) {
			String body = mockMvc.perform(get("/admin/attendance").param("q", token).header("Authorization", caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(25))
				.andExpect(jsonPath("$.items[0].id").value(grace))
				.andExpect(jsonPath("$.items[0].firstName").value("Grace"))
				.andExpect(jsonPath("$.items[0].school").value("Other University"))
				.andExpect(jsonPath("$.items[0].staff").value(true))
				.andExpect(jsonPath("$.items[0].arrivedAt").value(BASE.plus(Duration.ofMinutes(5)).toString()))
				.andExpect(jsonPath("$.items[0].events.length()").value(0))
				.andExpect(jsonPath("$.items[1].id").value(ada))
				.andExpect(jsonPath("$.items[1].lastName").value("Lovelace" + token))
				.andExpect(jsonPath("$.items[1].staff").value(false))
				.andExpect(jsonPath("$.items[1].arrivedAt").value(BASE.toString()))
				.andExpect(jsonPath("$.items[1].events.length()").value(2))
				.andExpect(jsonPath("$.items[1].events[0].eventId").value(earlier))
				.andExpect(jsonPath("$.items[1].events[0].name").value("Workshop " + token))
				.andExpect(jsonPath("$.items[1].events[0].checkedInAt")
					.value(BASE.plus(Duration.ofHours(1)).toString()))
				.andExpect(jsonPath("$.items[1].events[1].eventId").value(later))
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(((Map<String, Object>) JsonPath.read(body, "$")).keySet()).containsExactlyInAnyOrder("items",
					"total", "page", "size");
			assertThat(((Map<String, Object>) JsonPath.read(body, "$.items[1]")).keySet())
				.containsExactlyInAnyOrder("id", "firstName", "lastName", "school", "staff", "arrivedAt", "events");
			assertThat(((Map<String, Object>) JsonPath.read(body, "$.items[1].events[0]")).keySet())
				.containsExactlyInAnyOrder("eventId", "name", "checkedInAt");
			assertThat(body).doesNotContain("@").doesNotContain("checkedInBy").doesNotContain("Test Organizer")
				.doesNotContain("status").doesNotContain(workshopOnly);
		}

		for (String caller : List.of(admin, volunteer)) {
			String body = mockMvc
				.perform(get("/admin/events/" + earlier + "/attendance").param("q", token)
					.header("Authorization", caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.event.id").value(earlier))
				.andExpect(jsonPath("$.event.name").value("Workshop " + token))
				.andExpect(jsonPath("$.event.general").value(false))
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.items[0].id").value(workshopOnly))
				.andExpect(jsonPath("$.items[0].checkedInAt").value(BASE.plus(Duration.ofHours(2)).toString()))
				.andExpect(jsonPath("$.items[1].id").value(ada))
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(((Map<String, Object>) JsonPath.read(body, "$")).keySet()).containsExactlyInAnyOrder("event",
					"items", "total", "page", "size");
			assertThat(((Map<String, Object>) JsonPath.read(body, "$.event")).keySet())
				.containsExactlyInAnyOrder("id", "name", "general");
			assertThat(((Map<String, Object>) JsonPath.read(body, "$.items[0]")).keySet())
				.containsExactlyInAnyOrder("id", "firstName", "lastName", "school", "staff", "checkedInAt");
			assertThat(body).doesNotContain("@").doesNotContain("checkedInBy").doesNotContain("Test Organizer");
		}

		String general = jdbc.sql("select id from events where general").query(UUID.class).single().toString();
		mockMvc
			.perform(get("/admin/events/" + general + "/attendance").param("q", token)
				.header("Authorization", volunteer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.event.general").value(true))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.items[0].id").value(grace))
			.andExpect(jsonPath("$.items[0].staff").value(true))
			.andExpect(jsonPath("$.items[1].id").value(ada));
		mockMvc.perform(get("/admin/events/" + later + "/attendance").param("q", token)
			.header("Authorization", volunteer)).andExpect(jsonPath("$.total").value(1));
	}

	@Test
	void searchMatchesFirstLastAndFullName() throws Exception {
		String admin = bearer();
		String volunteer = account(admin, "VOLUNTEER");
		String token = letters();
		String hopper = accepted(admin, "Grace" + token, "Hopper" + token, "A School");
		String ada = accepted(admin, "Ada" + token, "Lovelace" + token, "A School");
		String both = accepted(admin, "Grace" + token, "Lovelace" + token, "A School");
		String workshop = event(admin, "Talk " + token);
		for (String id : List.of(hopper, ada, both)) {
			checkIn(admin, id, null, BASE);
			checkIn(admin, id, workshop, BASE);
		}

		Map<String, List<String>> expected = Map.of("grace" + token, List.of(hopper, both),
				("LOVELACE" + token).toUpperCase(), List.of(ada, both),
				"  grace" + token + " lovelace" + token + " ", List.of(both), "Ada" + token, List.of(ada),
				token, List.of(hopper, ada, both), "nobody" + token, List.of(), "ada" + token + " hopper", List.of());
		for (Map.Entry<String, List<String>> search : expected.entrySet()) {
			for (String path : List.of("/admin/attendance", "/admin/events/" + workshop + "/attendance")) {
				String body = mockMvc
					.perform(get(path).param("q", search.getKey()).header("Authorization", volunteer))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.total").value(search.getValue().size()))
					.andReturn()
					.getResponse()
					.getContentAsString();
				List<String> ids = JsonPath.read(body, "$.items[*].id");
				assertThat(ids).as(path + " q=" + search.getKey()).containsExactlyInAnyOrderElementsOf(search.getValue());
			}
		}
		mockMvc.perform(get("/admin/attendance").param("q", "%").header("Authorization", volunteer))
			.andExpect(jsonPath("$.total").value(0));
		mockMvc.perform(get("/admin/attendance").param("q", "_").header("Authorization", volunteer))
			.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void newestFirstInPagesOfAtMostAHundred() throws Exception {
		String admin = bearer();
		String volunteer = account(admin, "VOLUNTEER");
		String token = letters();
		String workshop = event(admin, "Pages " + token);
		String[] people = new String[5];
		for (int i = 0; i < people.length; i++) {
			people[i] = accepted(admin, "Person", token + letters().substring(0, 3), "A School");
			checkIn(admin, people[i], null, BASE.plus(Duration.ofMinutes(i)));
			checkIn(admin, people[i], workshop, BASE.plus(Duration.ofMinutes(10 - i)));
		}
		List<String> newestArrivalFirst = List.of(people[4], people[3], people[2], people[1], people[0]);
		List<String> newestAtWorkshopFirst = List.of(people[0], people[1], people[2], people[3], people[4]);

		for (Map.Entry<String, List<String>> list : Map
			.of("/admin/attendance", newestArrivalFirst, "/admin/events/" + workshop + "/attendance",
					newestAtWorkshopFirst)
			.entrySet()) {
			List<String> all = ids(volunteer, list.getKey(), token, null, null);
			assertThat(all).as(list.getKey()).containsExactlyElementsOf(list.getValue());
			assertThat(ids(volunteer, list.getKey(), token, 0, 2)).containsExactlyElementsOf(list.getValue().subList(0, 2));
			assertThat(ids(volunteer, list.getKey(), token, 1, 2)).containsExactlyElementsOf(list.getValue().subList(2, 4));
			assertThat(ids(volunteer, list.getKey(), token, 2, 2)).containsExactlyElementsOf(list.getValue().subList(4, 5));
			assertThat(ids(volunteer, list.getKey(), token, 3, 2)).isEmpty();
			mockMvc
				.perform(get(list.getKey()).param("q", token).param("page", "1").param("size", "2")
					.header("Authorization", volunteer))
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2));
			mockMvc.perform(get(list.getKey()).param("size", "1000").header("Authorization", volunteer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
			mockMvc
				.perform(get(list.getKey()).param("q", token).param("size", "0").param("page", "-3")
					.header("Authorization", volunteer))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.items.length()").value(1));
		}
	}

	@Test
	void staffOfEveryRoleMayLookAndLookingChangesNothing() throws Exception {
		String admin = bearer();
		String volunteer = account(admin, "VOLUNTEER");
		String lookup = account(admin, "LOOKUP");
		String token = letters();
		String hacker = accepted(admin, "Ada", "Lovelace" + token, "A School");
		String workshop = event(admin, "Roles " + token);
		checkIn(admin, hacker, null, BASE);
		checkIn(admin, hacker, workshop, BASE);
		long checkIns = count("select count(*) from check_ins");
		long badges = count("select count(*) from badges");
		String registrations = jdbc.sql("select string_agg(id::text || status || staff, ',' order by id) from registrations")
			.query(String.class)
			.single();
		String times = jdbc.sql("select string_agg(checked_in_at::text, ',' order by id) from check_ins")
			.query(String.class)
			.single();

		for (String path : List.of("/admin/attendance", "/admin/attendance/events",
				"/admin/events/" + workshop + "/attendance")) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
			for (String caller : List.of(admin, volunteer, lookup)) {
				mockMvc.perform(get(path).header("Authorization", caller)).andExpect(status().isOk());
			}
			for (MockHttpServletRequestBuilder write : List.of(post(path), delete(path), put(path))) {
				for (String caller : List.of(volunteer, lookup)) {
					mockMvc.perform(write.header("Authorization", caller)).andExpect(status().isForbidden());
				}
			}
			mockMvc.perform(post(path).header("Authorization", admin)).andExpect(status().isMethodNotAllowed());
		}
		mockMvc.perform(get("/admin/attendance").param("q", token).header("Authorization", lookup))
			.andExpect(jsonPath("$.items[0].id").value(hacker))
			.andExpect(jsonPath("$.items[0].events[0].eventId").value(workshop));
		mockMvc.perform(get("/admin/events/" + workshop + "/attendance").header("Authorization", lookup))
			.andExpect(jsonPath("$.items[0].id").value(hacker));

		String events = mockMvc.perform(get("/admin/events").header("Authorization", admin))
			.andReturn()
			.getResponse()
			.getContentAsString();
		for (String caller : List.of(admin, volunteer, lookup)) {
			String listed = mockMvc.perform(get("/admin/attendance/events").header("Authorization", caller))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].general").value(true))
				.andExpect(jsonPath("$[?(@.id == '%s')].checkedIn".formatted(workshop)).value(1))
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(listed).as("the same answer as GET /admin/events").isEqualTo(events);
			assertThat(((Map<String, Object>) JsonPath.read(listed, "$[0]")).keySet())
				.containsExactlyInAnyOrder("id", "name", "startsAt", "general", "checkedIn");
		}

		String general = jdbc.sql("select id from events where general").query(UUID.class).single().toString();
		for (MockHttpServletRequestBuilder refused : List.of(get("/admin/events"), get("/admin/check-in"),
				get("/admin/events/" + workshop + "/export.csv"), get("/admin/registrations"),
				get("/admin/registrations/" + hacker), get("/admin/stats"), get("/admin/badges/sponsors"),
				post("/admin/check-in/" + hacker), delete("/admin/check-in/" + hacker).param("eventId", general),
				post("/admin/badges/resolve").contentType(MediaType.APPLICATION_JSON)
					.content("{\"registrationId\":\"%s\"}".formatted(hacker)),
				post("/admin/badges/tap").contentType(MediaType.APPLICATION_JSON).content("{\"uid\":\"04A1B2C3\"}"),
				get("/admin/attendance-later"), get("/admin/attendance/events/" + workshop))) {
			mockMvc.perform(refused.header("Authorization", lookup))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		mockMvc.perform(get("/admin/events/" + UUID.randomUUID() + "/attendance").header("Authorization", volunteer))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/admin/events/not-an-id/attendance").header("Authorization", admin))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/admin/events/" + workshop + "/export.csv").header("Authorization", volunteer))
			.andExpect(status().isForbidden());

		try {
			json(put("/admin/settings"), admin, "{\"webCheckInAdminOnly\":true}").andExpect(status().isOk());
			mockMvc.perform(get("/admin/check-in").header("Authorization", volunteer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("WEB_CHECK_IN_ADMIN_ONLY"));
			mockMvc.perform(get("/admin/attendance").header("Authorization", volunteer)).andExpect(status().isOk());
			mockMvc.perform(get("/admin/events/" + workshop + "/attendance").header("Authorization", volunteer))
				.andExpect(status().isOk());
		}
		finally {
			json(put("/admin/settings"), admin, "{\"webCheckInAdminOnly\":false}").andExpect(status().isOk());
		}

		assertThat(count("select count(*) from check_ins")).isEqualTo(checkIns);
		assertThat(count("select count(*) from badges")).isEqualTo(badges);
		assertThat(jdbc.sql("select string_agg(id::text || status || staff, ',' order by id) from registrations")
			.query(String.class)
			.single()).isEqualTo(registrations);
		assertThat(jdbc.sql("select string_agg(checked_in_at::text, ',' order by id) from check_ins")
			.query(String.class)
			.single()).isEqualTo(times);
	}

	private List<String> ids(String token, String path, String q, Integer page, Integer size) throws Exception {
		MockHttpServletRequestBuilder request = get(path).param("q", q).header("Authorization", token);
		if (page != null) {
			request.param("page", page.toString()).param("size", size.toString());
		}
		String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.items[*].id");
	}

	/** Checks in through the API, then sets the time so the order is known. */
	private void checkIn(String admin, String registrationId, String eventId, Instant at) throws Exception {
		MockHttpServletRequestBuilder request = post("/admin/check-in/" + registrationId).header("Authorization",
				admin);
		if (eventId != null) {
			request.param("eventId", eventId);
		}
		mockMvc.perform(request).andExpect(status().isOk());
		jdbc.sql("""
				update check_ins set checked_in_at = :at
				where registration_id = :id
				  and event_id = coalesce(:eventId, (select id from events where general))
				""")
			.param("at", Timestamp.from(at))
			.param("id", UUID.fromString(registrationId))
			.param("eventId", (eventId != null) ? UUID.fromString(eventId) : null, java.sql.Types.OTHER)
			.update();
	}

	private String accepted(String admin, String firstName, String lastName, String school) throws Exception {
		String email = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "@example.com";
		json(put("/admin/settings"), admin, "{\"registrationOpen\":true}").andExpect(status().isOk());
		String created = mockMvc
			.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"%s","lastName":"%s","dateOfBirth":"2007-06-01","phone":"404 555 0100","email":"%s",
					 "schoolEmail":"%s","school":"%s","levelOfStudy":"Undergraduate University (3+ year)",
					 "graduationYear":2028,"graduationMonth":5,"countryOfResidence":"US",
					 "mlhCodeOfConduct":true,"mlhDataSharing":true,"mlhEmailOptIn":false}
					""".formatted(firstName, lastName, email, email.replace("@example.com", "@school.edu"), school)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		json(put("/admin/settings"), admin, "{\"registrationOpen\":false}").andExpect(status().isOk());
		String id = JsonPath.read(created, "$.id");
		json(patch("/admin/registrations/" + id), admin, "{\"status\":\"ACCEPTED\"}").andExpect(status().isOk());
		return id;
	}

	private String event(String admin, String name) throws Exception {
		String created = json(post("/admin/events"), admin, "{\"name\":\"%s\"}".formatted(name))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(created, "$.id");
	}

	private String account(String admin, String role) throws Exception {
		String email = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "@test.local";
		String created = json(post("/admin/admins"), admin,
				"{\"email\":\"%s\",\"name\":\"Staff Member\",\"role\":\"%s\"}".formatted(email, role))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Matcher link = PASSWORD_LINK.matcher((String) JsonPath.read(created, "$.setPasswordUrl"));
		assertThat(link.find()).isTrue();
		mockMvc
			.perform(post("/admin/auth/set-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(link.group(1), PASSWORD)))
			.andExpect(status().isNoContent());
		return login(email, PASSWORD);
	}

	private String bearer() throws Exception {
		return login("attendance@test.local", "correct-horse-battery");
	}

	private String login(String email, String password) throws Exception {
		String body = mockMvc
			.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.token");
	}

	private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		return mockMvc
			.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	/** Names may only hold letters, so tokens that keep tests apart are letters too. */
	private static String letters() {
		StringBuilder token = new StringBuilder();
		for (int i = 0; i < 10; i++) {
			token.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
		}
		return token.toString();
	}

}
