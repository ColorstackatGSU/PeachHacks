package com.peachhacks.backend;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A different minimum age and host school need their own application context. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.admin.bootstrap-email=age-review@test.local",
		"app.admin.bootstrap-password=correct-horse-battery", "app.admin.bootstrap-name=Test Organizer",
		"app.rate-limit.public-per-minute=100000", "app.rate-limit.login-per-minute=100000", "app.rate-limit.sign-up-per-window=100000",
		"app.rate-limit.sign-up-global-per-hour=100000",
		"app.acceptance.non-host-minimum-age=21", "app.acceptance.host-school-name=Kennesaw State University" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AgeReviewMinimumAgeTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theMinimumAgeAndTheHostSchoolComeFromConfiguration() throws Exception {
		String login = mockMvc
			.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"age-review@test.local\",\"password\":\"correct-horse-battery\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String admin = "Bearer " + JsonPath.read(login, "$.token");
		mockMvc
			.perform(put("/admin/settings").header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"registrationOpen\":true}"))
			.andExpect(status().isOk());

		String otherAtTwenty = register("Georgia State University", 20);
		String otherAtTwentyOne = register("Georgia State University", 21);
		String hostAtSeventeen = register("Kennesaw State University", 17);

		mockMvc.perform(get("/admin/registrations/" + otherAtTwenty).header("Authorization", admin))
			.andExpect(jsonPath("$.ageReview").value(true));
		mockMvc.perform(get("/admin/registrations/" + otherAtTwentyOne).header("Authorization", admin))
			.andExpect(jsonPath("$.ageReview").value(false));
		mockMvc.perform(get("/admin/registrations/" + hostAtSeventeen).header("Authorization", admin))
			.andExpect(jsonPath("$.ageReview").value(false));
		mockMvc.perform(get("/admin/registrations").param("ageReview", "true").header("Authorization", admin))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(otherAtTwenty));
		mockMvc.perform(get("/admin/acceptances/summary").header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hostSchool.name").value("Kennesaw State University"))
			.andExpect(jsonPath("$.ageReview.minimumAge").value(21))
			.andExpect(jsonPath("$.ageReview.total").value(1))
			.andExpect(jsonPath("$.ageReview.accepted").value(0));
	}

	private String register(String school, int age) throws Exception {
		String email = "age-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String created = mockMvc
			.perform(post("/public/registrations").contentType(MediaType.APPLICATION_JSON).content("""
					{"firstName":"Ada","lastName":"Example","age":%d,"phone":"404 555 0100","email":"%s",
					 "schoolEmail":"%s","school":"%s","levelOfStudy":"Undergraduate University (3+ year)","graduationYear":2028,"graduationMonth":5,
					 "countryOfResidence":"US","mlhCodeOfConduct":true,"mlhDataSharing":true,"mlhEmailOptIn":false}
					""".formatted(age, email, email, school)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(created, "$.id");
	}

}
