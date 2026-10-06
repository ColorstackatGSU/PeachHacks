package com.peachhacks.backend.email;

import java.util.List;
import java.util.Map;

public record EmailMessage(String to, String subject, String html, String text, Map<String, String> headers,
		List<Attachment> attachments) {

	/** A contentId lets the HTML show the file inline with an img whose src is "cid:" plus that id. */
	public record Attachment(String filename, String contentType, byte[] content, String contentId) {

		@Override
		public String toString() {
			return "Attachment[" + filename + ", " + content.length + " bytes]";
		}

	}

	public EmailMessage {
		headers = (headers != null) ? Map.copyOf(headers) : Map.of();
		attachments = (attachments != null) ? List.copyOf(attachments) : List.of();
	}

	public EmailMessage(String to, String subject, String html, String text, Map<String, String> headers) {
		this(to, subject, html, text, headers, List.of());
	}

	public EmailMessage withAttachments(List<Attachment> files) {
		return new EmailMessage(to, subject, html, text, headers, files);
	}

}
