package com.peachhacks.backend.email;

import com.peachhacks.backend.config.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class EmailConfig {

	private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

	@Bean
	EmailSender emailSender(EmailProperties properties, Environment environment) {
		if (properties.resendEnabled()) {
			log.info("Email delivery: Resend, from {}", properties.from());
			return new ResendEmailSender(properties.resendApiKey(), properties.from(), properties.resendBaseUrl());
		}
		if (environment.acceptsProfiles(Profiles.of("prod"))) {
			throw new IllegalStateException("RESEND_API_KEY is not set. The prod profile does not start without it,"
					+ " because every email (confirmations, tickets, password links) would be written to the log"
					+ " instead of being sent.");
		}
		log.info("Email delivery: logging only (RESEND_API_KEY is not set)");
		return new LoggingEmailSender();
	}

}
