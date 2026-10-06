package com.peachhacks.backend.ticket;

import java.time.Duration;

import com.peachhacks.backend.checkin.CheckInService;
import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.registration.Registration;
import com.peachhacks.backend.registration.RegistrationRepository;
import com.peachhacks.backend.registration.RegistrationStatus;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A ticket exists only while its registration is ACCEPTED; anything else answers exactly
 * like an unknown token so the endpoints reveal nothing about other registrations.
 */
@RestController
@RequestMapping("/public/tickets")
public class PublicTicketController {

	public record Ticket(String firstName, String lastName, String school, boolean checkedIn,
			String googleWalletUrl) {
	}

	private final RegistrationRepository registrations;

	private final CheckInService checkIns;

	private final Tickets tickets;

	private final GoogleWallet googleWallet;

	public PublicTicketController(RegistrationRepository registrations, CheckInService checkIns, Tickets tickets,
			GoogleWallet googleWallet) {
		this.registrations = registrations;
		this.checkIns = checkIns;
		this.tickets = tickets;
		this.googleWallet = googleWallet;
	}

	@GetMapping("/{token}")
	ResponseEntity<Ticket> ticket(@PathVariable String token) {
		Registration r = accepted(token);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(new Ticket(r.getFirstName(), r.getLastName(), r.getSchool(), checkIns.generalCheckedIn(r.getId()),
					googleWallet.saveUrl(r, tickets.url(r.getTicketToken())).orElse(null)));
	}

	@GetMapping("/{token}/qr.png")
	ResponseEntity<byte[]> qr(@PathVariable String token) {
		Registration r = accepted(token);
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
			.body(tickets.qrPng(r.getTicketToken()));
	}

	private Registration accepted(String token) {
		return Tickets.tokenFrom(token)
			.flatMap(registrations::findByTicketToken)
			.filter(r -> r.getStatus() == RegistrationStatus.ACCEPTED)
			.orElseThrow(() -> ApiException.notFound("Ticket not found."));
	}

}
