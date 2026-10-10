package com.peachhacks.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The host lanyard has a colour here and the other one has none, so both answers are seen. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.admin.bootstrap-email=badges@test.local",
		"app.admin.bootstrap-password=correct-horse-battery", "app.admin.bootstrap-name=Test Organizer",
		"app.rate-limit.public-per-minute=100000",
		"app.rate-limit.sign-up-per-window=100000", "app.rate-limit.sign-up-global-per-hour=100000",
		"app.badges.lanyard-host-color=Peach" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BadgeApiTests {

	private static final String ADMIN_EMAIL = "badges@test.local";

	private static final String ADMIN_PASSWORD = "correct-horse-battery";

	private static final String PASSWORD = "a-long-test-password";

	private static final String HOST_SCHOOL = "Georgia State University Perimeter College";

	private static final Pattern PASSWORD_LINK = Pattern.compile("/#/set-password\\?token=([A-Za-z0-9_-]+)");

	private static final List<EmailMessage> sentEmails = new CopyOnWriteArrayList<>();

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingEmail {

		@Bean
		@Primary
		EmailSender recordingEmailSender() {
			return sentEmails::add;
		}

	}

	private record Account(String id, String email, String token) {
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void bindingABadgeChecksThePersonInAndSaysWhichLanyard() throws Exception {
		String admin = bearer();
		Account volunteer = createAccount(admin, "Desk Volunteer", "VOLUNTEER");
		String host = acceptedHacker(admin, HOST_SCHOOL);
		String other = acceptedHacker(admin, uniqueSchool());
		String hostCard = uid();
		String otherCard = uid();

		bind(volunteer.token(), host, hostCard, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("BOUND"))
			.andExpect(jsonPath("$.item.id").value(host))
			.andExpect(jsonPath("$.item.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(true))
			.andExpect(jsonPath("$.item.checkedInAt").isNotEmpty())
			.andExpect(jsonPath("$.item.checkedInBy").value("Desk Volunteer"))
			.andExpect(jsonPath("$.badge.uid").value(hostCard))
			.andExpect(jsonPath("$.badge.boundAt").isNotEmpty())
			.andExpect(jsonPath("$.badge.boundBy").value("Desk Volunteer"))
			.andExpect(jsonPath("$.lanyard.group").value("HOST"))
			.andExpect(jsonPath("$.lanyard.label").value("Georgia State University hacker"))
			.andExpect(jsonPath("$.lanyard.color").value("Peach"));
		bind(volunteer.token(), other, otherCard, false).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("BOUND"))
			.andExpect(jsonPath("$.lanyard.group").value("OTHER"))
			.andExpect(jsonPath("$.lanyard.label").value("Hacker from another school"))
			.andExpect(jsonPath("$.lanyard.color").value(nullValue()));

		assertThat(generalCheckIns(host)).isEqualTo(1);
		assertThat(generalCheckIns(other)).isEqualTo(1);
		mockMvc.perform(get("/admin/registrations/" + host).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badge.uid").value(hostCard))
			.andExpect(jsonPath("$.badge.boundBy").value("Desk Volunteer"))
			.andExpect(jsonPath("$.badge.boundAt").isNotEmpty())
			.andExpect(jsonPath("$.checkedInBy").value("Desk Volunteer"));
		String unbound = acceptedHacker(admin, uniqueSchool());
		mockMvc.perform(get("/admin/registrations/" + unbound).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badge").value(nullValue()));

		bind(volunteer.token(), UUID.randomUUID().toString(), uid(), null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		json(post("/admin/badges/bind"), volunteer.token(), "{\"uid\":\"%s\"}".formatted(uid()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.registrationId").isNotEmpty());
		mockMvc
			.perform(post("/admin/badges/bind").contentType(MediaType.APPLICATION_JSON)
				.content("{\"registrationId\":\"%s\",\"uid\":\"%s\"}".formatted(unbound, uid())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aBadgeIsNotBoundToSomeoneWhoIsNotAccepted() throws Exception {
		String admin = bearer();
		String pending = hacker(admin, uniqueSchool());
		String card = uid();

		bind(admin, pending, card, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_ACCEPTED"))
			.andExpect(jsonPath("$.message").value("This person has not been accepted, so they can't be checked in."
					+ " An organizer has to accept them first."));
		bind(admin, pending, card, true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_ACCEPTED"));

		assertThat(badgeRows(pending)).isZero();
		assertThat(checkIns(pending)).isZero();
		tap(admin, card, null).andExpect(jsonPath("$.result").value("UNKNOWN_BADGE"));
	}

	@Test
	void aUidIsTheSameCardHoweverItIsWritten() throws Exception {
		String admin = bearer();
		String hacker = acceptedHacker(admin, uniqueSchool());
		String card = uid();
		String colons = String.join(":", card.split("(?<=\\G..)"));
		String dashes = String.join("-", card.split("(?<=\\G..)")).toLowerCase();

		bind(admin, hacker, colons, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("BOUND"))
			.andExpect(jsonPath("$.badge.uid").value(card));
		for (String spelling : List.of(dashes, card.toLowerCase(), "  " + colons.replace(":", " : ") + " ", card)) {
			bind(admin, hacker, spelling, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("ALREADY_BOUND"))
				.andExpect(jsonPath("$.badge.uid").value(card));
			lookup(admin, spelling).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("FOUND"));
			tap(admin, spelling, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"));
		}
		assertThat(badgeRows(hacker)).isEqualTo(1);
		assertThat(jdbc.sql("select uid from badges where registration_id = :id")
			.param("id", UUID.fromString(hacker))
			.query(String.class)
			.single()).isEqualTo(card);

		String fourBytes = uid().substring(0, 8);
		String tenBytes = uid() + uid().substring(0, 6);
		lookup(admin, fourBytes).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("UNKNOWN_BADGE"));
		lookup(admin, tenBytes).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("UNKNOWN_BADGE"));

		String other = acceptedHacker(admin, uniqueSchool());
		for (String bad : List.of("", "   ", "04A1", "04A1B2C3D4E5F", "04A1B2C3D4E5F6A", "04A1B2C3D4E5F6A7B8C9D0E1",
				"ZZA1B2C3D4E5F6", "04_A1_B2_C3_D4_E5_F6", "04A1B2C3D4E5F6".repeat(10))) {
			for (ResultActions answer : List.of(bind(admin, other, bad, null), tap(admin, bad, null),
					lookup(admin, bad))) {
				answer.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.fieldErrors.uid").isNotEmpty());
			}
		}
		json(post("/admin/badges/lookup"), admin, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.uid").isNotEmpty());
		json(post("/admin/badges/tap"), admin, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.uid").isNotEmpty());
		assertThat(badgeRows(other)).isZero();
		assertThat(checkIns(other)).isZero();
	}

	@Test
	void aCardBelongsToOnePersonAndAPersonHasOneCardUntilItIsReplaced() throws Exception {
		String admin = bearer();
		Account volunteer = createAccount(admin, "Desk Volunteer", "VOLUNTEER");
		String ada = acceptedHacker(admin, uniqueSchool());
		String grace = acceptedHacker(admin, uniqueSchool());
		String first = uid();
		String second = uid();

		String bound = bind(volunteer.token(), ada, first, null).andExpect(jsonPath("$.result").value("BOUND"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		bind(admin, ada, first, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("ALREADY_BOUND"))
			.andExpect(jsonPath("$.badge.boundAt").value((String) JsonPath.read(bound, "$.badge.boundAt")))
			.andExpect(jsonPath("$.badge.boundBy").value("Desk Volunteer"))
			.andExpect(jsonPath("$.item.checkedInAt").value((String) JsonPath.read(bound, "$.item.checkedInAt")))
			.andExpect(jsonPath("$.item.checkedInBy").value("Desk Volunteer"))
			.andExpect(jsonPath("$.lanyard.group").value("OTHER"));
		assertThat(badgeRows(ada)).isEqualTo(1);
		assertThat(generalCheckIns(ada)).isEqualTo(1);

		jdbc.sql("delete from check_ins where registration_id = :id").param("id", UUID.fromString(ada)).update();
		bind(admin, ada, first, null).andExpect(jsonPath("$.result").value("ALREADY_BOUND"))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(true))
			.andExpect(jsonPath("$.item.checkedInBy").value("Test Organizer"));
		assertThat(generalCheckIns(ada)).isEqualTo(1);

		for (Boolean replace : new Boolean[] { null, false, true }) {
			bind(volunteer.token(), grace, first, replace).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BADGE_IN_USE"))
				.andExpect(jsonPath("$.message").value("This badge already belongs to someone else. Use a different"
						+ " badge, or ask an organizer to revoke it."));
		}
		assertThat(badgeRows(grace)).isZero();
		assertThat(checkIns(grace)).isZero();

		for (Boolean replace : new Boolean[] { null, false }) {
			bind(volunteer.token(), ada, second, replace).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("HAS_BADGE"))
				.andExpect(jsonPath("$.message")
					.value("This person already has a badge. Replace it only if the old one is lost."));
		}
		assertThat(activeUid(ada)).isEqualTo(first);
		String hasBadgeOnly = acceptedHacker(admin, uniqueSchool());
		bind(admin, hasBadgeOnly, uid(), null).andExpect(jsonPath("$.result").value("BOUND"));
		jdbc.sql("delete from check_ins where registration_id = :id")
			.param("id", UUID.fromString(hasBadgeOnly))
			.update();
		bind(admin, hasBadgeOnly, uid(), null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HAS_BADGE"));
		assertThat(checkIns(hasBadgeOnly)).as("a refused bind records no check-in either").isZero();

		bind(volunteer.token(), ada, second, true).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("REPLACED"))
			.andExpect(jsonPath("$.badge.uid").value(second))
			.andExpect(jsonPath("$.item.id").value(ada));
		assertThat(activeUid(ada)).isEqualTo(second);
		assertThat(badgeRows(ada)).as("the revoked badge is kept as history").isEqualTo(2);
		assertThat(jdbc.sql("select revoked_by from badges where uid = :uid")
			.param("uid", first)
			.query(String.class)
			.single()).isEqualTo("Desk Volunteer");

		tap(volunteer.token(), first, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("REVOKED_BADGE"))
			.andExpect(jsonPath("$.event.general").value(true))
			.andExpect(jsonPath("$.item").value(nullValue()));
		lookup(volunteer.token(), first).andExpect(jsonPath("$.result").value("REVOKED_BADGE"))
			.andExpect(jsonPath("$.holder").value(nullValue()));
		tap(volunteer.token(), second, null).andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
			.andExpect(jsonPath("$.item.id").value(ada));

		bind(volunteer.token(), grace, first, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("BOUND"));
		tap(volunteer.token(), first, null).andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
			.andExpect(jsonPath("$.item.id").value(grace));
	}

	@Test
	void onlyAnAdminRevokesABadge() throws Exception {
		String admin = bearer();
		Account volunteer = createAccount(admin, "Desk Volunteer", "VOLUNTEER");
		String hacker = acceptedHacker(admin, uniqueSchool());
		String card = uid();
		bind(volunteer.token(), hacker, card, null).andExpect(jsonPath("$.result").value("BOUND"));

		mockMvc.perform(delete("/admin/registrations/" + hacker + "/badge").header("Authorization", volunteer.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		assertThat(activeUid(hacker)).isEqualTo(card);

		mockMvc.perform(delete("/admin/registrations/" + hacker + "/badge").header("Authorization", admin))
			.andExpect(status().isNoContent());
		assertThat(activeUid(hacker)).isNull();
		assertThat(jdbc.sql("select revoked_by from badges where uid = :uid")
			.param("uid", card)
			.query(String.class)
			.single()).isEqualTo("Test Organizer");
		assertThat(generalCheckIns(hacker)).as("revoking a badge is not an undo of the check-in").isEqualTo(1);
		mockMvc.perform(get("/admin/registrations/" + hacker).header("Authorization", admin))
			.andExpect(jsonPath("$.badge").value(nullValue()))
			.andExpect(jsonPath("$.checkedInAt").isNotEmpty());
		tap(volunteer.token(), card, null).andExpect(jsonPath("$.result").value("REVOKED_BADGE"));

		mockMvc.perform(delete("/admin/registrations/" + hacker + "/badge").header("Authorization", admin))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(delete("/admin/registrations/" + UUID.randomUUID() + "/badge").header("Authorization", admin))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void aTapChecksInOncePerEventAndKeepsTheFirstTimeAndName() throws Exception {
		String admin = bearer();
		Account desk = createAccount(admin, "Desk Volunteer", "VOLUNTEER");
		Account meals = createAccount(admin, "Meal Volunteer", "VOLUNTEER");
		String hacker = acceptedHacker(admin, uniqueSchool());
		String card = uid();
		String lunch = createEvent(admin, "Lunch " + unique());
		String workshop = createEvent(admin, "Workshop " + unique());

		String bound = bind(desk.token(), hacker, card, null).andReturn().getResponse().getContentAsString();
		tap(meals.token(), card, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
			.andExpect(jsonPath("$.event.general").value(true))
			.andExpect(jsonPath("$.event.name").value("General check-in"))
			.andExpect(jsonPath("$.item.checkedInAt").value((String) JsonPath.read(bound, "$.item.checkedInAt")))
			.andExpect(jsonPath("$.item.checkedInBy").value("Desk Volunteer"));

		String first = tap(meals.token(), card, lunch).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("CHECKED_IN"))
			.andExpect(jsonPath("$.event.id").value(lunch))
			.andExpect(jsonPath("$.event.general").value(false))
			.andExpect(jsonPath("$.item.id").value(hacker))
			.andExpect(jsonPath("$.item.firstName").value("Ada"))
			.andExpect(jsonPath("$.item.checkedInBy").value("Meal Volunteer"))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		for (String caller : List.of(admin, desk.token(), meals.token())) {
			tap(caller, card, lunch)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
				.andExpect(jsonPath("$.item.checkedInAt").value((String) JsonPath.read(first, "$.item.checkedInAt")))
				.andExpect(jsonPath("$.item.checkedInBy").value("Meal Volunteer"));
		}
		assertThat(eventCheckIns(hacker, lunch)).isEqualTo(1);

		tap(desk.token(), card, workshop).andExpect(jsonPath("$.result").value("CHECKED_IN"))
			.andExpect(jsonPath("$.event.id").value(workshop))
			.andExpect(jsonPath("$.item.checkedInBy").value("Desk Volunteer"));
		assertThat(checkIns(hacker)).isEqualTo(3);

		tap(desk.token(), uid(), lunch).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("UNKNOWN_BADGE"))
			.andExpect(jsonPath("$.event.id").value(lunch))
			.andExpect(jsonPath("$.item").value(nullValue()));
		tap(desk.token(), card, UUID.randomUUID().toString()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		String later = createEvent(admin, "Dinner " + unique());
		setStatus(admin, hacker, "WAITLISTED");
		tap(meals.token(), card, later).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("NOT_ACCEPTED"))
			.andExpect(jsonPath("$.item.id").value(hacker))
			.andExpect(jsonPath("$.item.status").value("WAITLISTED"))
			.andExpect(jsonPath("$.item.checkedInAt").value(nullValue()));
		tap(meals.token(), card, lunch).andExpect(jsonPath("$.result").value("NOT_ACCEPTED"));
		assertThat(eventCheckIns(hacker, later)).isZero();
		assertThat(checkIns(hacker)).isEqualTo(3);
		lookup(meals.token(), card).andExpect(jsonPath("$.result").value("FOUND"))
			.andExpect(jsonPath("$.holder.accepted").value(false))
			.andExpect(jsonPath("$.holder.checkedIn").value(true));
	}

	@Test
	void resolvingATicketOrARegistrationRecordsNothing() throws Exception {
		String admin = bearer();
		Account volunteer = createAccount(admin, "Desk Volunteer", "VOLUNTEER");
		String accepted = acceptedHacker(admin, HOST_SCHOOL);
		String pending = hacker(admin, uniqueSchool());
		String token = ticketToken(accepted);

		for (String body : List.of("{\"code\":\"%s\"}".formatted(token),
				"{\"code\":\"  https://www.peachhacks.com/ticket?utm=x&t=%s#top \"}".formatted(token),
				"{\"registrationId\":\"%s\"}".formatted(accepted),
				"{\"code\":\"  \",\"registrationId\":\"%s\"}".formatted(accepted))) {
			json(post("/admin/badges/resolve"), volunteer.token(), body).andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("FOUND"))
				.andExpect(jsonPath("$.item.id").value(accepted))
				.andExpect(jsonPath("$.item.firstName").value("Ada"))
				.andExpect(jsonPath("$.item.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.item.checkedInAt").value(nullValue()))
				.andExpect(jsonPath("$.item.generalCheckedIn").value(false))
				.andExpect(jsonPath("$.badge").value(nullValue()))
				.andExpect(jsonPath("$.lanyard.group").value("HOST"))
				.andExpect(jsonPath("$.lanyard.color").value("Peach"));
		}
		for (String body : List.of("{\"code\":\"%s\"}".formatted(ticketToken(pending)),
				"{\"registrationId\":\"%s\"}".formatted(pending))) {
			json(post("/admin/badges/resolve"), volunteer.token(), body).andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("NOT_ACCEPTED"))
				.andExpect(jsonPath("$.item.id").value(pending))
				.andExpect(jsonPath("$.item.status").value("PENDING"))
				.andExpect(jsonPath("$.badge").value(nullValue()))
				.andExpect(jsonPath("$.lanyard").value(nullValue()));
		}
		for (String body : List.of("{\"code\":\"%s\"}".formatted("x".repeat(40)), "{\"code\":\"not a ticket\"}",
				"{\"code\":\"%s\"}".formatted(uid()), "{\"registrationId\":\"%s\"}".formatted(UUID.randomUUID()))) {
			json(post("/admin/badges/resolve"), volunteer.token(), body).andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("NOT_RECOGNISED"))
				.andExpect(jsonPath("$.item").value(nullValue()))
				.andExpect(jsonPath("$.badge").value(nullValue()))
				.andExpect(jsonPath("$.lanyard").value(nullValue()));
		}
		for (String body : List.of("{}", "{\"code\":\"\"}",
				"{\"code\":\"%s\",\"registrationId\":\"%s\"}".formatted(token, accepted),
				"{\"code\":\"%s\"}".formatted("x".repeat(2049)))) {
			json(post("/admin/badges/resolve"), volunteer.token(), body).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertThat(checkIns(accepted)).isZero();
		assertThat(badgeRows(accepted)).isZero();
		assertThat(checkIns(pending)).isZero();

		String card = uid();
		bind(volunteer.token(), accepted, card, null).andExpect(jsonPath("$.result").value("BOUND"));
		json(post("/admin/badges/resolve"), volunteer.token(), "{\"code\":\"%s\"}".formatted(token))
			.andExpect(jsonPath("$.result").value("FOUND"))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(true))
			.andExpect(jsonPath("$.item.checkedInBy").value("Desk Volunteer"))
			.andExpect(jsonPath("$.badge.uid").value(card))
			.andExpect(jsonPath("$.badge.boundBy").value("Desk Volunteer"));
		setStatus(admin, accepted, "REJECTED");
		json(post("/admin/badges/resolve"), volunteer.token(), "{\"registrationId\":\"%s\"}".formatted(accepted))
			.andExpect(jsonPath("$.result").value("NOT_ACCEPTED"))
			.andExpect(jsonPath("$.badge.uid").value(card))
			.andExpect(jsonPath("$.lanyard").value(nullValue()));
	}

	@Test
	void aVolunteerWorksTheDeskAndNothingElse() throws Exception {
		String admin = bearer();
		Account volunteer = createAccount(admin, "Desk Volunteer", "VOLUNTEER");
		String hacker = acceptedHacker(admin, uniqueSchool());
		String card = uid();

		json(post("/admin/badges/resolve"), volunteer.token(), "{\"registrationId\":\"%s\"}".formatted(hacker))
			.andExpect(status().isOk());
		bind(volunteer.token(), hacker, card, null).andExpect(status().isOk());
		tap(volunteer.token(), card, null).andExpect(status().isOk());
		lookup(volunteer.token(), card).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("FOUND"));

		MockHttpServletRequestBuilder[] adminOnly = { delete("/admin/registrations/" + hacker + "/badge"),
				get("/admin/registrations/" + hacker), get("/admin/registrations"), get("/admin/stats"),
				get("/admin/settings"),
				put("/admin/settings").contentType(MediaType.APPLICATION_JSON)
					.content("{\"webCheckInAdminOnly\":false}"),
				get("/admin/badges"), get("/admin/badges/lookup"), post("/admin/badges/a-route-added-later"),
				delete("/admin/badges/" + card) };
		for (MockHttpServletRequestBuilder request : adminOnly) {
			mockMvc.perform(request.header("Authorization", volunteer.token()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		assertThat(activeUid(hacker)).isEqualTo(card);
	}

	@Test
	void aLookupAccountSeesWhoABadgeBelongsToAndNothingElse() throws Exception {
		String admin = bearer();
		Account lookup = createAccount(admin, "Venue Staff", "LOOKUP");
		EmailMessage invite = awaitEmail(lookup.email(), "badge lookup staff");
		assertThat(invite.subject()).isEqualTo("You've been added as PeachHacks badge lookup staff");
		assertThat(invite.text()).contains("Test Organizer added you as badge lookup staff for PeachHacks.")
			.contains("tap a hacker's badge in the PeachHacks staff app to see who it belongs to")
			.contains("it cannot check anyone in")
			.contains("Set your password: http://localhost:5174/#/set-password?token=")
			.doesNotContain("check-in volunteer");
		mockMvc.perform(get("/admin/admins").header("Authorization", admin))
			.andExpect(jsonPath("$[?(@.id == '%s')].role".formatted(lookup.id())).value("LOOKUP"));

		String hacker = acceptedHacker(admin, HOST_SCHOOL);
		String waitlisted = acceptedHacker(admin, uniqueSchool());
		String card = uid();
		String waitlistedCard = uid();
		String never = uid();
		bind(admin, hacker, card, null).andExpect(jsonPath("$.result").value("BOUND"));
		bind(admin, waitlisted, waitlistedCard, null).andExpect(jsonPath("$.result").value("BOUND"));
		setStatus(admin, waitlisted, "WAITLISTED");
		String generalId = jdbc.sql("select id from events where general").query(UUID.class).single().toString();
		long checkInsBefore = count("select count(*) from check_ins");
		long badgesBefore = count("select count(*) from badges");

		mockMvc.perform(get("/admin/auth/me").header("Authorization", lookup.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(lookup.email()))
			.andExpect(jsonPath("$.name").value("Venue Staff"))
			.andExpect(jsonPath("$.role").value("LOOKUP"));

		String found = lookup(lookup.token(), card).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("FOUND"))
			.andExpect(jsonPath("$.holder.firstName").value("Ada"))
			.andExpect(jsonPath("$.holder.lastName").value("Lovelace"))
			.andExpect(jsonPath("$.holder.school").value(HOST_SCHOOL))
			.andExpect(jsonPath("$.holder.accepted").value(true))
			.andExpect(jsonPath("$.holder.checkedIn").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Map<String, Object> answer = JsonPath.read(found, "$");
		Map<String, Object> holder = JsonPath.read(found, "$.holder");
		assertThat(answer.keySet()).containsExactlyInAnyOrder("result", "holder");
		assertThat(holder.keySet()).containsExactlyInAnyOrder("firstName", "lastName", "school", "accepted",
				"checkedIn");
		assertThat(found).doesNotContain(hacker)
			.doesNotContain("@")
			.doesNotContain("\"id\"")
			.doesNotContain("email")
			.doesNotContain(card);
		lookup(lookup.token(), waitlistedCard).andExpect(jsonPath("$.result").value("FOUND"))
			.andExpect(jsonPath("$.holder.accepted").value(false))
			.andExpect(jsonPath("$.holder.checkedIn").value(true));
		lookup(lookup.token(), never).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("UNKNOWN_BADGE"))
			.andExpect(jsonPath("$.holder").value(nullValue()));
		lookup(lookup.token(), "nonsense").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.uid").isNotEmpty());
		mockMvc
			.perform(post("/admin/badges/lookup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"uid\":\"%s\"}".formatted(card)))
			.andExpect(status().isUnauthorized());

		String anotherHacker = acceptedHacker(admin, uniqueSchool());
		MockHttpServletRequestBuilder[] forbidden = {
				post("/admin/badges/resolve").contentType(MediaType.APPLICATION_JSON)
					.content("{\"registrationId\":\"%s\"}".formatted(hacker)),
				post("/admin/badges/bind").contentType(MediaType.APPLICATION_JSON)
					.content("{\"registrationId\":\"%s\",\"uid\":\"%s\"}".formatted(anotherHacker, never)),
				post("/admin/badges/tap").contentType(MediaType.APPLICATION_JSON)
					.content("{\"uid\":\"%s\"}".formatted(card)),
				get("/admin/check-in"), get("/admin/check-in").param("q", "Ada"),
				post("/admin/check-in/scan").contentType(MediaType.APPLICATION_JSON)
					.content("{\"code\":\"%s\"}".formatted(ticketToken(anotherHacker))),
				post("/admin/check-in/" + anotherHacker), delete("/admin/check-in/" + hacker),
				get("/admin/events"),
				post("/admin/events").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sneaky\"}"),
				get("/admin/events/" + generalId + "/export.csv"), get("/admin/registrations"),
				get("/admin/registrations/" + hacker), get("/admin/registrations/export.csv"),
				delete("/admin/registrations/" + hacker + "/badge"), get("/admin/stats"), get("/admin/settings"),
				put("/admin/settings").contentType(MediaType.APPLICATION_JSON)
					.content("{\"webCheckInAdminOnly\":true}"),
				get("/admin/admins"), get("/admin/pre-registrations"), get("/admin/acceptances/summary"),
				get("/admin/emails"), get("/admin/badges/lookup"), get("/admin/a-route-added-later") };
		for (MockHttpServletRequestBuilder request : forbidden) {
			mockMvc
				.perform(request.header("Authorization", lookup.token())
					.header("X-PeachHacks-Client", "staff-app"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		assertThat(count("select count(*) from check_ins")).isEqualTo(checkInsBefore);
		assertThat(count("select count(*) from badges")).isEqualTo(badgesBefore);
		assertThat(activeUid(hacker)).isEqualTo(card);
		assertThat(checkIns(anotherHacker)).isZero();

		mockMvc.perform(post("/admin/auth/logout").header("Authorization", lookup.token()))
			.andExpect(status().isNoContent());
		lookup(lookup.token(), card).andExpect(status().isUnauthorized());

		json(post("/admin/admins"), admin,
				"{\"email\":\"%s@test.local\",\"name\":\"Odd\",\"role\":\"OWNER\"}".formatted(unique()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.role").value("Role must be ADMIN, VOLUNTEER or LOOKUP"));
	}

	@Test
	void aBadgeUidIsNotATicketForTheScanner() throws Exception {
		String admin = bearer();
		Account volunteer = createAccount(admin, "Door Volunteer", "VOLUNTEER");
		String hacker = acceptedHacker(admin, uniqueSchool());
		String card = uid();
		String dashes = String.join("-", card.split("(?<=\\G..)")).toLowerCase();
		assertThat(dashes).hasSize(20);
		String workshop = createEvent(admin, "Workshop " + unique());
		bind(volunteer.token(), hacker, card, null).andExpect(jsonPath("$.result").value("BOUND"));
		jdbc.sql("delete from check_ins where registration_id = :id").param("id", UUID.fromString(hacker)).update();

		for (String code : List.of(dashes, card, String.join(":", card.split("(?<=\\G..)")))) {
			for (String eventId : new String[] { null, workshop }) {
				String event = (eventId != null) ? "\"" + eventId + "\"" : "null";
				json(post("/admin/check-in/scan"), volunteer.token(),
						"{\"code\":\"%s\",\"eventId\":%s}".formatted(code, event))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.result").value("NOT_RECOGNISED"))
					.andExpect(jsonPath("$.item").value(nullValue()));
			}
		}
		assertThat(checkIns(hacker)).isZero();

		json(post("/admin/check-in/scan"), volunteer.token(), "{\"code\":\"%s\"}".formatted(ticketToken(hacker)))
			.andExpect(jsonPath("$.result").value("CHECKED_IN"));
	}

	@Test
	void undoingTheGeneralCheckInRevokesTheBadgeAndUndoingAWorkshopDoesNot() throws Exception {
		String admin = bearer();
		Account volunteer = createAccount(admin, "Desk Volunteer", "VOLUNTEER");
		String hacker = acceptedHacker(admin, uniqueSchool());
		String card = uid();
		String workshop = createEvent(admin, "Workshop " + unique());
		String generalId = jdbc.sql("select id from events where general").query(UUID.class).single().toString();
		bind(admin, hacker, card, null).andExpect(jsonPath("$.result").value("BOUND"));
		tap(volunteer.token(), card, workshop).andExpect(jsonPath("$.result").value("CHECKED_IN"));

		mockMvc
			.perform(delete("/admin/check-in/" + hacker).param("eventId", workshop)
				.header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkedInAt").value(nullValue()))
			.andExpect(jsonPath("$.generalCheckedIn").value(true));
		assertThat(activeUid(hacker)).isEqualTo(card);
		lookup(volunteer.token(), card).andExpect(jsonPath("$.result").value("FOUND"));
		tap(volunteer.token(), card, workshop).andExpect(jsonPath("$.result").value("CHECKED_IN"));

		mockMvc.perform(delete("/admin/check-in/" + hacker).header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generalCheckedIn").value(false));
		assertThat(activeUid(hacker)).isNull();
		assertThat(jdbc.sql("select revoked_by from badges where uid = :uid")
			.param("uid", card)
			.query(String.class)
			.single()).isEqualTo("Desk Volunteer");
		lookup(volunteer.token(), card).andExpect(jsonPath("$.result").value("REVOKED_BADGE"));
		tap(volunteer.token(), card, workshop).andExpect(jsonPath("$.result").value("REVOKED_BADGE"));
		assertThat(eventCheckIns(hacker, workshop)).as("the workshop check-in is left alone").isEqualTo(1);

		String wrongPerson = acceptedHacker(admin, uniqueSchool());
		bind(volunteer.token(), wrongPerson, card, null).andExpect(jsonPath("$.result").value("BOUND"));
		mockMvc
			.perform(delete("/admin/check-in/" + wrongPerson).param("eventId", generalId)
				.header("Authorization", volunteer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generalCheckedIn").value(false));
		assertThat(activeUid(wrongPerson)).isNull();
		bind(volunteer.token(), hacker, card, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("BOUND"))
			.andExpect(jsonPath("$.item.generalCheckedIn").value(true));
	}

	@Test
	void webCheckInCanBeKeptForAdminsWhileTheStaffAppKeepsWorking() throws Exception {
		String admin = bearer();
		Account volunteer = createAccount(admin, "Desk Volunteer", "VOLUNTEER");
		String hacker = acceptedHacker(admin, uniqueSchool());
		String card = uid();

		mockMvc.perform(get("/admin/settings").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.webCheckInAdminOnly").value(false))
			.andExpect(jsonPath("$.registrationOpen").value(false));
		mockMvc.perform(get("/admin/check-in").header("Authorization", volunteer.token()))
			.andExpect(status().isOk());

		try {
			json(put("/admin/settings"), admin, "{\"webCheckInAdminOnly\":true}").andExpect(status().isOk())
				.andExpect(jsonPath("$.webCheckInAdminOnly").value(true))
				.andExpect(jsonPath("$.registrationOpen").value(false));

			MockHttpServletRequestBuilder[] checkIn = { get("/admin/check-in"),
					get("/admin/check-in").param("q", "Ada"), post("/admin/check-in/" + hacker),
					delete("/admin/check-in/" + hacker),
					post("/admin/check-in/scan").contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"%s\"}".formatted(ticketToken(hacker))) };
			for (MockHttpServletRequestBuilder request : checkIn) {
				mockMvc.perform(request.header("Authorization", volunteer.token()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("WEB_CHECK_IN_ADMIN_ONLY"))
					.andExpect(jsonPath("$.message").value("Check-in is done in the PeachHacks staff app. Ask an"
							+ " organizer if you need it here."));
			}
			mockMvc
				.perform(get("/admin/check-in").header("Authorization", volunteer.token())
					.header("X-PeachHacks-Client", "web"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("WEB_CHECK_IN_ADMIN_ONLY"));
			assertThat(checkIns(hacker)).isZero();

			mockMvc
				.perform(get("/admin/check-in").header("Authorization", volunteer.token())
					.header("X-PeachHacks-Client", "staff-app"))
				.andExpect(status().isOk());
			mockMvc
				.perform(post("/admin/check-in/" + hacker).header("Authorization", volunteer.token())
					.header("X-PeachHacks-Client", "staff-app"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedInBy").value("Desk Volunteer"));
			mockMvc
				.perform(delete("/admin/check-in/" + hacker).header("Authorization", volunteer.token())
					.header("X-PeachHacks-Client", "staff-app"))
				.andExpect(status().isOk());

			mockMvc.perform(get("/admin/check-in").header("Authorization", admin)).andExpect(status().isOk());
			mockMvc.perform(post("/admin/check-in/" + hacker).header("Authorization", admin))
				.andExpect(status().isOk());
			mockMvc.perform(delete("/admin/check-in/" + hacker).header("Authorization", admin))
				.andExpect(status().isOk());

			json(post("/admin/badges/resolve"), volunteer.token(), "{\"registrationId\":\"%s\"}".formatted(hacker))
				.andExpect(status().isOk());
			bind(volunteer.token(), hacker, card, null).andExpect(status().isOk());
			tap(volunteer.token(), card, null).andExpect(status().isOk());
			lookup(volunteer.token(), card).andExpect(status().isOk());
			mockMvc.perform(get("/admin/events").header("Authorization", volunteer.token()))
				.andExpect(status().isOk());
			mockMvc.perform(get("/admin/auth/me").header("Authorization", volunteer.token()))
				.andExpect(status().isOk());

			json(put("/admin/settings"), admin, "{\"registrationOpen\":true}").andExpect(status().isOk())
				.andExpect(jsonPath("$.registrationOpen").value(true))
				.andExpect(jsonPath("$.webCheckInAdminOnly").value(true));
			json(put("/admin/settings"), admin, "{\"registrationOpen\":false,\"previewActive\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registrationOpen").value(false))
				.andExpect(jsonPath("$.previewActive").value(false))
				.andExpect(jsonPath("$.webCheckInAdminOnly").value(true));
			json(put("/admin/settings"), admin, "{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
			mockMvc.perform(get("/admin/settings").header("Authorization", admin))
				.andExpect(jsonPath("$.webCheckInAdminOnly").value(true));
		}
		finally {
			json(put("/admin/settings"), admin, "{\"webCheckInAdminOnly\":false}").andExpect(status().isOk())
				.andExpect(jsonPath("$.webCheckInAdminOnly").value(false))
				.andExpect(jsonPath("$.registrationOpen").value(false));
		}
		mockMvc.perform(get("/admin/check-in").header("Authorization", volunteer.token()))
			.andExpect(status().isOk());
	}

	@Test
	void twoVolunteersBindingAtTheSameMomentEndInOneWinnerAndOneRefusal() throws Exception {
		String admin = bearer();
		Account one = createAccount(admin, "Desk One", "VOLUNTEER");
		Account two = createAccount(admin, "Desk Two", "VOLUNTEER");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 12; round++) {
				String ada = acceptedHacker(admin, uniqueSchool());
				String grace = acceptedHacker(admin, uniqueSchool());
				String card = uid();
				List<MockHttpServletResponse> sameCard = together(pool,
						() -> bind(one.token(), ada, card, null).andReturn().getResponse(),
						() -> bind(two.token(), grace, card, null).andReturn().getResponse());
				assertThat(sameCard).extracting(MockHttpServletResponse::getStatus)
					.containsExactlyInAnyOrder(200, 409);
				for (MockHttpServletResponse response : sameCard) {
					if (response.getStatus() == 200) {
						assertThat((String) JsonPath.read(response.getContentAsString(), "$.result")).isEqualTo("BOUND");
					}
					else {
						assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
							.isEqualTo("BADGE_IN_USE");
					}
				}
				assertThat(jdbc.sql("select count(*) from badges where uid = :uid")
					.param("uid", card)
					.query(Long.class)
					.single()).isEqualTo(1);
				assertThat(generalCheckIns(ada) + generalCheckIns(grace))
					.as("only the winner was checked in")
					.isEqualTo(1);
				assertThat((activeUid(ada) != null) ? generalCheckIns(ada) : generalCheckIns(grace)).isEqualTo(1);

				String hopper = acceptedHacker(admin, uniqueSchool());
				String first = uid();
				String second = uid();
				List<MockHttpServletResponse> samePerson = together(pool,
						() -> bind(one.token(), hopper, first, null).andReturn().getResponse(),
						() -> bind(two.token(), hopper, second, null).andReturn().getResponse());
				assertThat(samePerson).extracting(MockHttpServletResponse::getStatus)
					.containsExactlyInAnyOrder(200, 409);
				for (MockHttpServletResponse response : samePerson) {
					if (response.getStatus() == 409) {
						assertThat((String) JsonPath.read(response.getContentAsString(), "$.code"))
							.isEqualTo("HAS_BADGE");
					}
				}
				assertThat(badgeRows(hopper)).isEqualTo(1);
				assertThat(generalCheckIns(hopper)).isEqualTo(1);

				List<MockHttpServletResponse> sameBind = together(pool,
						() -> bind(one.token(), hopper, activeUid(hopper), null).andReturn().getResponse(),
						() -> tap(two.token(), activeUid(hopper), null).andReturn().getResponse());
				assertThat(sameBind).extracting(MockHttpServletResponse::getStatus).containsExactly(200, 200);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	private List<MockHttpServletResponse> together(ExecutorService pool, Callable<MockHttpServletResponse> first,
			Callable<MockHttpServletResponse> second) throws Exception {
		CyclicBarrier start = new CyclicBarrier(2);
		List<Future<MockHttpServletResponse>> running = new ArrayList<>();
		for (Callable<MockHttpServletResponse> call : List.of(first, second)) {
			running.add(pool.submit(() -> {
				start.await(10, TimeUnit.SECONDS);
				return call.call();
			}));
		}
		List<MockHttpServletResponse> responses = new ArrayList<>();
		for (Future<MockHttpServletResponse> future : running) {
			responses.add(future.get(30, TimeUnit.SECONDS));
		}
		return responses;
	}

	private ResultActions bind(String token, String registrationId, String uid, Boolean replace) throws Exception {
		String replaceField = (replace != null) ? ",\"replace\":" + replace : "";
		return json(post("/admin/badges/bind"), token,
				"{\"registrationId\":\"%s\",\"uid\":\"%s\"%s}".formatted(registrationId, uid, replaceField));
	}

	private ResultActions tap(String token, String uid, String eventId) throws Exception {
		StringBuilder body = new StringBuilder("{\"uid\":\"").append(uid).append('"');
		if (eventId != null) {
			body.append(",\"eventId\":\"").append(eventId).append('"');
		}
		return json(post("/admin/badges/tap"), token, body.append('}').toString());
	}

	private ResultActions lookup(String token, String uid) throws Exception {
		return json(post("/admin/badges/lookup"), token, "{\"uid\":\"%s\"}".formatted(uid));
	}

	private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		return mockMvc
			.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String bearer() throws Exception {
		return "Bearer " + JsonPath.read(login(ADMIN_EMAIL, ADMIN_PASSWORD), "$.token");
	}

	private String login(String email, String password) throws Exception {
		return mockMvc
			.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private Account createAccount(String adminToken, String name, String role) throws Exception {
		String email = unique() + "@test.local";
		String created = json(post("/admin/admins"), adminToken,
				"{\"email\":\"%s\",\"name\":\"%s\",\"role\":\"%s\"}".formatted(email, name, role))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value(role))
			.andExpect(jsonPath("$.pending").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Matcher link = PASSWORD_LINK.matcher((String) JsonPath.read(created, "$.setPasswordUrl"));
		assertThat(link.find()).isTrue();
		mockMvc
			.perform(post("/admin/auth/set-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(link.group(1), PASSWORD)))
			.andExpect(status().isNoContent());
		String session = login(email, PASSWORD);
		assertThat((String) JsonPath.read(session, "$.admin.role")).isEqualTo(role);
		return new Account(JsonPath.read(created, "$.id"), email, "Bearer " + JsonPath.read(session, "$.token"));
	}

	private String hacker(String adminToken, String school) throws Exception {
		String email = unique() + "@example.com";
		json(put("/admin/settings"), adminToken, "{\"registrationOpen\":true}").andExpect(status().isOk());
		String created = mockMvc
			.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"Ada","lastName":"Lovelace","age":19,"phone":"404 555 0100","email":"%s",
					 "schoolEmail":"%s","school":"%s","levelOfStudy":"Undergraduate University (3+ year)",
					 "graduationYear":2028,"graduationMonth":5,"countryOfResidence":"US",
					 "mlhCodeOfConduct":true,"mlhDataSharing":true,"mlhEmailOptIn":false}
					""".formatted(email, email, school)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		json(put("/admin/settings"), adminToken, "{\"registrationOpen\":false}").andExpect(status().isOk());
		return JsonPath.read(created, "$.id");
	}

	private String acceptedHacker(String adminToken, String school) throws Exception {
		String id = hacker(adminToken, school);
		setStatus(adminToken, id, "ACCEPTED");
		return id;
	}

	private void setStatus(String adminToken, String registrationId, String status) throws Exception {
		json(patch("/admin/registrations/" + registrationId), adminToken, "{\"status\":\"%s\"}".formatted(status))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(status));
	}

	private String createEvent(String adminToken, String name) throws Exception {
		String created = json(post("/admin/events"), adminToken, "{\"name\":\"%s\"}".formatted(name))
			.andExpect(status().is2xxSuccessful())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(created, "$.id");
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

	private String ticketToken(String registrationId) {
		return jdbc.sql("select ticket_token from registrations where id = :id")
			.param("id", UUID.fromString(registrationId))
			.query(String.class)
			.single();
	}

	private String activeUid(String registrationId) {
		return jdbc.sql("select uid from badges where registration_id = :id and revoked_at is null")
			.param("id", UUID.fromString(registrationId))
			.query(String.class)
			.optional()
			.orElse(null);
	}

	private long badgeRows(String registrationId) {
		return jdbc.sql("select count(*) from badges where registration_id = :id")
			.param("id", UUID.fromString(registrationId))
			.query(Long.class)
			.single();
	}

	private long checkIns(String registrationId) {
		return jdbc.sql("select count(*) from check_ins where registration_id = :id")
			.param("id", UUID.fromString(registrationId))
			.query(Long.class)
			.single();
	}

	private long generalCheckIns(String registrationId) {
		return jdbc.sql("""
				select count(*) from check_ins c join events e on e.id = c.event_id
				where e.general and c.registration_id = :id
				""").param("id", UUID.fromString(registrationId)).query(Long.class).single();
	}

	private long eventCheckIns(String registrationId, String eventId) {
		return jdbc.sql("select count(*) from check_ins where registration_id = :id and event_id = :eventId")
			.param("id", UUID.fromString(registrationId))
			.param("eventId", UUID.fromString(eventId))
			.query(Long.class)
			.single();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private static String uid() {
		byte[] bytes = new byte[7];
		ThreadLocalRandom.current().nextBytes(bytes);
		StringBuilder hex = new StringBuilder();
		for (byte value : bytes) {
			hex.append(String.format("%02X", value));
		}
		return hex.toString();
	}

	private static String unique() {
		return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	private static String uniqueSchool() {
		return "School " + unique();
	}

}
