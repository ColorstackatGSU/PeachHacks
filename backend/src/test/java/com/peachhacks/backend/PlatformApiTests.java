package com.peachhacks.backend;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Google and Discord are one local stub. The code "Google" hands back here is
 * "email|nonce|audience", which the stub's token endpoint turns into the ID token Google
 * would issue for that sign-in.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.admin.bootstrap-email=platform@test.local",
		"app.admin.bootstrap-password=correct-horse-battery", "app.admin.bootstrap-name=Test Organizer",
		"app.rate-limit.public-per-minute=100000", "app.rate-limit.login-per-minute=100000",
		"app.rate-limit.sign-up-per-window=100000", "app.rate-limit.sign-up-global-per-hour=100000",
		"app.platform.google-client-id=google-client", "app.platform.google-client-secret=google-secret",
		"app.platform.api-base-url=https://api.test", "app.platform.max-team-size=2",
		"app.platform.base-url=https://platform.test", "app.discord.bot-token=test-bot-token",
		"app.discord.public-key=3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
		"app.discord.application-id=4242", "app.discord.client-secret=oauth-secret", "app.discord.guild-id=1000",
		"app.discord.hacker-role-id=2000", "app.discord.verification-channel-id=3000" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PlatformApiTests {

	private static final Pattern LINK_TOKEN = Pattern.compile("set-password\\?token=([A-Za-z0-9_-]+)");

	private static final List<String> calls = new CopyOnWriteArrayList<>();

	private static final List<String> bodies = new CopyOnWriteArrayList<>();

	private static final List<EmailMessage> sentEmails = new CopyOnWriteArrayList<>();

	private static final HttpServer stub;

	static {
		try {
			stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		stub.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			calls.add(exchange.getRequestMethod() + " " + path);
			String answer = null;
			if (path.equals("/token")) {
				String form = bodies.get(bodies.size() - 1);
				String[] code = URLDecoder
					.decode(form.substring(form.indexOf("code=") + 5).split("&")[0], StandardCharsets.UTF_8)
					.split("\\|");
				String claims = "{\"aud\":\"%s\",\"iss\":\"https://accounts.google.com\",\"sub\":\"sub-%s\",\"email\":\"%s\",\"email_verified\":true,\"nonce\":\"%s\",\"exp\":%d}"
					.formatted(code[2], code[0], code[0], code[1], System.currentTimeMillis() / 1000 + 600);
				answer = "{\"id_token\":\"e30.%s.signature\"}".formatted(Base64.getUrlEncoder()
					.withoutPadding()
					.encodeToString(claims.getBytes(StandardCharsets.UTF_8)));
			}
			else if (path.equals("/oauth2/token")) {
				answer = "{\"access_token\":\"user-access-token\"}";
			}
			else if (path.equals("/users/@me")) {
				answer = "{\"id\":\"777\",\"username\":\"ada_on_discord\"}";
			}
			if (answer != null) {
				byte[] body = answer.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			else {
				exchange.sendResponseHeaders(204, -1);
			}
			exchange.close();
		});
		stub.start();
	}

	@DynamicPropertySource
	static void stubUrls(DynamicPropertyRegistry registry) {
		String base = "http://127.0.0.1:" + stub.getAddress().getPort();
		registry.add("app.discord.api-base-url", () -> base);
		registry.add("app.platform.google-token-url", () -> base + "/token");
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

	private String admin;

	@BeforeEach
	void signInAsAdmin() throws Exception {
		String login = mockMvc
			.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"platform@test.local\",\"password\":\"correct-horse-battery\"}"))
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
		calls.clear();
		bodies.clear();
	}

	@Test
	void anAcceptedHackerChoosesAPasswordSignsInAndEditsTheirProfile() throws Exception {
		Hacker ada = register("Ada", "ACCEPTED");
		Hacker pending = register("Pending", "PENDING");
		mockMvc.perform(get("/platform/me")).andExpect(status().isUnauthorized());

		requestLink(pending.email()).andExpect(status().isNoContent());
		requestLink("nobody@example.com").andExpect(status().isNoContent());
		requestLink(ada.email()).andExpect(status().isNoContent());
		await(() -> sentEmails.stream().anyMatch(message -> isLink(message, ada.email())));
		assertThat(sentEmails).noneMatch(message -> isLink(message, pending.email()));
		EmailMessage email = sentEmails.stream().filter(message -> isLink(message, ada.email())).findFirst().orElseThrow();
		assertThat(email.text()).contains("https://platform.test/#/set-password?token=");
		Matcher matcher = LINK_TOKEN.matcher(email.text());
		assertThat(matcher.find()).isTrue();
		String linkToken = matcher.group(1);

		setPassword(linkToken, "short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.password").exists());
		String session = token(setPassword(linkToken, "a-long-password").andExpect(status().isOk()));
		setPassword(linkToken, "another-password").andExpect(status().isNotFound());

		as(session, get("/platform/me")).andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Ada"))
			.andExpect(jsonPath("$.hasPassword").value(true))
			.andExpect(jsonPath("$.ticket.token").isNotEmpty())
			.andExpect(jsonPath("$.ticket.url").isNotEmpty())
			.andExpect(jsonPath("$.profile.listed").value(true));

		login(ada.email(), "not-the-password").andExpect(status().isUnauthorized());
		String second = token(login(ada.email().toUpperCase(), "a-long-password").andExpect(status().isOk()));

		as(second, patch("/platform/me")).content("""
				{"bio":"Builds things","githubUrl":"http://example.com/ada","lookingForTeam":true}
				""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.githubUrl").exists());
		as(second, patch("/platform/me")).content("""
				{"bio":"Builds things","githubUrl":"https://github.com/ada","lookingForTeam":true}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.profile.lookingForTeam").value(true));
		as(second, get("/platform/hackers")).andExpect(jsonPath("$[?(@.id=='%s')].bio".formatted(ada.id()),
				hasItem("Builds things")));

		as(second, patch("/platform/me")).content("{\"bio\":\"Builds things\",\"listed\":false}")
			.andExpect(status().isOk());
		as(second, get("/platform/hackers")).andExpect(jsonPath("$[*].id", not(hasItem(ada.id()))));

		setStatus(ada.id(), "WAITLISTED");
		as(second, get("/platform/me")).andExpect(status().isUnauthorized());
		login(ada.email(), "a-long-password").andExpect(status().isUnauthorized());
	}

	@Test
	void googleSignsInOnlyTheAddressOfAnAcceptedApplication() throws Exception {
		Hacker grace = register("Grace", "ACCEPTED");
		Hacker pending = register("Waiting", "PENDING");
		mockMvc.perform(get("/platform/config"))
			.andExpect(jsonPath("$.googleSignIn").value(true))
			.andExpect(jsonPath("$.discordClientId").value("4242"))
			.andExpect(jsonPath("$.discordRedirectUri").value("https://platform.test/"))
			.andExpect(jsonPath("$.maxTeamSize").value(2));

		String start = googleStart();
		assertThat(start).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
			.contains("client_id=google-client")
			.contains("redirect_uri=https://api.test/platform/auth/google/callback")
			.contains("prompt=select_account");
		assertThat(start).doesNotContain("google-secret");

		assertThat(googleReturn(null, null, "access_denied")).isEqualTo("https://platform.test/?google_error=cancelled");
		assertThat(googleReturn("code", "a-state-nobody-issued", null))
			.isEqualTo("https://platform.test/?google_error=expired");
		assertThat(googleLanding(grace.email(), "someone-elses-client"))
			.isEqualTo("https://platform.test/?google_error=failed");

		String refused = googleLanding(pending.email(), "google-client");
		assertThat(refused).startsWith("https://platform.test/?google=").doesNotContain("@");
		claim(handoff(refused)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_ACCEPTED"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(pending.email())));

		String landing = googleLanding(grace.email(), "google-client");
		String session = token(claim(handoff(landing)).andExpect(status().isOk()));
		claim(handoff(landing)).andExpect(status().isUnauthorized());

		as(session, get("/platform/me")).andExpect(jsonPath("$.email").value(grace.email()))
			.andExpect(jsonPath("$.hasPassword").value(false));
		as(session, post("/platform/auth/logout")).andExpect(status().isNoContent());
		as(session, get("/platform/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void teamsFillUpThroughRequestsAndPassOnWhenTheLeaderLeaves() throws Exception {
		String lead = signIn(register("Lead", "ACCEPTED"));
		Hacker joinerHacker = register("Joiner", "ACCEPTED");
		String joiner = signIn(joinerHacker);
		String late = signIn(register("Late", "ACCEPTED"));
		String name = "Peach Pit " + UUID.randomUUID().toString().substring(0, 6);

		as(lead, post("/platform/teams")).content("{\"name\":\"x\"}").andExpect(status().isBadRequest());
		String created = as(lead, post("/platform/teams"))
			.content("{\"name\":\"%s\",\"description\":\"Hardware and snacks\"}".formatted(name))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String teamId = JsonPath.read(created, "$.id");
		as(joiner, post("/platform/teams")).content("{\"name\":\"%s\"}".formatted(name.toUpperCase()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.name").exists());
		as(lead, post("/platform/teams")).content("{\"name\":\"Second team\"}").andExpect(status().isConflict());

		as(joiner, post("/platform/teams/" + teamId + "/requests")).content("{\"message\":\"I do frontend\"}")
			.andExpect(status().isNoContent());
		as(late, post("/platform/teams/" + teamId + "/requests")).andExpect(status().isNoContent());
		as(joiner, get("/platform/teams")).andExpect(jsonPath("$[?(@.id=='%s')].requested".formatted(teamId), hasItem(true)))
			.andExpect(jsonPath("$[?(@.id=='%s')].requests[*]".formatted(teamId), hasSize(0)));
		String teams = as(lead, get("/platform/teams")).andExpect(jsonPath("$[0].id").value(teamId))
			.andExpect(jsonPath("$[0].openSpots").value(1))
			.andExpect(jsonPath("$[0].requests", hasSize(2)))
			.andExpect(jsonPath("$[0].requests[0].message").value("I do frontend"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String joinerRequest = JsonPath.read(teams, "$[0].requests[0].id");
		String lateRequest = JsonPath.read(teams, "$[0].requests[1].id");

		as(joiner, post("/platform/teams/mine/requests/" + joinerRequest + "/accept")).andExpect(status().isForbidden());
		as(lead, post("/platform/teams/mine/requests/" + joinerRequest + "/accept")).andExpect(status().isNoContent());
		as(lead, post("/platform/teams/mine/requests/" + lateRequest + "/accept")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TEAM_FULL"));
		as(late, post("/platform/teams/" + teamId + "/requests")).andExpect(status().isConflict());
		as(joiner, get("/platform/me")).andExpect(jsonPath("$.teamId").value(teamId));
		as(joiner, get("/platform/hackers"))
			.andExpect(jsonPath("$[?(@.id=='%s')].teamName".formatted(joinerHacker.id()), hasItem(name)));

		as(lead, post("/platform/teams/mine/leave")).andExpect(status().isNoContent());
		as(joiner, get("/platform/teams")).andExpect(jsonPath("$[0].members", hasSize(1)))
			.andExpect(jsonPath("$[0].members[0].owner").value(true));
		as(joiner, patch("/platform/teams/mine")).content("{\"name\":\"%s\",\"description\":null}".formatted(name))
			.andExpect(status().isNoContent());
		as(joiner, post("/platform/teams/mine/leave")).andExpect(status().isNoContent());
		as(joiner, get("/platform/teams")).andExpect(jsonPath("$[*].id", not(hasItem(teamId))));
		as(late, delete("/platform/teams/" + teamId + "/requests")).andExpect(status().isNoContent());
	}

	@Test
	void connectingDiscordAddsTheHackerToTheServerWithTheRole() throws Exception {
		Hacker ada = register("Ada", "ACCEPTED");
		String session = signIn(ada);

		as(session, post("/platform/discord")).content("{\"code\":\"oauth-code\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.discordUsername").value("ada_on_discord"));

		assertThat(calls).containsSubsequence("POST /oauth2/token", "GET /users/@me", "PUT /guilds/1000/members/777",
				"PUT /guilds/1000/members/777/roles/2000");
		assertThat(bodies.get(calls.indexOf("POST /oauth2/token"))).contains("code=oauth-code")
			.contains("redirect_uri=https%3A%2F%2Fplatform.test%2F");
		assertThat(bodies.get(calls.indexOf("PUT /guilds/1000/members/777"))).contains("user-access-token")
			.contains("2000");
		as(session, get("/platform/me")).andExpect(jsonPath("$.discordUsername").value("ada_on_discord"));

		calls.clear();
		setStatus(ada.id(), "REJECTED");
		await(() -> calls.contains("DELETE /guilds/1000/members/777/roles/2000"));
	}

	@Test
	void aPreviewLinkLetsAnOrganizerRegisterWhileRegistrationIsClosed() throws Exception {
		mockMvc
			.perform(put("/admin/settings").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"registrationOpen\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.previewActive").value(false));
		mockMvc.perform(post("/admin/settings/registration-preview")).andExpect(status().isUnauthorized());
		String link = JsonPath.read(mockMvc
			.perform(post("/admin/settings/registration-preview").header("Authorization", admin))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.url");
		assertThat(link).contains("/?preview=").endsWith("#register");
		String key = link.substring(link.indexOf("preview=") + 8, link.indexOf('#'));

		mockMvc.perform(get("/public/status"))
			.andExpect(jsonPath("$.registrationOpen").value(false))
			.andExpect(jsonPath("$.preview").value(false));
		mockMvc.perform(get("/public/status").header("X-Registration-Preview", "not-the-key"))
			.andExpect(jsonPath("$.registrationOpen").value(false));
		mockMvc.perform(get("/public/status").header("X-Registration-Preview", key))
			.andExpect(jsonPath("$.registrationOpen").value(true))
			.andExpect(jsonPath("$.preview").value(true));

		submitRegistration("Closed", null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("REGISTRATION_CLOSED"));
		submitRegistration("Tester", key).andExpect(status().isCreated());

		mockMvc.perform(delete("/admin/settings/registration-preview").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.previewActive").value(false));
		submitRegistration("Late", key).andExpect(status().isForbidden());
	}

	private ResultActions submitRegistration(String firstName, String previewKey) throws Exception {
		String email = firstName.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		MockHttpServletRequestBuilder request = post("/public/registrations").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"firstName":"%s","lastName":"Example","age":19,"phone":"404 555 0100","email":"%s",
					 "schoolEmail":"%s","school":"Georgia State University",
					 "levelOfStudy":"Undergraduate University (3+ year)","graduationYear":2028,
					 "countryOfResidence":"US","mlhCodeOfConduct":true,"mlhDataSharing":true,"mlhEmailOptIn":false}
					""".formatted(firstName, email, email));
		return mockMvc.perform((previewKey != null) ? request.header("X-Registration-Preview", previewKey) : request);
	}

	private record Hacker(String id, String email) {
	}

	private Hacker register(String firstName, String status) throws Exception {
		String email = firstName.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String created = mockMvc
			.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"%s","lastName":"Example","age":19,"phone":"404 555 0100","email":"%s",
					 "schoolEmail":"%s","school":"Georgia State University",
					 "levelOfStudy":"Undergraduate University (3+ year)","graduationYear":2028,
					 "countryOfResidence":"US","mlhCodeOfConduct":true,"mlhDataSharing":true,"mlhEmailOptIn":false}
					""".formatted(firstName, email, email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Hacker hacker = new Hacker(JsonPath.read(created, "$.id"), email);
		if (!status.equals("PENDING")) {
			setStatus(hacker.id(), status);
		}
		return hacker;
	}

	private void setStatus(String id, String status) throws Exception {
		mockMvc
			.perform(patch("/admin/registrations/" + id).header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"%s\"}".formatted(status)))
			.andExpect(status().isOk());
	}

	private String signIn(Hacker hacker) throws Exception {
		return token(claim(handoff(googleLanding(hacker.email(), "google-client"))).andExpect(status().isOk()));
	}

	/** The address at Google that "Continue with Google" sends the browser to. */
	private String googleStart() throws Exception {
		return mockMvc.perform(get("/platform/auth/google/start"))
			.andExpect(status().isFound())
			.andReturn()
			.getResponse()
			.getHeader("Location");
	}

	/** Where the browser ends up after choosing this Google account. */
	private String googleLanding(String email, String audience) throws Exception {
		String start = googleStart();
		return googleReturn(email + "|" + parameter(start, "nonce") + "|" + audience, parameter(start, "state"), null);
	}

	private String googleReturn(String code, String state, String error) throws Exception {
		MockHttpServletRequestBuilder request = get("/platform/auth/google/callback");
		if (code != null) {
			request = request.param("code", code);
		}
		if (state != null) {
			request = request.param("state", state);
		}
		if (error != null) {
			request = request.param("error", error);
		}
		return mockMvc.perform(request).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
	}

	private ResultActions claim(String handoff) throws Exception {
		return mockMvc.perform(post("/platform/auth/google/claim").contentType(MediaType.APPLICATION_JSON)
			.content("{\"handoff\":\"%s\"}".formatted(handoff)));
	}

	private static String handoff(String landing) {
		return landing.substring(landing.indexOf("?google=") + 8);
	}

	private static String parameter(String url, String name) {
		String rest = url.substring(url.indexOf(name + "=") + name.length() + 1);
		return rest.contains("&") ? rest.substring(0, rest.indexOf('&')) : rest;
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private ResultActions requestLink(String email) throws Exception {
		return mockMvc.perform(post("/platform/auth/password-link").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\"}".formatted(email)));
	}

	private ResultActions setPassword(String token, String password) throws Exception {
		return mockMvc.perform(post("/platform/auth/set-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password)));
	}

	private Request as(String session, MockHttpServletRequestBuilder builder) {
		return new Request(builder.header("Authorization", "Bearer " + session).contentType(MediaType.APPLICATION_JSON));
	}

	/** A request that can be sent as it is or given a JSON body first. */
	private final class Request {

		private final MockHttpServletRequestBuilder builder;

		private Request(MockHttpServletRequestBuilder builder) {
			this.builder = builder;
		}

		private ResultActions content(String body) throws Exception {
			return mockMvc.perform(builder.content(body));
		}

		private ResultActions andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
			return mockMvc.perform(builder).andExpect(matcher);
		}

	}

	private static String token(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
	}

	private static boolean isLink(EmailMessage message, String email) {
		return message.to().equals(email) && message.subject().contains("platform");
	}

	private static void await(BooleanSupplier condition) throws Exception {
		for (int attempt = 0; attempt < 200; attempt++) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(25);
		}
		throw new AssertionError("Timed out; Discord saw " + calls);
	}

}
