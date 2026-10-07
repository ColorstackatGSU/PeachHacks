package com.peachhacks.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import com.peachhacks.backend.email.EmailMessage;
import com.peachhacks.backend.email.EmailSender;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The summary counts every registration in the database, so this class has its own
 * application context (and with it its own empty Postgres container) and clears the
 * table before each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.admin.bootstrap-email=acceptances@test.local",
		"app.admin.bootstrap-password=correct-horse-battery", "app.admin.bootstrap-name=Test Organizer",
		"app.rate-limit.public-per-minute=100000", "app.rate-limit.login-per-minute=100000", "app.rate-limit.sign-up-per-window=100000",
		"app.rate-limit.sign-up-global-per-hour=100000",
		"app.email.campaign-delay=0ms" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AcceptanceApiTests {

	private static final String GSU = "Georgia State University";

	private static final String PERIMETER = "Georgia State University Perimeter College";

	private static final String TECH = "Georgia Institute of Technology";

	private static final List<EmailMessage> sentEmails = new CopyOnWriteArrayList<>();

	private static final Set<String> rejectedByProvider = ConcurrentHashMap.newKeySet();

	private static volatile CountDownLatch providerGate;

	private static volatile CountDownLatch providerReached;

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingEmail {

		@Bean
		@Primary
		EmailSender recordingEmailSender() {
			return message -> {
				CountDownLatch gate = providerGate;
				if (gate != null && isTicket(message)) {
					providerReached.countDown();
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
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private DataSource dataSource;

	private String admin;

	@BeforeEach
	void startEmpty() throws Exception {
		providerGate = null;
		rejectedByProvider.clear();
		String login = mockMvc
			.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"acceptances@test.local\",\"password\":\"correct-horse-battery\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		admin = "Bearer " + JsonPath.read(login, "$.token");
		awaitIdle();
		sentEmails.clear();
		jdbc.sql("delete from registrations").update();
		mockMvc
			.perform(put("/admin/settings").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"registrationOpen\":true}"))
			.andExpect(status().isOk());
	}

	@Test
	void acceptingSendsNoEmailAndPutsThePersonInTheBucket() throws Exception {
		Hacker ada = register("Ada", GSU);
		Hacker grace = register("Grace", TECH);
		register("Linus", TECH);

		summary().andExpect(jsonPath("$.totals.registrations").value(3))
			.andExpect(jsonPath("$.totals.accepted").value(0))
			.andExpect(jsonPath("$.totals.pending").value(3))
			.andExpect(jsonPath("$.totals.acceptanceRate").value(0.0))
			.andExpect(jsonPath("$.hostSchool.name").value(GSU))
			.andExpect(jsonPath("$.hostSchool.target").value(0.70))
			.andExpect(jsonPath("$.send.state").value("IDLE"));

		setStatus(ada.id(), "ACCEPTED").andExpect(jsonPath("$.acceptedAt").isNotEmpty())
			.andExpect(jsonPath("$.acceptanceNotifiedAt").value(nullValue()))
			.andExpect(jsonPath("$.ticketUrl").isNotEmpty());
		setStatus(grace.id(), "ACCEPTED");
		setStatus(grace.id(), "ACCEPTED");

		Thread.sleep(400);
		assertThat(ticketEmails()).as("accepting must not email anyone").isEmpty();
		summary().andExpect(jsonPath("$.totals.accepted").value(2))
			.andExpect(jsonPath("$.totals.acceptedWaiting").value(2))
			.andExpect(jsonPath("$.totals.acceptedNotified").value(0))
			.andExpect(jsonPath("$.totals.pending").value(1))
			.andExpect(jsonPath("$.totals.waitlisted").value(0))
			.andExpect(jsonPath("$.totals.rejected").value(0))
			.andExpect(jsonPath("$.totals.acceptanceRate").value(closeTo(2.0 / 3, 1e-9)));
		mockMvc.perform(get("/admin/acceptances/waiting").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].id").value(ada.id()))
			.andExpect(jsonPath("$[0].firstName").value("Ada"))
			.andExpect(jsonPath("$[0].email").value(ada.email()))
			.andExpect(jsonPath("$[0].school").value(GSU))
			.andExpect(jsonPath("$[0].host").value(true))
			.andExpect(jsonPath("$[0].acceptedAt").isNotEmpty())
			.andExpect(jsonPath("$[1].host").value(false));
		mockMvc.perform(get("/admin/registrations").param("status", "ACCEPTED").header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.items[0].acceptedAt").isNotEmpty())
			.andExpect(jsonPath("$.items[0].acceptanceNotifiedAt").value(nullValue()));

		setStatus(grace.id(), "WAITLISTED").andExpect(jsonPath("$.acceptedAt").value(nullValue()));
		summary().andExpect(jsonPath("$.totals.accepted").value(1))
			.andExpect(jsonPath("$.totals.acceptedWaiting").value(1))
			.andExpect(jsonPath("$.totals.waitlisted").value(1));
		mockMvc.perform(get("/admin/acceptances/waiting").header("Authorization", admin))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(ada.id()));
		send().andExpect(status().isAccepted()).andExpect(jsonPath("$.queued").value(1));
		awaitIdle();
		assertThat(ticketEmails(grace)).as("removed from the bucket before the send").isEmpty();
		assertThat(ticketEmails(ada)).hasSize(1);
	}

	@Test
	void sendAllEmailsEveryoneWaitingExactlyOnceAndMarksThem() throws Exception {
		Hacker ada = register("Ada", GSU);
		Hacker grace = register("Grace", PERIMETER);
		Hacker linus = register("Linus", TECH);
		Hacker pending = register("Pending", TECH);
		for (Hacker hacker : List.of(ada, grace, linus)) {
			setStatus(hacker.id(), "ACCEPTED");
		}

		send().andExpect(status().isAccepted())
			.andExpect(jsonPath("$.queued").value(3))
			.andExpect(jsonPath("$.send.state").value("SENDING"))
			.andExpect(jsonPath("$.send.startedBy").value("acceptances@test.local"));
		awaitIdle();

		summary().andExpect(jsonPath("$.send.state").value("IDLE"))
			.andExpect(jsonPath("$.send.queued").value(3))
			.andExpect(jsonPath("$.send.sent").value(3))
			.andExpect(jsonPath("$.send.failed").value(0))
			.andExpect(jsonPath("$.send.skipped").value(0))
			.andExpect(jsonPath("$.send.startedAt").isNotEmpty())
			.andExpect(jsonPath("$.send.finishedAt").isNotEmpty());
		mockMvc.perform(get("/admin/acceptances/send").header("Authorization", admin))
			.andExpect(status().isMethodNotAllowed());
		for (Hacker hacker : List.of(ada, grace, linus)) {
			List<EmailMessage> emails = ticketEmails(hacker);
			assertThat(emails).as(hacker.email()).hasSize(1);
			assertThat(emails.get(0).subject()).contains("You're in");
			assertThat(emails.get(0).text()).contains("/ticket?t=");
			assertThat(emails.get(0).idempotencyKey()).as("the provider can tell a repeat of this acceptance")
				.matches("acceptance-" + hacker.id() + "-\\d+");
			mockMvc.perform(get("/admin/registrations/" + hacker.id()).header("Authorization", admin))
				.andExpect(jsonPath("$.acceptanceNotifiedAt").isNotEmpty());
		}
		assertThat(ticketEmails(pending)).isEmpty();
		summary().andExpect(jsonPath("$.totals.accepted").value(3))
			.andExpect(jsonPath("$.totals.acceptedNotified").value(3))
			.andExpect(jsonPath("$.totals.acceptedWaiting").value(0))
			.andExpect(jsonPath("$.send.sent").value(3));
		mockMvc.perform(get("/admin/acceptances/waiting").header("Authorization", admin))
			.andExpect(jsonPath("$", hasSize(0)));

		send().andExpect(status().isOk())
			.andExpect(jsonPath("$.queued").value(0))
			.andExpect(jsonPath("$.send.state").value("IDLE"))
			.andExpect(jsonPath("$.send.sent").value(3));
		Thread.sleep(300);
		assertThat(ticketEmails()).as("a second send with nobody waiting sends nothing").hasSize(3);
	}

	@Test
	void aProviderFailureLeavesThatPersonWaitingAndTheOthersNotified() throws Exception {
		Hacker ada = register("Ada", GSU);
		Hacker grace = register("Grace", GSU);
		Hacker linus = register("Linus", TECH);
		for (Hacker hacker : List.of(ada, grace, linus)) {
			setStatus(hacker.id(), "ACCEPTED");
		}
		rejectedByProvider.add(grace.email());

		send().andExpect(status().isAccepted()).andExpect(jsonPath("$.queued").value(3));
		awaitIdle();

		summary().andExpect(jsonPath("$.send.sent").value(2))
			.andExpect(jsonPath("$.send.failed").value(1))
			.andExpect(jsonPath("$.totals.acceptedNotified").value(2))
			.andExpect(jsonPath("$.totals.acceptedWaiting").value(1));
		mockMvc.perform(get("/admin/acceptances/waiting").header("Authorization", admin))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(grace.id()));
		assertThat(ticketEmails(grace)).isEmpty();

		rejectedByProvider.clear();
		send().andExpect(status().isAccepted()).andExpect(jsonPath("$.queued").value(1));
		awaitIdle();

		for (Hacker hacker : List.of(ada, grace, linus)) {
			assertThat(ticketEmails(hacker)).as(hacker.email()).hasSize(1);
		}
		summary().andExpect(jsonPath("$.totals.acceptedWaiting").value(0))
			.andExpect(jsonPath("$.send.queued").value(1))
			.andExpect(jsonPath("$.send.sent").value(1))
			.andExpect(jsonPath("$.send.failed").value(0));
	}

	@Test
	void startingASendWhileOneIsRunningIsRefusedAndNobodyIsMailedTwice() throws Exception {
		Hacker ada = register("Ada", GSU);
		Hacker grace = register("Grace", GSU);
		setStatus(ada.id(), "ACCEPTED");
		setStatus(grace.id(), "ACCEPTED");
		providerReached = new CountDownLatch(1);
		CountDownLatch gate = new CountDownLatch(1);
		providerGate = gate;

		try {
			send().andExpect(status().isAccepted()).andExpect(jsonPath("$.queued").value(2));
			assertThat(providerReached.await(10, TimeUnit.SECONDS)).isTrue();

			send().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCEPTANCE_SEND_RUNNING"));
			summary().andExpect(jsonPath("$.send.state").value("SENDING"))
				.andExpect(jsonPath("$.send.queued").value(2));
			mockMvc.perform(post("/admin/registrations/" + ada.id() + "/ticket-email").header("Authorization", admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCEPTANCE_SEND_RUNNING"));
		}
		finally {
			providerGate = null;
			gate.countDown();
		}
		awaitIdle();

		assertThat(ticketEmails(ada)).hasSize(1);
		assertThat(ticketEmails(grace)).hasSize(1);
		summary().andExpect(jsonPath("$.send.sent").value(2));
	}

	@Test
	void theTicketEmailActionTellsOnePersonEarlyAndResendsOnceTheyKnow() throws Exception {
		Hacker ada = register("Ada", GSU);
		Hacker grace = register("Grace", GSU);

		mockMvc.perform(post("/admin/registrations/" + ada.id() + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		setStatus(ada.id(), "ACCEPTED");
		setStatus(grace.id(), "ACCEPTED");

		rejectedByProvider.add(ada.email());
		mockMvc.perform(post("/admin/registrations/" + ada.id() + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("EMAIL_FAILED"));
		summary().andExpect(jsonPath("$.totals.acceptedWaiting").value(2));
		rejectedByProvider.clear();

		mockMvc.perform(post("/admin/registrations/" + ada.id() + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isNoContent());
		assertThat(ticketEmails(ada)).as("sent before the response, not in the background").hasSize(1);
		summary().andExpect(jsonPath("$.totals.acceptedNotified").value(1))
			.andExpect(jsonPath("$.totals.acceptedWaiting").value(1));

		mockMvc.perform(post("/admin/registrations/" + ada.id() + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isNoContent());
		for (int attempt = 0; attempt < 50 && ticketEmails(ada).size() < 2; attempt++) {
			Thread.sleep(100);
		}
		assertThat(ticketEmails(ada)).hasSize(2);
		assertThat(ticketEmails(ada).get(0).idempotencyKey()).startsWith("acceptance-" + ada.id() + "-");
		assertThat(ticketEmails(ada).get(1).idempotencyKey()).as("a copy an admin asked for always goes out").isNull();

		send().andExpect(status().isAccepted()).andExpect(jsonPath("$.queued").value(1));
		awaitIdle();
		assertThat(ticketEmails(ada)).as("already told, so the send skips them").hasSize(2);
		assertThat(ticketEmails(grace)).hasSize(1);

		setStatus(ada.id(), "REJECTED").andExpect(jsonPath("$.acceptanceNotifiedAt").value(nullValue()));
		setStatus(ada.id(), "ACCEPTED");
		mockMvc.perform(get("/admin/acceptances/waiting").header("Authorization", admin))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(ada.id()));
		mockMvc
			.perform(post("/admin/registrations/" + UUID.randomUUID() + "/ticket-email").header("Authorization", admin))
			.andExpect(status().isNotFound());
	}

	@Test
	void hostSchoolShareCountsBothCampusesAndSaysWhatIsMissing() throws Exception {
		summary().andExpect(jsonPath("$.shares.accepted.total").value(0))
			.andExpect(jsonPath("$.shares.accepted.share").value(nullValue()))
			.andExpect(jsonPath("$.shares.accepted.met").value(true))
			.andExpect(jsonPath("$.shares.accepted.moreHostNeeded").value(0))
			.andExpect(jsonPath("$.shares.accepted.fewerOthersNeeded").value(0))
			.andExpect(jsonPath("$.totals.acceptanceRate").value(nullValue()))
			.andExpect(jsonPath("$.acceptedBySchool", hasSize(0)));

		List<Hacker> host = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			host.add(register("Main" + (char) ('A' + i), GSU));
		}
		host.add(register("PerimeterA", PERIMETER));
		host.add(register("PerimeterB", PERIMETER));
		host.add(register("Shouting", "  GEORGIA STATE UNIVERSITY "));
		List<Hacker> others = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			others.add(register("Tech" + (char) ('A' + i), TECH));
		}
		Hacker lookalike = register("Lookalike", "Georgia State");
		Hacker hostPending = register("Later", GSU);

		setStatuses("ACCEPTED", host).andExpect(jsonPath("$.changed").value(7));
		setStatuses("ACCEPTED", others.subList(0, 2)).andExpect(jsonPath("$.changed").value(2));
		summary().andExpect(jsonPath("$.shares.accepted.total").value(9))
			.andExpect(jsonPath("$.shares.accepted.host").value(7))
			.andExpect(jsonPath("$.shares.accepted.met").value(true));

		setStatus(others.get(2).id(), "ACCEPTED");
		summary().andExpect(jsonPath("$.shares.accepted.total").value(10))
			.andExpect(jsonPath("$.shares.accepted.host").value(7))
			.andExpect(jsonPath("$.shares.accepted.other").value(3))
			.andExpect(jsonPath("$.shares.accepted.share").value(0.7))
			.andExpect(jsonPath("$.shares.accepted.met").value(true))
			.andExpect(jsonPath("$.shares.accepted.moreHostNeeded").value(0))
			.andExpect(jsonPath("$.shares.accepted.fewerOthersNeeded").value(0))
			// 8 of 13 registrations and 1 of 3 still pending are from the host school.
			.andExpect(jsonPath("$.shares.registrations.total").value(13))
			.andExpect(jsonPath("$.shares.registrations.host").value(8))
			.andExpect(jsonPath("$.shares.registrations.met").value(false))
			.andExpect(jsonPath("$.shares.pending.total").value(3))
			.andExpect(jsonPath("$.shares.pending.host").value(1))
			.andExpect(jsonPath("$.shares.checkedIn.total").value(0))
			.andExpect(jsonPath("$.acceptedBySchool", hasSize(4)))
			.andExpect(jsonPath("$.acceptedBySchool[0].school").value(GSU))
			.andExpect(jsonPath("$.acceptedBySchool[0].count").value(4))
			.andExpect(jsonPath("$.acceptedBySchool[0].host").value(true))
			.andExpect(jsonPath("$.acceptedBySchool[1].school").value(TECH))
			.andExpect(jsonPath("$.acceptedBySchool[1].count").value(3))
			.andExpect(jsonPath("$.acceptedBySchool[1].host").value(false))
			.andExpect(jsonPath("$.acceptedBySchool[2].school").value(PERIMETER))
			.andExpect(jsonPath("$.acceptedBySchool[2].host").value(true));

		setStatus(lookalike.id(), "ACCEPTED");
		// 7 of 11 is 63.6%. Three more host acceptances make 10 of 14; one fewer other makes 7 of 10.
		summary().andExpect(jsonPath("$.shares.accepted.total").value(11))
			.andExpect(jsonPath("$.shares.accepted.host").value(7))
			.andExpect(jsonPath("$.shares.accepted.share").value(closeTo(7.0 / 11, 1e-9)))
			.andExpect(jsonPath("$.shares.accepted.met").value(false))
			.andExpect(jsonPath("$.shares.accepted.moreHostNeeded").value(3))
			.andExpect(jsonPath("$.shares.accepted.fewerOthersNeeded").value(1));

		setStatus(hostPending.id(), "ACCEPTED");
		summary().andExpect(jsonPath("$.shares.accepted.host").value(8))
			.andExpect(jsonPath("$.shares.accepted.met").value(false))
			.andExpect(jsonPath("$.shares.accepted.moreHostNeeded").value(2))
			.andExpect(jsonPath("$.shares.accepted.fewerOthersNeeded").value(1));

		mockMvc.perform(post("/admin/check-in/" + host.get(4).id()).header("Authorization", admin))
			.andExpect(status().isOk());
		mockMvc.perform(post("/admin/check-in/" + others.get(3).id()).header("Authorization", admin))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_ACCEPTED"));
		mockMvc.perform(post("/admin/check-in/" + others.get(0).id()).header("Authorization", admin))
			.andExpect(status().isOk());
		summary().andExpect(jsonPath("$.shares.checkedIn.total").value(2))
			.andExpect(jsonPath("$.shares.checkedIn.host").value(1))
			.andExpect(jsonPath("$.shares.checkedIn.share").value(0.5))
			.andExpect(jsonPath("$.shares.checkedIn.met").value(false))
			.andExpect(jsonPath("$.shares.checkedIn.moreHostNeeded").value(2));
	}

	@Test
	void bulkStatusChangeFollowsTheSameRulesAndSendsNothing() throws Exception {
		Hacker ada = register("Ada", GSU);
		Hacker grace = register("Grace", TECH);
		Hacker linus = register("Linus", TECH);
		String unknown = UUID.randomUUID().toString();

		bulk("ACCEPTED", List.of(ada.id(), grace.id(), unknown, ada.id())).andExpect(status().isOk())
			.andExpect(jsonPath("$.changed").value(2))
			.andExpect(jsonPath("$.unchanged").value(0))
			.andExpect(jsonPath("$.notFound").value(1));
		bulk("ACCEPTED", List.of(ada.id(), grace.id(), linus.id())).andExpect(jsonPath("$.changed").value(1))
			.andExpect(jsonPath("$.unchanged").value(2))
			.andExpect(jsonPath("$.notFound").value(0));
		Thread.sleep(300);
		assertThat(ticketEmails()).isEmpty();
		summary().andExpect(jsonPath("$.totals.acceptedWaiting").value(3));
		mockMvc.perform(get("/admin/registrations/" + ada.id()).header("Authorization", admin))
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.acceptedAt").isNotEmpty());

		bulk("REJECTED", List.of(grace.id())).andExpect(jsonPath("$.changed").value(1));
		bulk("WAITLISTED", List.of(linus.id())).andExpect(jsonPath("$.changed").value(1));
		summary().andExpect(jsonPath("$.totals.acceptedWaiting").value(1))
			.andExpect(jsonPath("$.totals.rejected").value(1))
			.andExpect(jsonPath("$.totals.waitlisted").value(1));
		bulk("PENDING", List.of(ada.id(), grace.id(), linus.id())).andExpect(jsonPath("$.changed").value(3));
		summary().andExpect(jsonPath("$.totals.pending").value(3)).andExpect(jsonPath("$.totals.accepted").value(0));

		bulk("ACCEPTED", List.of()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors.ids").isNotEmpty());
		List<String> tooMany = new ArrayList<>();
		for (int i = 0; i < 501; i++) {
			tooMany.add(UUID.randomUUID().toString());
		}
		bulk("ACCEPTED", tooMany).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.ids").value("At most 500 registrations at a time"));
		bulk("MAYBE", List.of(ada.id())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc
			.perform(post("/admin/registrations/status").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ids\":[\"%s\"]}".formatted(ada.id())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.status").value("Status is required"));
	}

	@Test
	void anAgeReviewIsFlaggedForYoungerStudentsOfOtherSchoolsOnly() throws Exception {
		Hacker hostMinor = register("Ada", GSU, 17);
		Hacker perimeterMinor = register("Grace", "  georgia state university perimeter college ", 16);
		Hacker otherMinor = register("Linus", TECH, 17);
		Hacker otherAtMinimum = register("Margaret", TECH, 18);
		Hacker lookalikeMinor = register("Alan", "Georgia Southern University", 17);

		for (Hacker hacker : List.of(hostMinor, perimeterMinor, otherAtMinimum)) {
			mockMvc.perform(get("/admin/registrations/" + hacker.id()).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ageReview").value(false));
		}
		for (Hacker hacker : List.of(otherMinor, lookalikeMinor)) {
			mockMvc.perform(get("/admin/registrations/" + hacker.id()).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ageReview").value(true));
		}
		mockMvc.perform(get("/admin/registrations").header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(5))
			.andExpect(jsonPath("$.items[?(@.ageReview == true)]", hasSize(2)))
			.andExpect(jsonPath("$.items[?(@.ageReview == false)]", hasSize(3)))
			.andExpect(jsonPath("$.items[?(@.id == '%s')].ageReview".formatted(otherMinor.id())).value(true))
			.andExpect(jsonPath("$.items[?(@.id == '%s')].ageReview".formatted(hostMinor.id())).value(false));

		mockMvc.perform(get("/admin/registrations").param("ageReview", "true").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.items[?(@.ageReview == true)]", hasSize(2)));
		mockMvc.perform(get("/admin/registrations").param("ageReview", "false").header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.items[?(@.ageReview == false)]", hasSize(3)));
		mockMvc
			.perform(get("/admin/registrations").param("ageReview", "true")
				.param("status", "ACCEPTED")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(0));

		String csv = mockMvc.perform(get("/admin/registrations/export.csv").header("Authorization", admin))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(csv.lines().findFirst().orElseThrow()).endsWith(",school_email_confirmed,age_review");
		assertThat(csvLine(csv, otherMinor)).endsWith(",true");
		assertThat(csvLine(csv, lookalikeMinor)).endsWith(",true");
		assertThat(csvLine(csv, hostMinor)).endsWith(",false");
		assertThat(csvLine(csv, perimeterMinor)).endsWith(",false");
		assertThat(csvLine(csv, otherAtMinimum)).endsWith(",false");
		String flaggedCsv = mockMvc
			.perform(get("/admin/registrations/export.csv").param("ageReview", "true")
				.header("Authorization", admin))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(flaggedCsv.lines()).hasSize(3);
		assertThat(flaggedCsv).contains(otherMinor.email(), lookalikeMinor.email())
			.doesNotContain(hostMinor.email(), otherAtMinimum.email());

		mockMvc.perform(get("/public/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ageReview").doesNotExist());
	}

	@Test
	void acceptingSomeoneWhoNeedsAnAgeReviewWorksAndIsCounted() throws Exception {
		Hacker hostMinor = register("Ada", GSU, 17);
		Hacker otherMinor = register("Linus", TECH, 17);
		Hacker secondOtherMinor = register("Alan", TECH, 15);
		Hacker otherAdult = register("Margaret", TECH, 18);
		Hacker thirdOtherMinor = register("Edsger", TECH, 17);

		summary().andExpect(jsonPath("$.ageReview.minimumAge").value(18))
			.andExpect(jsonPath("$.ageReview.total").value(3))
			.andExpect(jsonPath("$.ageReview.accepted").value(0));

		setStatus(otherMinor.id(), "ACCEPTED").andExpect(jsonPath("$.ageReview").value(true))
			.andExpect(jsonPath("$.ticketUrl").isNotEmpty());
		setStatus(hostMinor.id(), "ACCEPTED").andExpect(jsonPath("$.ageReview").value(false));
		summary().andExpect(jsonPath("$.ageReview.total").value(3))
			.andExpect(jsonPath("$.ageReview.accepted").value(1));

		// otherMinor is already accepted, so only the two newly accepted minors are counted.
		bulk("ACCEPTED",
				List.of(otherMinor.id(), secondOtherMinor.id(), thirdOtherMinor.id(), otherAdult.id(),
						UUID.randomUUID().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.changed").value(3))
			.andExpect(jsonPath("$.unchanged").value(1))
			.andExpect(jsonPath("$.notFound").value(1))
			.andExpect(jsonPath("$.acceptedAgeReview").value(2));
		summary().andExpect(jsonPath("$.totals.accepted").value(5))
			.andExpect(jsonPath("$.ageReview.total").value(3))
			.andExpect(jsonPath("$.ageReview.accepted").value(3));
		mockMvc
			.perform(get("/admin/registrations").param("ageReview", "true")
				.param("status", "ACCEPTED")
				.header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(3));
		mockMvc.perform(get("/admin/acceptances/waiting").header("Authorization", admin))
			.andExpect(jsonPath("$", hasSize(5)))
			.andExpect(jsonPath("$[?(@.ageReview == true)]", hasSize(3)))
			.andExpect(jsonPath("$[?(@.id == '%s')].ageReview".formatted(hostMinor.id())).value(false))
			.andExpect(jsonPath("$[?(@.id == '%s')].ageReview".formatted(otherAdult.id())).value(false));

		bulk("WAITLISTED", List.of(secondOtherMinor.id(), thirdOtherMinor.id()))
			.andExpect(jsonPath("$.changed").value(2))
			.andExpect(jsonPath("$.acceptedAgeReview").value(0));
		summary().andExpect(jsonPath("$.ageReview.total").value(3))
			.andExpect(jsonPath("$.ageReview.accepted").value(1));

		send().andExpect(status().isAccepted()).andExpect(jsonPath("$.queued").value(3));
		awaitIdle();
		assertThat(ticketEmails(otherMinor)).hasSize(1);
	}

	@Test
	void volunteersCannotSeeOrSendAcceptances() throws Exception {
		Hacker ada = register("Ada", GSU);
		setStatus(ada.id(), "ACCEPTED");
		String email = "door-" + UUID.randomUUID() + "@test.local";
		String created = mockMvc
			.perform(post("/admin/admins").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"name\":\"Door\",\"role\":\"VOLUNTEER\"}".formatted(email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String link = JsonPath.read(created, "$.setPasswordUrl");
		mockMvc
			.perform(post("/admin/auth/set-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"%s\",\"password\":\"volunteer-password\"}"
					.formatted(link.substring(link.indexOf("token=") + 6))))
			.andExpect(status().isNoContent());
		String session = mockMvc
			.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"volunteer-password\"}".formatted(email)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String volunteer = "Bearer " + JsonPath.read(session, "$.token");

		MockHttpServletRequestBuilder[] adminOnly = { get("/admin/acceptances/summary"),
				get("/admin/acceptances/waiting"), post("/admin/acceptances/send"),
				post("/admin/registrations/status").contentType(MediaType.APPLICATION_JSON)
					.content("{\"ids\":[\"%s\"],\"status\":\"REJECTED\"}".formatted(ada.id())),
				post("/admin/registrations/" + ada.id() + "/ticket-email") };
		for (MockHttpServletRequestBuilder request : adminOnly) {
			mockMvc.perform(request.header("Authorization", volunteer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.message").isNotEmpty());
		}
		mockMvc.perform(get("/admin/acceptances/summary")).andExpect(status().isUnauthorized());

		Thread.sleep(300);
		assertThat(ticketEmails()).isEmpty();
		summary().andExpect(jsonPath("$.totals.acceptedWaiting").value(1));
		mockMvc
			.perform(delete("/admin/admins/" + JsonPath.read(created, "$.id")).header("Authorization", admin))
			.andExpect(status().isNoContent());
	}

	@Test
	void theMigrationTreatsPeopleAcceptedBeforeItAsAlreadyTold() {
		String schema = "v6_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Flyway.configure().dataSource(dataSource).schemas(schema).target("5").load().migrate();
		String insert = """
				insert into %s.registrations (id, first_name, last_name, age, phone, email, school, level_of_study,
					country_of_residence, mlh_code_of_conduct, mlh_data_sharing, mlh_email_opt_in, status,
					unsubscribe_token, ticket_token)
				values (gen_random_uuid(), 'Old', :status, 20, '1', :email, 'Georgia State University', 'Undergraduate',
					'US', true, true, false, :status, gen_random_uuid()::text, gen_random_uuid()::text)
				""".formatted(schema);
		for (String status : List.of("ACCEPTED", "PENDING", "WAITLISTED", "REJECTED")) {
			jdbc.sql(insert).param("status", status).param("email", status.toLowerCase() + "@example.com").update();
		}

		Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate();

		List<String> told = jdbc
			.sql("select status from %s.registrations where acceptance_notified_at is not null".formatted(schema))
			.query(String.class)
			.list();
		assertThat(told).containsExactly("ACCEPTED");
		assertThat(jdbc.sql("select count(*) from %s.registrations where accepted_at is not null".formatted(schema))
			.query(Long.class)
			.single()).isZero();
		jdbc.sql("drop schema %s cascade".formatted(schema)).update();
	}

	private record Hacker(String id, String email) {
	}

	private Hacker register(String firstName, String school) throws Exception {
		return register(firstName, school, 19);
	}

	private Hacker register(String firstName, String school, int age) throws Exception {
		String email = firstName.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String created = mockMvc
			.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"%s","lastName":"Example","age":%d,"phone":"404 555 0100","email":"%s",
					 "schoolEmail":"%s","school":"%s","levelOfStudy":"Undergraduate University (3+ year)",
					 "countryOfResidence":"US","mlhCodeOfConduct":true,"mlhDataSharing":true,"mlhEmailOptIn":false}
					""".formatted(firstName, age, email, email, school)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new Hacker(JsonPath.read(created, "$.id"), email);
	}

	private static String csvLine(String csv, Hacker hacker) {
		return csv.lines().filter(line -> line.contains(hacker.email())).findFirst().orElseThrow();
	}

	private ResultActions setStatus(String id, String status) throws Exception {
		return mockMvc
			.perform(patch("/admin/registrations/" + id).header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"%s\"}".formatted(status)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(status));
	}

	private ResultActions setStatuses(String status, List<Hacker> hackers) throws Exception {
		return bulk(status, hackers.stream().map(Hacker::id).toList()).andExpect(status().isOk());
	}

	private ResultActions bulk(String status, List<String> ids) throws Exception {
		String list = ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		return mockMvc.perform(post("/admin/registrations/status").header("Authorization", admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\":[%s],\"status\":\"%s\"}".formatted(list, status)));
	}

	private ResultActions summary() throws Exception {
		return mockMvc.perform(get("/admin/acceptances/summary").header("Authorization", admin))
			.andExpect(status().isOk());
	}

	private ResultActions send() throws Exception {
		return mockMvc.perform(post("/admin/acceptances/send").header("Authorization", admin));
	}

	private void awaitIdle() throws Exception {
		for (int attempt = 0; attempt < 200; attempt++) {
			String body = summary().andReturn().getResponse().getContentAsString();
			if ("IDLE".equals(JsonPath.read(body, "$.send.state"))) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("The acceptance send did not finish");
	}

	private static boolean isTicket(EmailMessage message) {
		return message.subject().startsWith("You're in");
	}

	private static List<EmailMessage> ticketEmails() {
		return sentEmails.stream().filter(AcceptanceApiTests::isTicket).toList();
	}

	private static List<EmailMessage> ticketEmails(Hacker hacker) {
		return ticketEmails().stream().filter(message -> message.to().equals(hacker.email())).toList();
	}

}
