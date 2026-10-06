package com.peachhacks.backend.registration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.peachhacks.backend.admin.AdminPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminResumeController {

	private static final Logger log = LoggerFactory.getLogger(AdminResumeController.class);

	private final RegistrationService registrations;

	private final ResumeService resumes;

	public AdminResumeController(RegistrationService registrations, ResumeService resumes) {
		this.registrations = registrations;
		this.resumes = resumes;
	}

	@GetMapping("/registrations/{id}/resume")
	ResponseEntity<byte[]> download(@PathVariable UUID id) {
		registrations.get(id);
		ResumeService.StoredResume resume = resumes.load(id);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment()
						.filename(resume.fileName(), StandardCharsets.UTF_8)
						.build()
						.toString())
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(resume.content());
	}

	@DeleteMapping("/registrations/{id}/resume")
	ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AdminPrincipal admin) {
		registrations.get(id);
		resumes.delete(id);
		log.info("Resume of registration {} removed by {}", id, admin.email());
		return ResponseEntity.noContent().build();
	}

	/**
	 * Written straight to the response instead of returned, so the archive is never held
	 * in memory. The list is read before the first byte goes out; a failure there still
	 * produces a normal error response.
	 */
	@GetMapping("/resumes/export.zip")
	void export(@RequestParam(defaultValue = "false") boolean checkedIn, @AuthenticationPrincipal AdminPrincipal admin,
			HttpServletResponse response) throws IOException {
		List<ResumeService.BookEntry> entries = resumes.book(checkedIn);
		log.info("Resume book of {} resumes ({}) exported by {}", entries.size(),
				checkedIn ? "attended only" : "all accepted", admin.email());
		String filename = "peachhacks-resume-book-" + LocalDate.now(ZoneOffset.UTC) + ".zip";
		response.setContentType("application/zip");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(filename).build().toString());
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		resumes.writeBook(entries, response.getOutputStream());
	}

}
