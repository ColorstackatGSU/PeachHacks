package com.peachhacks.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import com.peachhacks.backend.config.EmailProperties;

@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

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

	public void sendPreRegistrationConfirmation(String email, String firstName, String unsubscribeToken) {
		String body = """
				Hi {{firstName}},

				Thanks for pre-registering for PeachHacks! You are on the list.

				We will email you as soon as full registration opens, along with dates, venue details and everything else you need to know.

				See you soon,
				The PeachHacks team""";
		sendInBackground(composer.compose(email, "You're pre-registered for PeachHacks", body, firstName, null,
				unsubscribeToken));
	}

	public void sendRegistrationConfirmation(String email, String firstName, String unsubscribeToken) {
		String body = """
				Hi {{firstName}},

				We received your registration for PeachHacks. Thank you!

				Your application is now pending review. We will email you with a decision and next steps.

				See you soon,
				The PeachHacks team""";
		sendInBackground(composer.compose(email, "We received your PeachHacks registration", body, firstName, null,
				unsubscribeToken));
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
