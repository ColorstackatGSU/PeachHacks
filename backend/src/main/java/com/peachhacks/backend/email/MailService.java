package com.peachhacks.backend.email;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import com.peachhacks.backend.config.EmailProperties;
import com.peachhacks.backend.email.EmailComposer.Content;
import com.peachhacks.backend.email.EmailComposer.Footer;
import com.peachhacks.backend.email.EmailComposer.InlineImage;

/**
 * The emails the system sends on its own. They are essential: always delivered, with no
 * unsubscribe link.
 */
@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private static final String TICKET_CONTENT_ID = "peachhacks-ticket";

	private static final String SIGN_OFF = "See you soon,\nThe PeachHacks team";

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
	public void sendPreRegistrationConfirmation(String email, String firstName, String schoolEmailToConfirm) {
		List<String> paragraphs = new ArrayList<>();
		paragraphs.add(greeting(firstName));
		paragraphs.add("Thanks for pre-registering for PeachHacks! You are on the list.");
		addSchoolInboxParagraph(paragraphs, schoolEmailToConfirm);
		paragraphs.add("We will email you as soon as full registration opens, along with dates, venue details and"
				+ " everything else you need to know.");
		paragraphs.add(SIGN_OFF);
		Content content = Content.of("You are on the list. We will email you when registration opens.",
				"You're pre-registered", paragraphs);
		sendInBackground(composer.compose(email, "You're pre-registered for PeachHacks", content, Footer.SIGNED_UP));
	}

	/** schoolEmailToConfirm is null when the school address is already confirmed. */
	public void sendRegistrationConfirmation(String email, String firstName, String schoolEmailToConfirm) {
		List<String> paragraphs = new ArrayList<>();
		paragraphs.add(greeting(firstName));
		paragraphs.add("We received your application for PeachHacks. Thank you!");
		addSchoolInboxParagraph(paragraphs, schoolEmailToConfirm);
		paragraphs.add("It is now pending review. If you are accepted, we will email you your ticket: a QR code to"
				+ " show at check-in.");
		paragraphs.add(SIGN_OFF);
		Content content = Content.of("Your application is in and pending review.", "We received your registration",
				paragraphs);
		sendInBackground(
				composer.compose(email, "We received your PeachHacks registration", content, Footer.REGISTERED));
	}

	private static void addSchoolInboxParagraph(List<String> paragraphs, String schoolEmail) {
		if (schoolEmail != null) {
			paragraphs.add("One more step: look in your school inbox (" + schoolEmail
					+ ") for a confirmation link from us and open it, so we know you are a current student.");
		}
	}

	/**
	 * Goes to the school address itself; the link is the only proof that the person can read
	 * it. That inbox has never heard from us, so the message opens with who we are and which
	 * sign-up it belongs to, naming the personal address only in masked form.
	 */
	public void sendSchoolEmailConfirmation(String schoolEmail, String firstName, String personalEmail,
			String confirmUrl, int validDays) {
		Content content = Content
			.of("Someone signed up for PeachHacks with this school address. Confirm that it is yours.",
					"Confirm your school email",
					List.of(greeting(firstName),
							"You, or someone using this address, signed up for PeachHacks, a student hackathon run by"
									+ " ColorStack at Georgia State University, with the personal email "
									+ mask(personalEmail) + ".",
							"PeachHacks is for current students, so we ask everyone to confirm a school email"
									+ " address. If this address is yours, confirm it here:"))
			.withPrimary("Confirm your school email", confirmUrl)
			.withClosing(List.of("The link works for " + validDays + " days.",
					"If this was not you, you can ignore this email and nothing will be confirmed.",
					"The PeachHacks team"));
		sendInBackground(composer.compose(schoolEmail, "Confirm your school email for PeachHacks", content,
				Footer.SCHOOL_EMAIL));
	}

	/** j***@gmail.com: enough for the owner to recognise, not enough to tell anyone else. */
	static String mask(String email) {
		int at = (email != null) ? email.lastIndexOf('@') : -1;
		if (at < 1) {
			return "the one you signed up with";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}

	/**
	 * The QR code travels inside the message (shown inline and listed as an attachment)
	 * because many mail clients block images loaded from a server.
	 */
	public void sendTicket(String email, String firstName, String ticketUrl, byte[] qrPng, String googleWalletUrl) {
		sendInBackground(ticketMessage(email, firstName, ticketUrl, qrPng, googleWalletUrl));
	}

	/**
	 * Sends on the calling thread so the caller knows the provider took the message.
	 * @throws RuntimeException when it did not
	 */
	public void sendTicketNow(String email, String firstName, String ticketUrl, byte[] qrPng,
			String googleWalletUrl) {
		sender.send(ticketMessage(email, firstName, ticketUrl, qrPng, googleWalletUrl));
	}

	private EmailMessage ticketMessage(String email, String firstName, String ticketUrl, byte[] qrPng,
			String googleWalletUrl) {
		Content content = Content
			.of("Your application was accepted. Your ticket is inside.", "You're in!",
					List.of(greeting(firstName),
							"Your application to PeachHacks has been accepted, and we can't wait to see you.",
							"Below is your ticket. Open it on your phone and show the QR code when you arrive; we"
									+ " scan the same code at workshops."))
			.withPrimary("View your ticket", ticketUrl)
			.withImage(new InlineImage(TICKET_CONTENT_ID, "Your PeachHacks ticket QR code", 240))
			.withClosing(List.of(
					"Keep this email. If the QR code does not show above, it is attached as an image, and the"
							+ " link always works.",
					SIGN_OFF));
		if (googleWalletUrl != null) {
			content = content.withSecondary("Add to Google Wallet", googleWalletUrl);
		}
		return composer.compose(email, "You're in! Your PeachHacks ticket", content, Footer.REGISTERED)
			.withAttachments(List.of(
					new EmailMessage.Attachment("peachhacks-ticket.png", "image/png", qrPng, TICKET_CONTENT_ID)));
	}

	public String adminPasswordUrl(String token) {
		return properties.adminBaseUrl() + "/#/set-password?token=" + token;
	}

	public void sendAdminInvite(String email, String name, String addedBy, String token, Duration validFor) {
		Content content = Content
			.of(addedBy + " added you as an admin for PeachHacks. Choose a password to finish.",
					"You're a PeachHacks admin",
					List.of(greeting(name), addedBy + " added you as an admin for PeachHacks.",
							"Choose a password to finish setting up your account. You will sign in with this"
									+ " email address."))
			.withPrimary("Set your password", adminPasswordUrl(token))
			.withClosing(inviteClosing(validFor, addedBy));
		sendInBackground(
				composer.compose(email, "You've been added as a PeachHacks admin", content, Footer.ADMIN_ACCOUNT));
	}

	public void sendVolunteerInvite(String email, String name, String addedBy, String token, Duration validFor) {
		Content content = Content
			.of(addedBy + " added you as a check-in volunteer for PeachHacks. Choose a password to finish.",
					"You're a check-in volunteer",
					List.of(greeting(name), addedBy + " added you as a check-in volunteer for PeachHacks.",
							"On the day, you can look hackers up by name or email and check them in as they"
									+ " arrive. Your account only opens the check-in screen; if someone is not on"
									+ " the list or something looks wrong, ask an organizer.",
							"Choose a password to finish setting up your account. You will sign in with this"
									+ " email address."))
			.withPrimary("Set your password", adminPasswordUrl(token))
			.withClosing(inviteClosing(validFor, addedBy));
		sendInBackground(composer.compose(email, "You've been added as a PeachHacks check-in volunteer", content,
				Footer.ADMIN_ACCOUNT));
	}

	public void sendPasswordReset(String email, String name, String token, Duration validFor) {
		Content content = Content
			.of("Use this link to choose a new password for the PeachHacks admin site.", "Reset your password",
					List.of(greeting(name),
							"Someone asked to reset the password for your account on the PeachHacks admin site."
									+ " If that was you, choose a new one here:"))
			.withPrimary("Choose a new password", adminPasswordUrl(token))
			.withClosing(List.of("The link works for " + validity(validFor) + " and can be used once.",
					"If this was not you, you can ignore this email and your password stays the same.",
					"The PeachHacks team"));
		sendInBackground(
				composer.compose(email, "Reset your PeachHacks admin password", content, Footer.ADMIN_ACCOUNT));
	}

	private static List<String> inviteClosing(Duration validFor, String addedBy) {
		return List.of(
				"The link works for " + validity(validFor) + " and can be used once. If it has expired, ask "
						+ addedBy + " to send a new one.",
				"If you were not expecting this, you can ignore this email.", "The PeachHacks team");
	}

	static String validity(Duration duration) {
		long days = duration.toDays();
		if (days >= 1) {
			return (days == 1) ? "1 day" : days + " days";
		}
		long hours = duration.toHours();
		if (hours >= 1) {
			return (hours == 1) ? "1 hour" : hours + " hours";
		}
		return Math.max(1, duration.toMinutes()) + " minutes";
	}

	private static String greeting(String firstName) {
		return (firstName != null && !firstName.isBlank()) ? "Hi " + firstName.strip() + "," : "Hi,";
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
