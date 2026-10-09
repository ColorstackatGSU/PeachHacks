package com.peachhacks.backend.sponsor;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.RequestValidator;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.config.SponsorProperties;
import com.peachhacks.backend.email.MailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sponsor form submissions are not stored: each one becomes an email to the sponsor
 * inbox, with Reply-To set to the sender. It is sent before the request is answered, so
 * a failed send reaches the visitor, whose form still holds what they typed.
 */
@Service
public class SponsorInquiryService {

	private static final Logger log = LoggerFactory.getLogger(SponsorInquiryService.class);

	private final RequestValidator validator;

	private final MailService mailService;

	private final SponsorProperties properties;

	public SponsorInquiryService(RequestValidator validator, MailService mailService, SponsorProperties properties) {
		this.validator = validator;
		this.mailService = mailService;
		this.properties = properties;
	}

	public void submit(SponsorInquiryRequest request) {
		if (Texts.clean(request.website()) != null) {
			return;
		}
		validator.validate(request);
		String organization = request.organization().strip();
		try {
			mailService.sendSponsorInquiryNow(properties.inbox(), organization, request.name().strip(),
					Texts.email(request.email()), Texts.clean(request.message()));
		}
		catch (RuntimeException ex) {
			log.warn("Could not send the sponsor inquiry from {}: {}", organization, ex.toString());
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_FAILED",
					"We couldn't send your message right now. Please try again, or email us directly.");
		}
	}

}
