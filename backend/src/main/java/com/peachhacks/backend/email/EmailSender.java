package com.peachhacks.backend.email;

public interface EmailSender {

	/** @throws RuntimeException when the message could not be handed to the provider */
	void send(EmailMessage message);

}
