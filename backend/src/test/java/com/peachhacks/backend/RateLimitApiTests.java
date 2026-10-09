package com.peachhacks.backend;

import java.net.URI;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.peachhacks.backend.admin.AdminRole;
import com.peachhacks.backend.admin.AuthService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Low limits, so this class has its own application context. Every test speaks from its
 * own client addresses, given the way the hosting proxy gives them: as the last entry of
 * X-Forwarded-For.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.rate-limit.public-per-minute=100000", "app.rate-limit.login-per-minute=5",
		"app.rate-limit.sign-up-per-window=2", "app.rate-limit.sign-up-global-per-hour=5",
		"app.rate-limit.login-failures-per-account=3", "app.rate-limit.trust-forwarded-for=true" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RateLimitApiTests {

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Test
	void anEncodedLoginPathCountsAsALoginAndTheClientIsTheLastForwardedEntry() throws Exception {
		for (int attempt = 0; attempt < 5; attempt++) {
			mockMvc
				.perform(from(loginRequest(post(URI.create("/admin/%61uth/login")), unique() + "@test.local", "wrong"),
						"198.51.100." + attempt + ", 203.0.113.1"))
				.andExpect(status().isUnauthorized());
		}
		mockMvc
			.perform(from(loginRequest(post(URI.create("/admin/%61uth/login")), unique() + "@test.local", "wrong"),
					"198.51.100.99, 203.0.113.1"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		login(unique() + "@test.local", "wrong", "203.0.113.1").andExpect(status().isTooManyRequests());
		login(unique() + "@test.local", "wrong", "203.0.113.1, 203.0.113.2").andExpect(status().isUnauthorized());
	}

	@Test
	void changingAPasswordSharesTheLoginAllowance() throws Exception {
		String email = account();
		String session = "Bearer " + JsonPath.read(login(email, PASSWORD, "203.0.113.10").andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.token");

		for (int attempt = 0; attempt < 4; attempt++) {
			changePassword(session, "203.0.113.10").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.currentPassword").isNotEmpty());
		}
		changePassword(session, "203.0.113.10").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		changePassword(session, "203.0.113.11").andExpect(status().isBadRequest());
	}

	@Test
	void failedSignInsForOneEmailAreThrottledWhateverTheAddressAndASuccessClearsThem() throws Exception {
		String email = account();
		String nobody = unique() + "@test.local";
		int address = 20;

		login(email.toUpperCase(), "wrong", "203.0.113." + address++).andExpect(status().isUnauthorized());
		login(email, "wrong", "203.0.113." + address++).andExpect(status().isUnauthorized());
		login(email, PASSWORD, "203.0.113." + address++).andExpect(status().isOk());

		for (String attempted : new String[] { email, nobody }) {
			for (int attempt = 0; attempt < 3; attempt++) {
				login(attempted, "wrong", "203.0.113." + address++).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
					.andExpect(jsonPath("$.message").value("Incorrect email or password."));
			}
			login(attempted, PASSWORD, "203.0.113." + address++).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
				.andExpect(jsonPath("$.message").value(containsString("Too many failed sign-in attempts")));
		}
	}

	@Test
	void signUpsAreLimitedPerAddressAndAcrossAllAddresses() throws Exception {
		preRegister("203.0.113.40").andExpect(status().isCreated());
		preRegister("203.0.113.40").andExpect(status().isCreated());
		preRegister("203.0.113.40").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
			.andExpect(jsonPath("$.message").value(containsString("from this network")));
		mockMvc
			.perform(from(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("{}"),
					"203.0.113.40"))
			.andExpect(status().isTooManyRequests());
		mockMvc
			.perform(from(post("/public/unsubscribe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"no-such-token\"}"), "203.0.113.40"))
			.andExpect(status().isNotFound());

		preRegister("203.0.113.41").andExpect(status().isCreated());
		preRegister("203.0.113.41").andExpect(status().isCreated());
		preRegister("203.0.113.42").andExpect(status().isCreated());
		preRegister("203.0.113.42").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"))
			.andExpect(jsonPath("$.message").value(containsString("Please try again shortly")));
		mockMvc
			.perform(from(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("{}"),
					"203.0.113.43"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.message").value(containsString("Please try again shortly")));
	}

	private String account() {
		String email = unique() + "@test.local";
		authService.create(email, "Limited", PASSWORD, AdminRole.VOLUNTEER);
		return email;
	}

	private ResultActions login(String email, String password, String forwardedFor) throws Exception {
		return mockMvc.perform(from(loginRequest(post("/admin/auth/login"), email, password), forwardedFor));
	}

	private static MockHttpServletRequestBuilder loginRequest(MockHttpServletRequestBuilder request, String email,
			String password) {
		return request.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
	}

	private ResultActions changePassword(String session, String forwardedFor) throws Exception {
		return mockMvc.perform(from(post("/admin/auth/change-password").header("Authorization", session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"not-my-password\",\"newPassword\":\"a-brand-new-password\"}"),
				forwardedFor));
	}

	private ResultActions preRegister(String forwardedFor) throws Exception {
		return mockMvc
			.perform(from(post("/public/pre-registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"Ada","lastName":"Lovelace","email":"%s@example.com","school":"Rate Limit School","schoolEmail":"ada@school.edu"}
					""".formatted(unique())), forwardedFor));
	}

	private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String forwardedFor) {
		return request.header("X-Forwarded-For", forwardedFor);
	}

	private static String unique() {
		return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

}
