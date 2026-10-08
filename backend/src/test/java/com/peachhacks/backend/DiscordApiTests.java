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

import com.jayway.jsonpath.JsonPath;
import com.peachhacks.backend.discord.DiscordWelcome;
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
import static org.hamcrest.Matchers.containsString;
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
		"app.discord.hacker-role-id=2000", "app.discord.verification-channel-id=3000",
		"app.discord.welcome-channel-id=5000", "app.discord.applications-channel-id=6000", "app.discord.gateway-url=ws://127.0.0.1:1" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DiscordApiTests {

	private static final String ROLE_PATH = "/guilds/1000/members/%s/roles/2000";

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
		registry.add("app.discord.cdn-base-url", () -> "http://127.0.0.1:" + discord.getAddress().getPort());
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

	@Autowired
	private DiscordWelcome welcome;

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
	void verifyGivesTheRoleToAConnectedAcceptedHackerAndFollowsTheirStatus() throws Exception {
		Hacker ada = register("Ada");
		setStatus(ada.id(), "ACCEPTED");
		String user = newUser();
		connect(ada, user);

		click(user, "peachbot:verify").andExpect(jsonPath("$.type").value(4))
			.andExpect(jsonPath("$.data.flags").value(64))
			.andExpect(jsonPath("$.data.content").value(containsString("You are verified")))
			.andExpect(jsonPath("$.data.components").isEmpty());
		awaitCall("PUT " + ROLE_PATH.formatted(user));
		assertThat(botAuthorizations).contains("Bot test-bot-token");

		discordCalls.clear();
		setStatus(ada.id(), "WAITLISTED");
		awaitCall("DELETE " + ROLE_PATH.formatted(user));
		discordCalls.clear();
		click(user, "peachbot:verify")
			.andExpect(jsonPath("$.data.content").value(containsString("not accepted")))
			.andExpect(jsonPath("$.data.components").isEmpty());
		assertThat(discordCalls).noneMatch(call -> call.startsWith("PUT "));

		setStatus(ada.id(), "ACCEPTED");
		awaitCall("PUT " + ROLE_PATH.formatted(user));
	}

	@Test
	void verifyPointsAnUnconnectedAccountToThePlatform() throws Exception {
		Hacker accepted = register("Grace");
		setStatus(accepted.id(), "ACCEPTED");
		String stranger = newUser();

		click(stranger, "peachbot:verify").andExpect(jsonPath("$.type").value(4))
			.andExpect(jsonPath("$.data.flags").value(64))
			.andExpect(jsonPath("$.data.content").value(containsString("Connect Discord")))
			.andExpect(jsonPath("$.data.components[0].components[0].style").value(5))
			.andExpect(jsonPath("$.data.components[0].components[0].url").value("http://localhost:5176/#/discord"));
		assertThat(discordCalls).noneMatch(call -> call.contains("/roles/"));
		assertThat(linkedUser(accepted)).isNull();
	}

	@Test
	void someoneWhoJoinsIsWelcomedOnceWithACard() throws Exception {
		String user = newUser();

		welcome.memberJoined(user, "ada_l", "Ada Lovelace", "a1b2c3");
		awaitCall("POST /channels/5000/messages");
		assertThat(discordCalls).contains("GET /avatars/" + user + "/a1b2c3.png");
		String posted = discordBodies.get(discordCalls.indexOf("POST /channels/5000/messages"));
		assertThat(posted).contains("payload_json")
			.contains("Welcome to PeachHacks, <@" + user + ">")
			.contains("<#3000>")
			.contains("filename=\"welcome.png\"")
			.contains("PNG");

		discordCalls.clear();
		welcome.memberJoined(user, "ada_l", "Ada Lovelace", "a1b2c3");
		welcome.memberJoined(newUser(), "second", "", "");
		awaitCall("POST /channels/5000/messages");
		Thread.sleep(300);
		assertThat(discordCalls).as("the first person is not welcomed again").containsOnlyOnce("POST /channels/5000/messages");
	}

	@Test
	void organizersHearAboutEachApplicationAndGetARecapWithAChart() throws Exception {
		Hacker ada = register("Ada");
		awaitCall("POST /channels/6000/messages");
		String posted = discordBodies.get(discordCalls.indexOf("POST /channels/6000/messages"));
		assertThat(posted).contains("New application")
			.contains("Ada Example")
			.contains("Georgia State University")
			.contains("May 2028")
			.contains("Application #")
			.doesNotContain(ada.email());

		discordCalls.clear();
		discordBodies.clear();
		mockMvc.perform(post("/admin/discord/recap")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/admin/discord/recap").header("Authorization", admin)).andExpect(status().isNoContent());
		assertThat(discordCalls).containsExactly("POST /channels/6000/messages");
		assertThat(discordBodies.get(0)).contains("Daily recap")
			.contains("applications so far")
			.contains("Top schools")
			.contains("Georgia State University vs other schools")
			.contains("attachment://recap.png")
			.contains("filename=\"recap.png\"");
		mockMvc.perform(get("/admin/discord").header("Authorization", admin))
			.andExpect(jsonPath("$.welcomes").value(true))
			.andExpect(jsonPath("$.applications").value(true));
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
	void anAdminWritesTheVerificationMessageAndLaterEditsItInPlace() throws Exception {
		mockMvc.perform(post("/admin/discord/verification-message")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/discord").header("Authorization", admin))
			.andExpect(jsonPath("$.configured").value(true))
			.andExpect(jsonPath("$.message").value(containsString("Verify to get into PeachHacks")));

		publish("{\"message\":\"   \"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.message").exists());
		publish("{\"message\":\"%s\"}".formatted("x".repeat(901))).andExpect(status().isBadRequest());
		assertThat(discordCalls).isEmpty();

		publish("{\"message\":\"Welcome! Press Verify.\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.messagePostedAt").isNotEmpty())
			.andExpect(jsonPath("$.message").value("Welcome! Press Verify."));
		assertThat(discordCalls).containsExactly("POST /channels/3000/messages");
		assertThat(discordBodies.get(0)).contains("Welcome! Press Verify.").contains("\"custom_id\":\"peachbot:verify\"");
		String messageId = String.valueOf(9000 + messageIds.get());

		discordCalls.clear();
		discordBodies.clear();
		publish("{\"message\":\"Second wording.\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Second wording."));
		assertThat(discordCalls).containsExactly("PATCH /channels/3000/messages/" + messageId);
		assertThat(discordBodies.get(0)).contains("Second wording.");

		discordCalls.clear();
		mockMvc.perform(post("/admin/discord/verification-message").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("Second wording."));
		assertThat(discordCalls).containsExactly("PATCH /channels/3000/messages/" + messageId);
	}

	private ResultActions publish(String body) throws Exception {
		return mockMvc.perform(post("/admin/discord/verification-message").header("Authorization", admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** What "Connect Discord" on the platform records; PlatformApiTests covers that flow itself. */
	private void connect(Hacker hacker, String user) {
		jdbc.sql("""
				insert into discord_links (registration_id, discord_user_id, discord_username)
				values (cast(:id as uuid), :user, :username)
				""").param("id", hacker.id()).param("user", user).param("username", "hacker" + user).update();
	}

	private record Hacker(String id, String email) {
	}

	private Hacker register(String firstName) throws Exception {
		String email = firstName.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String created = mockMvc
			.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"%s","lastName":"Example","age":19,"phone":"404 555 0100","email":"%s",
					 "schoolEmail":"%s","school":"Georgia State University",
					 "levelOfStudy":"Undergraduate University (3+ year)","graduationYear":2028,"graduationMonth":5,
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
