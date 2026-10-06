package com.peachhacks.backend.email;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.peachhacks.backend.config.EmailProperties;

@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private static final String TICKET_CONTENT_ID = "peachhacks-ticket";

	private final EmailSender sender;

	private final EmailComposer composer;

	private final TaskExecutor mailExecutor;

	private final EmailProperties properties;

	public MailService(EmailSender sender, EmailComposer composer, @Qualifier("mailExecutor") TaskExecutor mailExecutor,
			EmailProperties properties) {
		this.sender = sender;
		this.composer = composer;
		this.mailExecutor = mailExecutor;
		this.properties = properties;
	}

	/** schoolEmailToConfirm is null when the school address is already confirmed. */
	public void sendPreRegistrationConfirmation(String email, String firstName, String unsubscribeToken,
			String schoolEmailToConfirm) {
		String body = """
				Hi {{firstName}},

				Thanks for pre-registering for PeachHacks! You are on the list.
				%s
				We will email you as soon as full registration opens, along with dates, venue details and everything else you need to know.

				See you soon,
				The PeachHacks team""".formatted(schoolInboxParagraph(schoolEmailToConfirm));
		sendInBackground(composer.compose(email, "You're pre-registered for PeachHacks", body, firstName, null,
				unsubscribeToken));
	}

	/** schoolEmailToConfirm is null when the school address is already confirmed. */
	public void sendRegistrationConfirmation(String email, String firstName, String unsubscribeToken,
			String schoolEmailToConfirm) {
		String body = """
				Hi {{firstName}},

				We received your application for PeachHacks. Thank you!
				%s
				It is now pending review. If you are accepted, we will email you your ticket: a QR code to show at check-in.

				See you soon,
				The PeachHacks team""".formatted(schoolInboxParagraph(schoolEmailToConfirm));
		sendInBackground(composer.compose(email, "We received your PeachHacks registration", body, firstName, null,
				unsubscribeToken));
	}

	private static String schoolInboxParagraph(String schoolEmail) {
		if (schoolEmail == null) {
			return "";
		}
		return "\nOne more step: look in your school inbox (" + schoolEmail
				+ ") for a confirmation link from us and open it, so we know you are a current student.\n";
	}

	/** Goes to the school address itself; the link is the only proof that the person can read it. */
	public void sendSchoolEmailConfirmation(String schoolEmail, String firstName, String confirmUrl,
			int validDays) {
		String body = """
				Hi {{firstName}},

				You gave this address as your school email for PeachHacks. PeachHacks is open to current university students, so please confirm that it is yours.

				%s

				The link works for %d days. If you did not sign up for PeachHacks, ignore this email and nothing will happen.

				The PeachHacks team""".formatted(EmailComposer.BLOCK_MARKER, validDays);
		String escapedUrl = HtmlUtils.htmlEscape(confirmUrl, "UTF-8");
		String html = "<p style=\"margin:0 0 16px\"><a href=\"" + escapedUrl + "\" style=\"display:inline-block;"
				+ "background:#e8703a;color:#ffffff;font-size:18px;font-weight:bold;text-decoration:none;"
				+ "padding:14px 28px;border-radius:8px\">Confirm my school email</a></p>"
				+ "<p style=\"font-size:14px;line-height:1.5;margin:0 0 16px;word-break:break-all\">" + escapedUrl
				+ "</p>";
		EmailComposer.Block block = new EmailComposer.Block(html, "Confirm your school email: " + confirmUrl);
		sendInBackground(composer.compose(schoolEmail, "Confirm your school email for PeachHacks", body, firstName, null,
				null, block));
	}

	/**
	 * The QR code travels inside the message (shown inline and listed as an attachment)
	 * because many mail clients block images loaded from a server.
	 */
	public void sendTicket(String email, String firstName, String ticketUrl, byte[] qrPng, String googleWalletUrl,
			String unsubscribeToken) {
		String body = """
				Hi {{firstName}},

				You're in! Your application to PeachHacks has been accepted, and we can't wait to see you.

				Below is your ticket. Open it on your phone and show the QR code when you arrive; we scan the same code at workshops.

				%s

				Keep this email. If the QR code does not show above, it is attached as an image, and the link always works.

				See you soon,
				The PeachHacks team""".formatted(EmailComposer.BLOCK_MARKER);
		String escapedUrl = HtmlUtils.htmlEscape(ticketUrl, "UTF-8");
		String html = "<p style=\"margin:0 0 16px\"><a href=\"" + escapedUrl + "\" style=\"display:inline-block;"
				+ "background:#e8703a;color:#ffffff;font-size:18px;font-weight:bold;text-decoration:none;"
				+ "padding:14px 28px;border-radius:8px\">Open your ticket</a></p>"
				+ "<p style=\"margin:0 0 16px\"><img src=\"cid:" + TICKET_CONTENT_ID + "\" width=\"240\" height=\"240\""
				+ " alt=\"Your PeachHacks ticket QR code\" style=\"display:block;border:0\"></p>"
				+ "<p style=\"font-size:14px;line-height:1.5;margin:0 0 16px;word-break:break-all\">" + escapedUrl
				+ "</p>";
		String text = "Your ticket: " + ticketUrl;
		if (googleWalletUrl != null) {
			html += "<p style=\"font-size:16px;line-height:1.5;margin:0 0 16px\"><a href=\""
					+ HtmlUtils.htmlEscape(googleWalletUrl, "UTF-8")
					+ "\" style=\"color:#e8703a;font-weight:bold\">Add to Google Wallet</a></p>";
			text += "\n\nAdd to Google Wallet: " + googleWalletUrl;
		}
		EmailComposer.Block block = new EmailComposer.Block(html, text);
		EmailMessage message = composer
			.compose(email, "You're in! Your PeachHacks ticket", body, firstName, null, unsubscribeToken, block)
			.withAttachments(List.of(
					new EmailMessage.Attachment("peachhacks-ticket.png", "image/png", qrPng, TICKET_CONTENT_ID)));
		sendInBackground(message);
	}

	public void sendAdminWelcome(String email, String name, String addedBy) {
		String body = """
				Hi {{firstName}},

				%s added you as an admin for PeachHacks.

				Sign in here with this email address:
				%s

				Your password was set by the person who added you, so ask them for it. If you were not expecting this, you can ignore this email.

				The PeachHacks team""".formatted(addedBy, properties.adminBaseUrl());
		sendInBackground(composer.compose(email, "You've been added as a PeachHacks admin", body, name, null, null));
	}

	public void sendVolunteerWelcome(String email, String name, String addedBy) {
		String body = """
				Hi {{firstName}},

				%s added you as a check-in volunteer for PeachHacks.

				On the day, you can look hackers up by name or email and check them in as they arrive. Your account only opens the check-in screen; if someone is not on the list or something looks wrong, ask an organizer.

				Sign in here with this email address:
				%s

				Your password was set by the person who added you, so ask them for it. If you were not expecting this, you can ignore this email.

				The PeachHacks team""".formatted(addedBy, properties.adminBaseUrl());
		sendInBackground(
				composer.compose(email, "You've been added as a PeachHacks check-in volunteer", body, name, null, null));
	}

	private void sendInBackground(EmailMessage message) {
		try {
			mailExecutor.execute(() -> {
				try {
					sender.send(message);
				}
				catch (RuntimeException ex) {
					log.warn("Could not send \"{}\" to {}: {}", message.subject(), message.to(), ex.toString());
				}
			});
		}
		catch (TaskRejectedException ex) {
			log.warn("Mail queue is full; dropped \"{}\" to {}", message.subject(), message.to());
		}
	}

}
