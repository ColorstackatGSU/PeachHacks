package com.peachhacks.backend.registration;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Csv;
import com.peachhacks.backend.registration.ResumeUpload.ResumeFile;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ResumeService {

	/** One person in the sponsor resume book. */
	public record BookEntry(UUID registrationId, String firstName, String lastName, String email,
			String schoolEmail, String school,
			String levelOfStudy, String major, String linkedinUrl) {
	}

	/** A stored resume ready to send: its file name and bytes. */
	public record StoredResume(String fileName, byte[] content) {
	}

	private static final List<String> INDEX_HEADER = List.of("first_name", "last_name", "email",
			"school_email", "school",
			"level_of_study", "major", "linkedin_url", "file_name");

	private static final int MAX_NAME_PART = 40;

	private final JdbcClient jdbc;

	private final RegistrationResumeRepository repository;

	public ResumeService(JdbcClient jdbc, RegistrationResumeRepository repository) {
		this.jdbc = jdbc;
		this.repository = repository;
	}

	/** Joins the caller's transaction so the registration and its resume are stored together. */
	void store(UUID registrationId, ResumeFile file) {
		jdbc.sql("""
				insert into registration_resumes
					(registration_id, file_name, size_bytes, content, uploaded_at)
				values (:id, :fileName, :size, :content, :uploadedAt)
				""")
			.param("id", registrationId)
			.param("fileName", file.fileName())
			.param("size", file.content().length)
			.param("content", file.content())
			.param("uploadedAt", Timestamp.from(Instant.now()))
			.update();
	}

	public Optional<RegistrationResume> find(UUID registrationId) {
		return repository.findById(registrationId);
	}

	public Map<UUID, RegistrationResume> byRegistration(Collection<UUID> registrationIds) {
		if (registrationIds.isEmpty()) {
			return Map.of();
		}
		return index(repository.findAllByRegistrationIdIn(registrationIds));
	}

	public Map<UUID, RegistrationResume> byRegistration() {
		return index(repository.findAll());
	}

	public StoredResume load(UUID registrationId) {
		return jdbc.sql("select file_name, content from registration_resumes where registration_id = :id")
			.param("id", registrationId)
			.query((rs, rowNum) -> new StoredResume(rs.getString("file_name"), rs.getBytes("content")))
			.optional()
			.orElseThrow(ResumeService::noResume);
	}

	public void delete(UUID registrationId) {
		int removed = jdbc.sql("delete from registration_resumes where registration_id = :id")
			.param("id", registrationId)
			.update();
		if (removed == 0) {
			throw noResume();
		}
	}

	/**
	 * Everyone whose resume goes to sponsors: they uploaded one (the form says at the upload
	 * that sponsors receive it) and they were accepted. attendedOnly narrows it to people with a general check-in.
	 */
	public List<BookEntry> book(boolean attendedOnly) {
		return jdbc.sql("""
				select r.id, r.first_name, r.last_name, r.email, r.school_email, r.school, r.level_of_study,
					coalesce(r.major_other, r.major_field_of_study) as major, r.linkedin_url
				from registrations r
				join registration_resumes x on x.registration_id = r.id
				where r.status = 'ACCEPTED'
					and (:attendedOnly = false or exists (select 1 from check_ins c
						join events e on e.id = c.event_id where c.registration_id = r.id and e.general))
				order by lower(r.last_name), lower(r.first_name), r.id
				""")
			.param("attendedOnly", attendedOnly)
			.query((rs, rowNum) -> new BookEntry(rs.getObject("id", UUID.class), rs.getString("first_name"),
					rs.getString("last_name"), rs.getString("email"), rs.getString("school_email"),
					rs.getString("school"),
					rs.getString("level_of_study"), rs.getString("major"), rs.getString("linkedin_url")))
			.list();
	}

	/**
	 * Writes one PDF per entry and an index.csv straight to the stream, holding a single
	 * resume in memory at a time. Each file is read again as it is written, so someone whose
	 * resume was removed after the list was read is left out of both.
	 */
	public void writeBook(List<BookEntry> entries, OutputStream out) throws IOException {
		Csv index = new Csv(INDEX_HEADER);
		Set<String> used = new HashSet<>();
		try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
			for (BookEntry entry : entries) {
				Optional<byte[]> content = jdbc
					.sql("select content from registration_resumes where registration_id = :id")
					.param("id", entry.registrationId())
					.query((rs, rowNum) -> rs.getBytes("content"))
					.optional();
				if (content.isEmpty()) {
					continue;
				}
				String fileName = entryName(entry, used);
				zip.putNextEntry(new ZipEntry(fileName));
				zip.write(content.get());
				zip.closeEntry();
				index.row(Arrays.asList(entry.firstName(), entry.lastName(), entry.email(), entry.schoolEmail(),
						entry.school(),
						entry.levelOfStudy(), entry.major(), entry.linkedinUrl(), fileName));
			}
			zip.putNextEntry(new ZipEntry("index.csv"));
			zip.write(index.toString().getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}

	/** The short id keeps namesakes apart; the full id is the fallback should two short ids ever match. */
	private static String entryName(BookEntry entry, Set<String> used) {
		String id = entry.registrationId().toString().replace("-", "");
		String base = asciiPart(entry.lastName()) + "_" + asciiPart(entry.firstName()) + "_";
		String name = base + id.substring(0, 8) + ".pdf";
		if (!used.add(name.toLowerCase(Locale.ROOT))) {
			name = base + id + ".pdf";
			used.add(name.toLowerCase(Locale.ROOT));
		}
		return name;
	}

	/** Accents are folded to their base letter; anything else outside A-Z, 0-9 and hyphen is dropped. */
	static String asciiPart(String value) {
		String folded = Normalizer.normalize((value != null) ? value : "", Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "")
			.strip()
			.replaceAll("[\\s-]+", "-")
			.replaceAll("[^A-Za-z0-9-]", "")
			.replaceAll("^-+|-+$", "");
		if (folded.isEmpty()) {
			return "Unknown";
		}
		return (folded.length() > MAX_NAME_PART) ? folded.substring(0, MAX_NAME_PART) : folded;
	}

	private static Map<UUID, RegistrationResume> index(List<RegistrationResume> resumes) {
		return resumes.stream().collect(Collectors.toMap(RegistrationResume::getRegistrationId, Function.identity()));
	}

	private static ApiException noResume() {
		return ApiException.notFound("This registration has no resume.");
	}

}
