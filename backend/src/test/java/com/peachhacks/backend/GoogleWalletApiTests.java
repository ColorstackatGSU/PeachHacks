package com.peachhacks.backend;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.admin.bootstrap-email=organizer@test.local",
		"app.admin.bootstrap-password=correct-horse-battery", "app.rate-limit.public-per-minute=100000",
		"app.rate-limit.login-per-minute=100000", "app.google-wallet.issuer-id=3388000000012345678",
		"app.google-wallet.service-account-email=wallet@project.iam.gserviceaccount.com" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class GoogleWalletApiTests {

	private static final List<EmailMessage> sentEmails = new CopyOnWriteArrayList<>();

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingEmail {

		@Bean
		@Primary
		EmailSender recordingEmailSender() {
			return sentEmails::add;
		}

	}

	@DynamicPropertySource
	static void walletKey(DynamicPropertyRegistry registry) {
		registry.add("app.google-wallet.private-key", () -> GoogleWalletTests.pem(GoogleWalletTests.KEYS));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void acceptedTicketsExposeTheWalletLinkAndTheEmailCarriesIt() throws Exception {
		String login = mockMvc
			.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"organizer@test.local\",\"password\":\"correct-horse-battery\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String admin = "Bearer " + JsonPath.read(login, "$.token");
		String email = "wallet-" + UUID.randomUUID() + "@example.com";
		mockMvc
			.perform(put("/admin/settings").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"registrationOpen\":true}"))
			.andExpect(status().isOk());
		String created = mockMvc
			.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"Ada","lastName":"Lovelace","age":19,"phone":"404 555 0100","email":"%s","schoolEmail":"ada@school.edu",
					 "school":"Georgia State University","levelOfStudy":"Undergraduate University (3+ year)",
					 "countryOfResidence":"US","mlhCodeOfConduct":true,"mlhDataSharing":true,"mlhEmailOptIn":false}
					""".formatted(email)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(created, "$.id");
		String token = jdbc.sql("select ticket_token from registrations where id = :id")
			.param("id", UUID.fromString(id))
			.query(String.class)
			.single();
		String ticketUrl = "http://localhost:5173/ticket?t=" + token;

		mockMvc.perform(get("/public/tickets/" + token)).andExpect(status().isNotFound());
		mockMvc
			.perform(patch("/admin/registrations/" + id).header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACCEPTED\"}"))
			.andExpect(status().isOk());

		String ticket = mockMvc.perform(get("/public/tickets/" + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String walletUrl = JsonPath.read(ticket, "$.googleWalletUrl");
		String claims = GoogleWalletTests.verifiedClaims(walletUrl, GoogleWalletTests.KEYS.getPublic());
		assertThat((String) JsonPath.read(claims, "$.payload.eventTicketObjects[0].barcode.value")).isEqualTo(ticketUrl);
		assertThat((String) JsonPath.read(claims, "$.payload.eventTicketObjects[0].id"))
			.isEqualTo("3388000000012345678.ticket_" + id.replace("-", ""));

		EmailMessage message = null;
		for (int attempt = 0; attempt < 50 && message == null; attempt++) {
			Thread.sleep(100);
			message = sentEmails.stream()
				.filter(sent -> sent.to().equals(email) && !sent.attachments().isEmpty())
				.findFirst()
				.orElse(null);
		}
		assertThat(message).isNotNull();
		assertThat(message.text()).contains(ticketUrl).contains("Add to Google Wallet: " + GoogleWalletTests.SAVE_PREFIX);
		assertThat(message.html()).contains(">Add to Google Wallet</a>");
	}

}
