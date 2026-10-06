package com.peachhacks.backend.registration;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

import com.peachhacks.backend.common.ApiException;

/** The resume as it arrives inside the registration JSON. */
public record ResumeUpload(String fileName, String contentBase64) {

	public static final int MAX_BYTES = 2 * 1024 * 1024;

	public static final String TOO_LARGE = "Resume must be 2 MB or smaller";

	private static final int MAX_ENCODED_CHARS = (MAX_BYTES + 2) / 3 * 4;

	private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private static final int MAX_NAME_LENGTH = 120;

	private static final String EXTENSION = ".pdf";

	private static final String NOT_ALLOWED_IN_NAMES = "<>:\"|?*";

	/** A sanitized file name and the decoded bytes of a file known to start like a PDF. */
	public record ResumeFile(String fileName, byte[] content) {
	}

	ResumeFile toFile() {
		String encoded = (contentBase64 != null) ? contentBase64.strip() : "";
		if (encoded.isEmpty()) {
			throw ApiException.invalidField("resume", "Choose a PDF file to upload");
		}
		if (encoded.length() > MAX_ENCODED_CHARS) {
			throw ApiException.invalidField("resume", TOO_LARGE);
		}
		byte[] content;
		try {
			content = Base64.getDecoder().decode(encoded);
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.invalidField("resume", "Resume could not be read. Please choose the file again");
		}
		if (content.length > MAX_BYTES) {
			throw ApiException.invalidField("resume", TOO_LARGE);
		}
		if (content.length < PDF_SIGNATURE.length
				|| !Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
			throw ApiException.invalidField("resume", "Resume must be a PDF file");
		}
		return new ResumeFile(cleanFileName(fileName), content);
	}

	/**
	 * The name is shown to organizers and sent back in a Content-Disposition header, so
	 * directories, control and invisible formatting characters and characters Windows
	 * rejects are dropped. The result always ends in .pdf.
	 */
	static String cleanFileName(String raw) {
		String name = (raw != null) ? raw : "";
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		StringBuilder kept = new StringBuilder();
		name.codePoints().forEach(codePoint -> {
			switch (Character.getType(codePoint)) {
				case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
						Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED -> {
				}
				default -> kept.appendCodePoint((NOT_ALLOWED_IN_NAMES.indexOf(codePoint) >= 0) ? '_' : codePoint);
			}
		});
		name = kept.toString().strip();
		if (name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
			name = name.substring(0, name.length() - EXTENSION.length());
		}
		name = name.replaceFirst("^[.\\s]+", "").stripTrailing();
		if (name.isEmpty()) {
			name = "resume";
		}
		int maxBase = MAX_NAME_LENGTH - EXTENSION.length();
		if (name.codePointCount(0, name.length()) > maxBase) {
			name = name.substring(0, name.offsetByCodePoints(0, maxBase)).stripTrailing();
		}
		return name + EXTENSION;
	}

	/** Keeps the file out of anything that prints the request. */
	@Override
	public String toString() {
		return "ResumeUpload[fileName=" + fileName + ", contentBase64="
				+ ((contentBase64 != null) ? contentBase64.length() + " chars" : "null") + "]";
	}

}
