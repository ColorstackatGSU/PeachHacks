package com.peachhacks.backend;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.peachhacks.backend.email.EmailMessage;
import com.peachhacks.backend.email.EmailSender;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Discord itself is a local stub that records what PeachBot asked of it. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.admin.bootstrap-email=discord@test.local",
		"app.admin.bootstrap-password=correct-horse-battery", "app.admin.bootstrap-name=Test Organizer",
		"app.rate-limit.public-per-minute=100000", "app.rate-limit.login-per-minute=100000",
		"app.rate-limit.sign-up-per-window=100000", "app.rate-limit.sign-up-global-per-hour=100000",
		"app.discord.bot-token=test-bot-token", "app.discord.application-id=4242", "app.discord.guild-id=1000",
		"app.discord.hacker-role-id=2000", "app.discord.verification-channel-id=3000" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DiscordApiTests {

	private static final String ROLE_PATH = "/guilds/1000/members/%s/roles/2000";

	private static final Pattern CODE = Pattern.compile("\\b\\d{6}\\b");

	private static final List<String> discordCalls = new CopyOnWriteArrayList<>();

	private static final List<String> discordBodies = new CopyOnWriteArrayList<>();

	private static final List<String> botAuthorizations = new CopyOnWriteArrayList<>();

	private static final List<EmailMessage> sentEmails = new CopyOnWriteArrayList<>();

	private static final AtomicInteger messageIds = new AtomicInteger();

	private static final AtomicInteger users = new AtomicInteger(500);

	private static final KeyPair keys;

	private static final HttpServer discord;

	static {
		try {
			keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
			discord = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		discord.createContext("/", exchange -> {
			String call = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
			discordBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			botAuthorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
			discordCalls.add(call);
			if (call.startsWith("POST /channels/")) {
				byte[] body = ("{\"id\":\"" + (9000 + messageIds.incrementAndGet()) + "\"}")
					.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			else {
				exchange.sendResponseHeaders(204, -1);
			}
			exchange.close();
		});
		discord.start();
	}

	@DynamicPropertySource
	static void discordProperties(DynamicPropertyRegistry registry) {
		byte[] encoded = keys.getPublic().getEncoded();
		registry.add("app.discord.public-key",
				() -> HexFormat.of().formatHex(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length)));
		registry.add("app.discord.api-base-url", () -> "http://127.0.0.1:" + discord.getAddress().getPort());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingEmail {

		@Bean
		@Primary
		EmailSender recordingEmailSender() {
			return sentEmails::add;
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	private String admin;

	@BeforeEach
	void signIn() throws Exception {
		String login = mockMvc
			.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"discord@test.local\",\"password\":\"correct-horse-battery\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		admin = "Bearer " + JsonPath.read(login, "$.token");
		mockMvc
			.perform(put("/admin/settings").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"registrationOpen\":true}"))
			.andExpect(status().isOk());
		discordCalls.clear();
		discordBodies.clear();
		botAuthorizations.clear();
	}

	@Test
	void aPingIsAnsweredAndAnUnsignedRequestIsRefused() throws Exception {
		interact("{\"type\":1}").andExpect(status().isOk()).andExpect(jsonPath("$.type").value(1));

		mockMvc
			.perform(post("/discord/interactions").contentType(MediaType.APPLICATION_JSON)
				.header("X-Signature-Ed25519", "00".repeat(64))
				.header("X-Signature-Timestamp", "1700000000")
				.content("{\"type\":1}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/discord/interactions").contentType(MediaType.APPLICATION_JSON).content("{\"type\":1}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void anAcceptedHackerGetsTheRoleAndLosesItWhenNoLongerAccepted() throws Exception {
		Hacker ada = register("Ada");
		setStatus(ada.id(), "ACCEPTED");
		String user = newUser();

		click(user, "peachbot:verify").andExpect(jsonPath("$.type").value(9))
			.andExpect(jsonPath("$.data.custom_id").value("peachbot:email"));
		submit(user, "peachbot:email", "email", "  " + ada.email().toUpperCase() + " ")
			.andExpect(jsonPath("$.type").value(4))
			.andExpect(jsonPath("$.data.flags").value(64))
			.andExpect(jsonPath("$.data.components[0].components[0].custom_id").value("peachbot:code"));
		String code = codeMailedTo(ada.email());
		click(user, "peachbot:code").andExpect(jsonPath("$.type").value(9))
			.andExpect(jsonPath("$.data.custom_id").value("peachbot:code-submit"));

		submit(user, "peachbot:code-submit", "code", code).andExpect(jsonPath("$.type").value(5))
			.andExpect(jsonPath("$.data.flags").value(64));
		awaitCall("PATCH /webhooks/4242/token-" + user + "/messages/@original");
		assertThat(discordCalls).contains("PUT " + ROLE_PATH.formatted(user));
		assertThat(botAuthorizations).contains("Bot test-bot-token");
		assertThat(lastBody()).contains("You are verified");
		mockMvc.perform(get("/admin/discord").header("Authorization", admin))
			.andExpect(jsonPath("$.configured").value(true));
		assertThat(linkedUser(ada)).isEqualTo(user);

		discordCalls.clear();
		click(user, "peachbot:verify").andExpect(jsonPath("$.type").value(4));
		awaitCall("PUT " + ROLE_PATH.formatted(user));

		setStatus(ada.id(), "WAITLISTED");
		awaitCall("DELETE " + ROLE_PATH.formatted(user));
		click(user, "peachbot:verify").andExpect(jsonPath("$.type").value(9));

		discordCalls.clear();
		setStatus(ada.id(), "ACCEPTED");
		awaitCall("PUT " + ROLE_PATH.formatted(user));
	}

	@Test
	void someoneNotAcceptedGetsTheSameReplyAndNoCode() throws Exception {
		Hacker pending = register("Pending");
		String user = newUser();
		sentEmails.clear();

		submit(user, "peachbot:email", "email", pending.email()).andExpect(jsonPath("$.type").value(4))
			.andExpect(jsonPath("$.data.components[0].components[0].custom_id").value("peachbot:code"));
		submit(user, "peachbot:email", "email", "nobody@example.com").andExpect(jsonPath("$.type").value(4))
			.andExpect(jsonPath("$.data.components[0].components[0].custom_id").value("peachbot:code"));

		submit(user, "peachbot:code-submit", "code", "123456").andExpect(jsonPath("$.type").value(5));
		awaitCall("PATCH /webhooks/4242/token-" + user + "/messages/@original");
		assertThat(lastBody()).contains("no code waiting");
		assertThat(discordCalls).noneMatch(call -> call.startsWith("PUT "));
		assertThat(sentEmails).noneMatch(message -> message.subject().contains("Discord"));
		assertThat(linkedUser(pending)).isNull();
	}

	@Test
	void aWrongCodeVerifiesNobodyAndFiveGuessesUseTheCodeUp() throws Exception {
		Hacker grace = register("Grace");
		setStatus(grace.id(), "ACCEPTED");
		String user = newUser();
		submit(user, "peachbot:email", "email", grace.email());
		String code = codeMailedTo(grace.email());
		String wrong = code.equals("000000") ? "000001" : "000000";

		for (int guess = 1; guess <= 5; guess++) {
			discordCalls.clear();
			submit(user, "peachbot:code-submit", "code", wrong);
			awaitCall("PATCH /webhooks/4242/token-" + user + "/messages/@original");
			assertThat(lastBody()).contains("not right");
		}
		discordCalls.clear();
		submit(user, "peachbot:code-submit", "code", code);
		awaitCall("PATCH /webhooks/4242/token-" + user + "/messages/@original");
		assertThat(lastBody()).contains("no code waiting");
		assertThat(linkedUser(grace)).isNull();
	}

	@Test
	void verifyingFromASecondAccountMovesTheRole() throws Exception {
		Hacker linus = register("Linus");
		setStatus(linus.id(), "ACCEPTED");
		String first = newUser();
		String second = newUser();
		verify(first, linus);

		discordCalls.clear();
		verify(second, linus);
		assertThat(discordCalls).contains("DELETE " + ROLE_PATH.formatted(first), "PUT " + ROLE_PATH.formatted(second));
		assertThat(linkedUser(linus)).isEqualTo(second);
	}

	@Test
	void anotherServerIsTurnedAway() throws Exception {
		interact("""
				{"type":3,"guild_id":"7777","token":"t","member":{"user":{"id":"1","username":"x"}},
				 "data":{"custom_id":"peachbot:verify"}}
				""").andExpect(jsonPath("$.type").value(4))
			.andExpect(jsonPath("$.data.content").value("PeachBot only works inside the PeachHacks server."));
	}

	@Test
	void anAdminPostsTheVerificationMessageAndTheOldOneIsRemoved() throws Exception {
		mockMvc.perform(post("/admin/discord/verification-message")).andExpect(status().isUnauthorized());

		mockMvc.perform(post("/admin/discord/verification-message").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messagePostedAt").isNotEmpty());
		assertThat(discordCalls).contains("POST /channels/3000/messages");
		assertThat(discordBodies.get(discordCalls.indexOf("POST /channels/3000/messages")))
			.contains("\"custom_id\":\"peachbot:verify\"");
		String firstId = String.valueOf(9000 + messageIds.get());

		discordCalls.clear();
		mockMvc.perform(post("/admin/discord/verification-message").header("Authorization", admin))
			.andExpect(status().isOk());
		assertThat(discordCalls).containsExactly("POST /channels/3000/messages",
				"DELETE /channels/3000/messages/" + firstId);
	}

	private void verify(String user, Hacker hacker) throws Exception {
		sentEmails.clear();
		// Each test uses its own address, so the per-address limit on codes is not reached here.
		submit(user, "peachbot:email", "email", hacker.email());
		String code = codeMailedTo(hacker.email());
		submit(user, "peachbot:code-submit", "code", code);
		awaitCall("PATCH /webhooks/4242/token-" + user + "/messages/@original");
		assertThat(lastBody()).contains("You are verified");
	}

	private record Hacker(String id, String email) {
	}

	private Hacker register(String firstName) throws Exception {
		String email = firstName.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String created = mockMvc
			.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"%s","lastName":"Example","age":19,"phone":"404 555 0100","email":"%s",
					 "schoolEmail":"%s","school":"Georgia State University",
					 "levelOfStudy":"Undergraduate University (3+ year)",
					 "countryOfResidence":"US","mlhCodeOfConduct":true,"mlhDataSharing":true,"mlhEmailOptIn":false}
					""".formatted(firstName, email, email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new Hacker(JsonPath.read(created, "$.id"), email);
	}

	private void setStatus(String id, String status) throws Exception {
		mockMvc
			.perform(patch("/admin/registrations/" + id).header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"%s\"}".formatted(status)))
			.andExpect(status().isOk());
	}

	private String linkedUser(Hacker hacker) {
		return jdbc.sql("select discord_user_id from discord_links where registration_id = cast(:id as uuid)")
			.param("id", hacker.id())
			.query(String.class)
			.optional()
			.orElse(null);
	}

	private static String newUser() {
		return String.valueOf(users.incrementAndGet());
	}

	private ResultActions click(String user, String button) throws Exception {
		return interact("""
				{"type":3,"guild_id":"1000","token":"token-%s","member":{"user":{"id":"%s","username":"hacker%s"}},
				 "data":{"custom_id":"%s","component_type":2}}
				""".formatted(user, user, user, button)).andExpect(status().isOk());
	}

	private ResultActions submit(String user, String modal, String field, String value) throws Exception {
		return interact("""
				{"type":5,"guild_id":"1000","token":"token-%s","member":{"user":{"id":"%s","username":"hacker%s"}},
				 "data":{"custom_id":"%s","components":[{"type":1,"components":[
				   {"type":4,"custom_id":"%s","value":"%s"}]}]}}
				""".formatted(user, user, user, modal, field, value)).andExpect(status().isOk());
	}

	private ResultActions interact(String body) throws Exception {
		String timestamp = "1700000000";
		Signature signer = Signature.getInstance("Ed25519");
		signer.initSign(keys.getPrivate());
		signer.update((timestamp + body).getBytes(StandardCharsets.UTF_8));
		return mockMvc.perform(post("/discord/interactions").contentType(MediaType.APPLICATION_JSON)
			.header("X-Signature-Ed25519", HexFormat.of().formatHex(signer.sign()))
			.header("X-Signature-Timestamp", timestamp)
			.content(body));
	}

	private String codeMailedTo(String email) throws Exception {
		await(() -> sentEmails.stream().anyMatch(message -> isCode(message, email)), "a code email to " + email);
		EmailMessage message = sentEmails.stream().filter(m -> isCode(m, email)).reduce((a, b) -> b).orElseThrow();
		Matcher matcher = CODE.matcher(message.text());
		assertThat(matcher.find()).isTrue();
		return matcher.group();
	}

	private static boolean isCode(EmailMessage message, String email) {
		return message.to().equals(email) && message.subject().contains("Discord");
	}

	private static String lastBody() {
		return discordBodies.get(discordBodies.size() - 1);
	}

	private static void awaitCall(String call) throws Exception {
		await(() -> discordCalls.contains(call), call + " among " + discordCalls);
	}

	private static void await(BooleanSupplier condition, String what) throws Exception {
		for (int attempt = 0; attempt < 200; attempt++) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(25);
		}
		throw new AssertionError("Timed out waiting for " + what);
	}

}
