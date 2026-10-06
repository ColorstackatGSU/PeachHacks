package com.peachhacks.backend.email;

import com.peachhacks.backend.config.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmailConfig {

	private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

	@Bean
	EmailSender emailSender(EmailProperties properties) {
		if (properties.resendEnabled()) {
			log.info("Email delivery: Resend, from {}", properties.from());
			return new ResendEmailSender(properties.resendApiKey(), properties.from(), properties.resendBaseUrl());
		}
		log.info("Email delivery: logging only (RESEND_API_KEY is not set)");
		return new LoggingEmailSender();
	}

}
