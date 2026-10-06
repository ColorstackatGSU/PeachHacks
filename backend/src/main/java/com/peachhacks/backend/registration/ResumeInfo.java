package com.peachhacks.backend.registration;

import java.time.Instant;

public record ResumeInfo(String fileName, int size, Instant uploadedAt) {

	static ResumeInfo from(RegistrationResume resume) {
		return new ResumeInfo(resume.getFileName(), resume.getSize(), resume.getUploadedAt());
	}

}
