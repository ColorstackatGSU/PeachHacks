package com.peachhacks.backend.email;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.common.PageResponse;
import com.peachhacks.backend.common.Texts;
import com.peachhacks.backend.config.EmailProperties;
import com.peachhacks.backend.email.AudienceService.Recipient;
import com.peachhacks.backend.email.CampaignRecipients.Pending;
import com.peachhacks.backend.email.EmailComposer.Footer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A campaign's recipients are written to campaign_recipients when it is started and the
 * send works through the rows that are still PENDING. That is what lets a campaign cut
 * short by a restart carry on where it stopped.
 */
@Service
public class CampaignService {

	private static final int BATCH_SIZE = 100;

	private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

	private final EmailCampaignRepository campaigns;

	private final CampaignRecipients recipients;

	private final AudienceService audiences;

	private final EmailComposer composer;

	private final EmailSender sender;

	private final TaskExecutor campaignExecutor;

	private final TransactionTemplate transaction;

	private final Duration delay;

	private volatile boolean stopping;

	public CampaignService(EmailCampaignRepository campaigns, CampaignRecipients recipients,
			AudienceService audiences, EmailComposer composer, EmailSender sender,
			@Qualifier("campaignExecutor") TaskExecutor campaignExecutor, EmailProperties properties,
			PlatformTransactionManager transactionManager) {
		this.campaigns = campaigns;
		this.recipients = recipients;
		this.audiences = audiences;
		this.composer = composer;
		this.sender = sender;
		this.campaignExecutor = campaignExecutor;
		this.transaction = new TransactionTemplate(transactionManager);
		this.delay = properties.campaignDelay();
	}

	/**
	 * Campaigns a previous process left QUEUED or SENDING are queued again and go to the
	 * people still PENDING. One from before campaign_recipients existed has no rows to
	 * work from and is marked FAILED with the counts it had reached.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void resumeInterrupted() {
		for (EmailCampaign campaign : campaigns.findUnfinished()) {
			UUID id = campaign.getId();
			if (!recipients.exist(id)) {
				campaigns.fail(id, Instant.now());
				log.warn("Email campaign {} was interrupted by a restart and has no recipient rows; marked FAILED",
						id);
				continue;
			}
			log.info("Resuming email campaign {} ({}, \"{}\") interrupted by a restart", id, campaign.getKind(),
					campaign.getSubject());
			campaignExecutor.execute(() -> run(id));
		}
	}

	/** The run stops after the email in hand and leaves the campaign unfinished for the next start. */
	@EventListener(ContextClosedEvent.class)
	void stop() {
		stopping = true;
	}

	public List<EmailCampaign> list() {
		return campaigns.findAllByOrderByCreatedAtDesc();
	}

	public PageResponse<CampaignRecipients.View> recipients(UUID id, CampaignRecipients.Status status, int page,
			int size) {
		if (!campaigns.existsById(id)) {
			throw ApiException.notFound("Email campaign not found.");
		}
		return recipients.page(id, status, PageResponse.pageable(page, size));
	}

	/** Synchronous so the caller learns whether the send worked. */
	public void sendTest(String to, String name, CampaignKind kind, String subject, String body) {
		try {
			sender.send(sample(to, name, kind, subject, body));
		}
		catch (RuntimeException ex) {
			log.warn("Test email ({}, \"{}\") to {} failed: {}", kind, subject, to, ex.toString());
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

	/** Synchronized so two identical requests arriving together cannot both pass the duplicate check. */
	public synchronized EmailCampaign start(CampaignKind kind, Audience audience, String school, String subject,
			String body, String createdBy) {
		String schoolFilter = Texts.clean(school);
		String cleanSubject = subject.strip();
		List<Recipient> people = audiences.recipients(kind, audience, schoolFilter);
		if (people.isEmpty()) {
			throw ApiException.invalidField("audience", "Nobody matches this audience, so there is nothing to send.");
		}
		if (campaigns.existsUnfinishedCopy(kind, audience, Texts.orEmpty(schoolFilter), cleanSubject, body)) {
			throw new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_ALREADY_SENDING",
					"This exact email is already being sent to this audience. Wait for it to finish before"
							+ " sending it again.");
		}
		EmailCampaign campaign = transaction.execute(tx -> {
			EmailCampaign saved = campaigns.saveAndFlush(
					new EmailCampaign(kind, cleanSubject, body, audience, schoolFilter, people.size(), createdBy));
			recipients.add(saved.getId(), people);
			return saved;
		});
		UUID id = campaign.getId();
		try {
			campaignExecutor.execute(() -> run(id));
		}
		catch (RuntimeException ex) {
			campaigns.fail(id, Instant.now());
			throw ex;
		}
		log.info("Queued email campaign {} ({}) to {} recipient(s), audience {}", id, kind, people.size(), audience);
		return campaign;
	}

	private void run(UUID id) {
		try {
			EmailCampaign campaign = stopping ? null : campaigns.findById(id).orElse(null);
			if (campaign == null) {
				return;
			}
			recipients.recordProgress(id);
			long lastId = 0;
			boolean first = true;
			for (List<Pending> batch = recipients.pending(id, lastId, BATCH_SIZE); !batch.isEmpty(); batch = recipients
				.pending(id, lastId, BATCH_SIZE)) {
				for (Pending recipient : batch) {
					if (!first && !delay.isZero()) {
						Thread.sleep(delay);
					}
					if (stopping) {
						log.info("Email campaign {} paused by shutdown; it resumes at the next start", id);
						return;
					}
					deliver(campaign, recipient);
					lastId = recipient.id();
					first = false;
				}
			}
			recipients.complete(id);
			EmailCampaign finished = campaigns.findById(id).orElse(campaign);
			log.info("Email campaign {} finished: {} sent, {} failed", id, finished.getSentCount(),
					finished.getFailedCount());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Email campaign {} interrupted; it resumes at the next start", id);
		}
		catch (RuntimeException ex) {
			log.error("Email campaign {} aborted", id, ex);
			try {
				campaigns.fail(id, Instant.now());
			}
			catch (RuntimeException failure) {
				log.error("Could not mark email campaign {} as failed; it resumes at the next start", id, failure);
			}
		}
	}

	/**
	 * Never throws. The key makes a repeat of this row (a resume after a crash between the
	 * send and the mark, or two instances overlapping during a deploy) the same send to the
	 * provider. Not being able to record the outcome is logged and the campaign carries on.
	 */
	private void deliver(EmailCampaign campaign, Pending recipient) {
		UUID id = campaign.getId();
		String error = null;
		try {
			Footer footer = (campaign.getKind() == CampaignKind.EVENT_UPDATE) ? Footer.REGISTERED
					: Footer.announcement(recipient.unsubscribeToken());
			sender.send(composer
				.composeCampaign(recipient.email(), campaign.getSubject(), campaign.getBody(), recipient.firstName(),
						recipient.lastName(), footer)
				.withIdempotencyKey("campaign-" + id + "-" + recipient.id()));
		}
		catch (RuntimeException ex) {
			error = ex.toString();
			log.warn("Campaign {} ({}, \"{}\"): could not send to {}: {}", id, campaign.getKind(),
					campaign.getSubject(), recipient.email(), error);
		}
		try {
			if (error == null) {
				recipients.markSent(recipient.id());
			}
			else {
				recipients.markFailed(recipient.id(), error);
			}
			recipients.recordProgress(id);
		}
		catch (RuntimeException ex) {
			log.error("Campaign {}: could not record that the email to {} {}", id, recipient.email(),
					(error == null) ? "was sent" : "failed", ex);
		}
	}

}
