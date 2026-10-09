package com.peachhacks.backend.platform;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.peachhacks.backend.common.ClientAddress;
import com.peachhacks.backend.common.RateLimiter;
import com.peachhacks.backend.config.DiscordProperties;
import com.peachhacks.backend.config.PlatformProperties;
import com.peachhacks.backend.discord.DiscordVerification;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The hacker platform's API. Everything except /platform/config and /platform/auth needs
 * a hacker session: Authorization: Bearer with the token a sign-in returned.
 */
@RestController
@RequestMapping("/platform")
public class PlatformController {

	/** googleSignIn says whether "Continue with Google" is offered. discordClientId is null while "Connect Discord" is not set up. */
	public record Config(boolean googleSignIn, String discordClientId, String discordRedirectUri,
			int maxTeamSize) {
	}

	public record LoginRequest(String email, String password) {
	}

	public record EmailRequest(String email) {
	}

	public record SetPasswordRequest(String token, String password) {
	}

	public record GoogleClaim(String handoff) {
	}

	public record DiscordRequest(String code) {
	}

	public record JoinMessage(String message) {
	}

	private static final int GOOGLE_STARTS_PER_MINUTE = 20;

	private final HackerAuth auth;

	private final PlatformService platform;

	private final DiscordVerification discord;

	private final PlatformProperties properties;

	private final DiscordProperties discordProperties;

	private final GoogleIdentity google;

	private final RateLimiter rateLimiter;

	private final ClientAddress clientAddress;

	public PlatformController(HackerAuth auth, PlatformService platform, DiscordVerification discord,
			PlatformProperties properties, DiscordProperties discordProperties, GoogleIdentity google,
			RateLimiter rateLimiter, ClientAddress clientAddress) {
		this.rateLimiter = rateLimiter;
		this.clientAddress = clientAddress;
		this.auth = auth;
		this.platform = platform;
		this.discord = discord;
		this.properties = properties;
		this.discordProperties = discordProperties;
		this.google = google;
	}

	@GetMapping("/config")
	Config config() {
		return new Config(google.enabled(),
				discordProperties.oauthConfigured() ? discordProperties.applicationId() : null,
				properties.discordRedirectUri(), properties.maxTeamSize());
	}

	@PostMapping("/auth/login")
	HackerAuth.Session login(@RequestBody LoginRequest request) {
		return auth.login(request.email(), request.password());
	}

	@PostMapping("/auth/password-link")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void passwordLink(@RequestBody EmailRequest request) {
		auth.requestPasswordLink(request.email());
	}

	@PostMapping("/auth/set-password")
	HackerAuth.Session setPassword(@RequestBody SetPasswordRequest request) {
		return auth.setPassword(request.token(), request.password());
	}

	/** A page navigation, not an API call: the whole tab goes to Google and comes back to the callback. */
	@GetMapping("/auth/google/start")
	ResponseEntity<Void> googleStart(HttpServletRequest request) {
		if (!rateLimiter.tryAcquire("google-start:" + clientAddress.of(request), GOOGLE_STARTS_PER_MINUTE)) {
			return redirect(properties.baseUrl() + "/?google_error=failed");
		}
		return redirect(auth.googleStart());
	}

	@GetMapping("/auth/google/callback")
	ResponseEntity<Void> googleCallback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error) {
		return redirect(auth.googleCallback(code, state, error));
	}

	@PostMapping("/auth/google/claim")
	HackerAuth.Session googleClaim(@RequestBody GoogleClaim request) {
		return auth.googleClaim(request.handoff());
	}

	@PostMapping("/auth/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request) {
		auth.logout(request);
	}

	@GetMapping("/me")
	ResponseEntity<PlatformService.Me> me(HttpServletRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(platform.me(auth.require(request)));
	}

	@PatchMapping("/me")
	PlatformService.Me updateProfile(HttpServletRequest request, @RequestBody PlatformService.ProfileUpdate update) {
		UUID id = auth.require(request);
		platform.updateProfile(id, update);
		return platform.me(id);
	}

	@PostMapping("/discord")
	Map<String, String> connectDiscord(HttpServletRequest request, @RequestBody DiscordRequest body) {
		UUID id = auth.require(request);
		return Map.of("discordUsername", discord.connect(id, body.code(), properties.discordRedirectUri()));
	}

	@GetMapping("/hackers")
	List<PlatformService.Hacker> hackers(HttpServletRequest request) {
		auth.require(request);
		return platform.hackers();
	}

	@GetMapping("/teams")
	List<PlatformService.Team> teams(HttpServletRequest request) {
		return platform.teams(auth.require(request));
	}

	@PostMapping("/teams")
	@ResponseStatus(HttpStatus.CREATED)
	Map<String, UUID> createTeam(HttpServletRequest request, @RequestBody PlatformService.TeamRequest team) {
		return Map.of("id", platform.createTeam(auth.require(request), team));
	}

	@PatchMapping("/teams/mine")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateTeam(HttpServletRequest request, @RequestBody PlatformService.TeamRequest team) {
		platform.updateTeam(auth.require(request), team);
	}

	@PostMapping("/teams/mine/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void leaveTeam(HttpServletRequest request) {
		platform.leaveTeam(auth.require(request));
	}

	@DeleteMapping("/teams/mine/members/{memberId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeMember(HttpServletRequest request, @PathVariable UUID memberId) {
		platform.removeMember(auth.require(request), memberId);
	}

	@PostMapping("/teams/mine/requests/{requestId}/accept")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void acceptRequest(HttpServletRequest request, @PathVariable UUID requestId) {
		platform.answerRequest(auth.require(request), requestId, true);
	}

	@PostMapping("/teams/mine/requests/{requestId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void declineRequest(HttpServletRequest request, @PathVariable UUID requestId) {
		platform.answerRequest(auth.require(request), requestId, false);
	}

	@PostMapping("/teams/{teamId}/requests")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void requestToJoin(HttpServletRequest request, @PathVariable UUID teamId,
			@RequestBody(required = false) JoinMessage body) {
		platform.requestToJoin(auth.require(request), teamId, (body != null) ? body.message() : null);
	}

	@DeleteMapping("/teams/{teamId}/requests")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdrawRequest(HttpServletRequest request, @PathVariable UUID teamId) {
		platform.withdrawRequest(auth.require(request), teamId);
	}

	private static ResponseEntity<Void> redirect(String url) {
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(url))
			.cacheControl(CacheControl.noStore())
			.build();
	}

}
