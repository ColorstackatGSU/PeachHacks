package com.peachhacks.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(EmailMessage message) {
		log.info("Email not sent (RESEND_API_KEY is not set)\nTo: {}\nSubject: {}\nAttachments: {}\n\n{}",
				message.to(), message.subject(), message.attachments(), message.text());
	}

}
