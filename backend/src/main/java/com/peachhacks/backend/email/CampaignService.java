package com.peachhacks.backend.email;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.config.EmailProperties;
import com.peachhacks.backend.email.AudienceService.Recipient;
import com.peachhacks.backend.email.EmailComposer.Footer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CampaignService {

	private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

	private final EmailCampaignRepository campaigns;

	private final AudienceService audiences;

	private final EmailComposer composer;

	private final EmailSender sender;

	private final TaskExecutor campaignExecutor;

	private final Duration delay;

	public CampaignService(EmailCampaignRepository campaigns, AudienceService audiences, EmailComposer composer,
			EmailSender sender, @Qualifier("campaignExecutor") TaskExecutor campaignExecutor,
			EmailProperties properties) {
		this.campaigns = campaigns;
		this.audiences = audiences;
		this.composer = composer;
		this.sender = sender;
		this.campaignExecutor = campaignExecutor;
		this.delay = properties.campaignDelay();
	}

	@EventListener(ApplicationReadyEvent.class)
	void failInterruptedCampaigns() {
		int count = campaigns.failInterrupted(Instant.now());
		if (count > 0) {
			log.warn("Marked {} email campaign(s) interrupted by a restart as FAILED", count);
		}
	}

	public List<EmailCampaign> list() {
		return campaigns.findAllByOrderByCreatedAtDesc();
	}

	/** Synchronous so the caller learns whether the send worked. */
	public void sendTest(String to, String name, CampaignKind kind, String subject, String body) {
		try {
			sender.send(sample(to, name, kind, subject, body));
		}
		catch (RuntimeException ex) {
			log.warn("Test email to {} failed: {}", to, ex.toString());
			throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_FAILED",
					"The email provider rejected the test email. Check the server logs.");
		}
	}

	/**
	 * The message as the named person would get it. An announcement shows the unsubscribe
	 * link, but it is not live: there is no recipient whose token it could carry.
	 */
	public EmailMessage sample(String to, String name, CampaignKind kind, String subject, String body) {
		String[] names = Texts.orEmpty(name).split("\\s+", 2);
		String lastName = (names.length > 1) ? names[1] : "";
		Footer footer = (kind == CampaignKind.EVENT_UPDATE) ? Footer.REGISTERED : Footer.announcementSample();
		return composer.composeCampaign(to, subject, body, names[0], lastName, footer);
	}

	public EmailCampaign start(CampaignKind kind, Audience audience, String school, String subject, String body,
			String createdBy) {
		String schoolFilter = Texts.clean(school);
		List<Recipient> recipients = audiences.recipients(kind, audience, schoolFilter);
		if (recipients.isEmpty()) {
			throw ApiException.invalidField("audience", "Nobody matches this audience, so there is nothing to send.");
		}
		EmailCampaign campaign = campaigns
			.save(new EmailCampaign(kind, subject.strip(), body, audience, schoolFilter, recipients.size(),
					createdBy));
		UUID id = campaign.getId();
		try {
			campaignExecutor.execute(() -> run(id, kind, subject, body, recipients));
		}
		catch (RuntimeException ex) {
			campaigns.complete(id, EmailCampaign.Status.FAILED, 0, 0, Instant.now());
			throw ex;
		}
		log.info("Queued email campaign {} ({}) to {} recipient(s), audience {}", id, kind, recipients.size(),
				audience);
		return campaign;
	}

	private void run(UUID id, CampaignKind kind, String subject, String body, List<Recipient> recipients) {
		int sent = 0;
		int failed = 0;
		try {
			campaigns.updateProgress(id, EmailCampaign.Status.SENDING, 0, 0);
			for (int i = 0; i < recipients.size(); i++) {
				Recipient recipient = recipients.get(i);
				try {
					Footer footer = (kind == CampaignKind.EVENT_UPDATE) ? Footer.REGISTERED
							: Footer.announcement(recipient.unsubscribeToken());
					sender.send(composer.composeCampaign(recipient.email(), subject, body, recipient.firstName(),
							recipient.lastName(), footer));
					sent++;
				}
				catch (RuntimeException ex) {
					failed++;
					log.warn("Campaign {}: could not send to {}: {}", id, recipient.email(), ex.toString());
				}
				campaigns.updateProgress(id, EmailCampaign.Status.SENDING, sent, failed);
				if (i < recipients.size() - 1 && !delay.isZero()) {
					Thread.sleep(delay);
				}
			}
			EmailCampaign.Status status = (sent > 0) ? EmailCampaign.Status.SENT : EmailCampaign.Status.FAILED;
			campaigns.complete(id, status, sent, failed, Instant.now());
			log.info("Email campaign {} finished: {} sent, {} failed", id, sent, failed);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			finishAsFailed(id, sent, failed);
		}
		catch (RuntimeException ex) {
			log.error("Email campaign {} aborted", id, ex);
			finishAsFailed(id, sent, failed);
		}
	}

	private void finishAsFailed(UUID id, int sent, int failed) {
		try {
			campaigns.complete(id, EmailCampaign.Status.FAILED, sent, failed, Instant.now());
		}
		catch (RuntimeException ex) {
			log.error("Could not mark email campaign {} as failed", id, ex);
		}
	}

}
