package com.peachhacks.backend.acceptance;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.config.EmailProperties;
import com.peachhacks.backend.email.MailService;
import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationRepository;
import com.peachhacks.backend.registration.RegistrationStatus;
import com.peachhacks.backend.ticket.GoogleWallet;
import com.peachhacks.backend.ticket.Tickets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Sends the acceptance ("You're in") email, to one person or to everyone still waiting,
 * and records that it went out. Progress of a run is kept in memory: the record of who
 * was told is in the database, so a run cut short by a restart loses only its counters
 * and the people it had not reached are still waiting.
 */
@Service
public class AcceptanceMailer {

	public enum State {

		IDLE, SENDING

	}

	/**
	 * The current run while state is SENDING, otherwise the last one since startup (all
	 * zero and null if there was none). skipped counts people who stopped waiting between
	 * the start of the run and their turn.
	 */
	public record SendStatus(State state, int queued, int sent, int failed, int skipped, Instant startedAt,
			Instant finishedAt, String startedBy) {

		static final SendStatus NONE = new SendStatus(State.IDLE, 0, 0, 0, 0, null, null, null);

	}

	public record Started(int queued, SendStatus send) {
	}

	private enum Outcome {

		SENT, FAILED, SKIPPED

	}

	private static final Logger log = LoggerFactory.getLogger(AcceptanceMailer.class);

	private final RegistrationRepository registrations;

	private final JdbcClient jdbc;

	private final MailService mailService;

	private final Tickets tickets;

	private final GoogleWallet googleWallet;

	private final TaskExecutor campaignExecutor;

	private final Duration delay;

	private final AtomicBoolean running = new AtomicBoolean();

	// Registrations whose acceptance email is being sent right now, so the run and the
	// one-person send can never both mail the same person.
	private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

	private volatile SendStatus status = SendStatus.NONE;

	public AcceptanceMailer(RegistrationRepository registrations, JdbcClient jdbc, MailService mailService,
			Tickets tickets, GoogleWallet googleWallet, @Qualifier("campaignExecutor") TaskExecutor campaignExecutor,
			EmailProperties properties) {
		this.registrations = registrations;
		this.jdbc = jdbc;
		this.mailService = mailService;
		this.tickets = tickets;
		this.googleWallet = googleWallet;
		this.campaignExecutor = campaignExecutor;
		this.delay = properties.campaignDelay();
	}

	public SendStatus status() {
		return status;
	}

	/**
	 * Queues the acceptance email for everyone accepted and not yet told. Runs on the
	 * campaign executor, so it shares the provider rate limit with campaigns and waits
	 * behind one that is already sending.
	 */
	public Started start(String startedBy) {
		if (!running.compareAndSet(false, true)) {
			throw new ApiException(HttpStatus.CONFLICT, "ACCEPTANCE_SEND_RUNNING",
					"Acceptance emails are already being sent. Wait for that to finish.");
		}
		SendStatus previous = status;
		try {
			List<UUID> ids = jdbc.sql("""
					select id from registrations
					where status = 'ACCEPTED' and acceptance_notified_at is null
					order by accepted_at asc nulls first, id
					""").query(UUID.class).list();
			if (ids.isEmpty()) {
				running.set(false);
				return new Started(0, previous);
			}
			SendStatus started = new SendStatus(State.SENDING, ids.size(), 0, 0, 0, Instant.now(), null, startedBy);
			status = started;
			campaignExecutor.execute(() -> run(ids, started));
			log.info("Queued {} acceptance email(s), started by {}", ids.size(), startedBy);
			return new Started(ids.size(), started);
		}
		catch (RuntimeException ex) {
			status = previous;
			running.set(false);
			throw ex;
		}
	}

	private void run(List<UUID> ids, SendStatus started) {
		int sent = 0;
		int failed = 0;
		int skipped = 0;
		try {
			for (int i = 0; i < ids.size(); i++) {
				Outcome outcome = sendWaiting(ids.get(i));
				switch (outcome) {
					case SENT -> sent++;
					case FAILED -> failed++;
					case SKIPPED -> skipped++;
				}
				status = progress(started, State.SENDING, sent, failed, skipped, null);
				if (outcome != Outcome.SKIPPED && i < ids.size() - 1 && !delay.isZero()) {
					Thread.sleep(delay);
				}
			}
			log.info("Acceptance emails finished: {} sent, {} failed, {} skipped", sent, failed, skipped);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Acceptance emails interrupted after {} sent, {} failed", sent, failed);
		}
		finally {
			status = progress(started, State.IDLE, sent, failed, skipped, Instant.now());
			running.set(false);
		}
	}

	private static SendStatus progress(SendStatus started, State state, int sent, int failed, int skipped,
			Instant finishedAt) {
		return new SendStatus(state, started.queued(), sent, failed, skipped, started.startedAt(), finishedAt,
				started.startedBy());
	}

	/** Never throws: one person's failure must not stop the run. */
	private Outcome sendWaiting(UUID id) {
		if (!inFlight.add(id)) {
			return Outcome.SKIPPED;
		}
		try {
			Registration registration = registrations.findById(id).orElse(null);
			if (registration == null || !isWaiting(registration)) {
				return Outcome.SKIPPED;
			}
			deliver(registration);
			return Outcome.SENT;
		}
		catch (RuntimeException ex) {
			log.warn("Could not send the acceptance email for registration {}: {}", id, ex.toString());
			return Outcome.FAILED;
		}
		finally {
			inFlight.remove(id);
		}
	}

	/**
	 * The per-person action. Someone already told gets the same email again in the
	 * background; someone still waiting is told now and leaves the bucket, and the call
	 * fails if the provider does not take the message.
	 */
	public void sendTicketEmail(UUID id) {
		Registration registration = registrations.findById(id)
			.orElseThrow(() -> ApiException.notFound("Registration not found."));
		if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
			throw ApiException.validation("Only accepted registrations have a ticket to send.", null);
		}
		String ticketUrl = tickets.url(registration.getTicketToken());
		if (registration.getAcceptanceNotifiedAt() != null) {
			mailService.sendTicket(registration.getEmail(), registration.getFirstName(), ticketUrl,
					tickets.qrPng(registration.getTicketToken()),
					googleWallet.saveUrl(registration, ticketUrl).orElse(null));
			return;
		}
		if (!inFlight.add(id)) {
			throw new ApiException(HttpStatus.CONFLICT, "ACCEPTANCE_SEND_RUNNING",
					"This person's acceptance email is being sent right now.");
		}
		try {
			deliver(registration);
		}
		catch (RuntimeException ex) {
			log.warn("Could not send the acceptance email for registration {}: {}", id, ex.toString());
			throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_FAILED",
					"The email provider did not take the acceptance email, so this person is still waiting to be told.");
		}
		finally {
			inFlight.remove(id);
		}
	}

	private static boolean isWaiting(Registration registration) {
		return registration.getStatus() == RegistrationStatus.ACCEPTED
				&& registration.getAcceptanceNotifiedAt() == null;
	}

	private void deliver(Registration registration) {
		String token = registration.getTicketToken();
		String ticketUrl = tickets.url(token);
		mailService.sendTicketNow(registration.getEmail(), registration.getFirstName(), ticketUrl,
				tickets.qrPng(token), googleWallet.saveUrl(registration, ticketUrl).orElse(null));
		if (registrations.markAcceptanceNotified(registration.getId(), Instant.now()) == 0) {
			log.warn("Registration {} stopped waiting while its acceptance email was being sent",
					registration.getId());
		}
	}

}
