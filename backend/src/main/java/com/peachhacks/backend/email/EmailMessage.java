package com.peachhacks.backend.email;

import java.util.List;
import java.util.Map;

/**
 * idempotencyKey is null for most messages. When set, the provider treats a second send
 * with the same key as the first one again instead of delivering another copy. replyTo is
 * null unless replies should go somewhere other than the sending address.
 */
public record EmailMessage(String to, String subject, String html, String text, Map<String, String> headers,
		List<Attachment> attachments, String idempotencyKey, String replyTo) {

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
		this(to, subject, html, text, headers, List.of(), null, null);
	}

	public EmailMessage withAttachments(List<Attachment> files) {
		return new EmailMessage(to, subject, html, text, headers, files, idempotencyKey, replyTo);
	}

	public EmailMessage withIdempotencyKey(String key) {
		return new EmailMessage(to, subject, html, text, headers, attachments, key, replyTo);
	}

	public EmailMessage withReplyTo(String address) {
		return new EmailMessage(to, subject, html, text, headers, attachments, idempotencyKey, address);
	}

}
