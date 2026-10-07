package com.peachhacks.backend.discord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.config.DiscordProperties;
import com.peachhacks.backend.discord.DiscordVerification.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The URL Discord calls for every button press and form submission ("Interactions
 * Endpoint URL" in the Developer Portal). Each call must be answered within three seconds,
 * so the step that talks back to Discord is deferred and finished in the background.
 */
@RestController
public class DiscordInteractionController {

	private static final int PING = 1;

	private static final int COMPONENT = 3;

	private static final int MODAL_SUBMIT = 5;

	private static final int EPHEMERAL = 64;

	private static final String EMAIL_MODAL = "peachbot:email";

	private static final String ENTER_CODE_BUTTON = "peachbot:code";

	private static final String CODE_MODAL = "peachbot:code-submit";

	private static final Pattern SNOWFLAKE = Pattern.compile("\\d{1,32}");

	private static final Logger log = LoggerFactory.getLogger(DiscordInteractionController.class);

	private final JsonMapper json = JsonMapper.builder().build();

	private final DiscordProperties properties;

	private final DiscordVerification verification;

	private final DiscordClient client;

	private final TaskExecutor executor;

	private final InteractionSignature signature;

	public DiscordInteractionController(DiscordProperties properties, DiscordVerification verification,
			DiscordClient client, @Qualifier("discordExecutor") TaskExecutor executor) {
		this.properties = properties;
		this.verification = verification;
		this.client = client;
		this.executor = executor;
		this.signature = properties.configured() ? new InteractionSignature(properties.publicKey()) : null;
	}

	@PostMapping("/discord/interactions")
	Map<String, Object> interaction(@RequestHeader(name = "X-Signature-Ed25519", required = false) String signed,
			@RequestHeader(name = "X-Signature-Timestamp", required = false) String timestamp,
			@RequestBody byte[] body) {
		if (signature == null) {
			throw ApiException.notFound("PeachBot is not set up.");
		}
		if (!signature.valid(signed, timestamp, body)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid request signature.");
		}
		JsonNode interaction;
		try {
			interaction = json.readTree(body);
		}
		catch (JacksonException ex) {
			throw ApiException.validation("The request body is missing or malformed.", null);
		}
		int type = interaction.path("type").asInt(0);
		if (type == PING) {
			return Map.of("type", 1);
		}
		JsonNode user = interaction.path("member").path("user");
		String userId = user.path("id").asString("");
		if (!properties.guildId().equals(interaction.path("guild_id").asString(""))
				|| !SNOWFLAKE.matcher(userId).matches()) {
			return message("PeachBot only works inside the PeachHacks server.", false);
		}
		String username = user.path("username").asString("");
		String action = interaction.path("data").path("custom_id").asString("");
		if (type == COMPONENT && action.equals(DiscordVerification.VERIFY_BUTTON)) {
			if (verification.restore(userId)) {
				return message("You are already verified. Your Hacker role is on its way back.", false);
			}
			return modal(EMAIL_MODAL, "Verify for PeachHacks", "email", "The email you applied with", 5, 255,
					"you@example.com");
		}
		if (type == MODAL_SUBMIT && action.equals(EMAIL_MODAL)) {
			if (!verification.requestCode(userId, shorten(username), field(interaction, "email"))) {
				return message("You have asked for several codes already. Wait ten minutes and try again.", false);
			}
			return message("If an accepted PeachHacks application uses that email, we just sent it a "
					+ DiscordVerification.CODE_LENGTH + "-digit code. It works for "
					+ DiscordVerification.CODE_VALID_FOR.toMinutes() + " minutes, so check your inbox (and spam),"
					+ " then press the button below.\n\nNo email? Verification is for accepted hackers, and the"
					+ " address has to be the one you applied with.", true);
		}
		if (type == COMPONENT && action.equals(ENTER_CODE_BUTTON)) {
			return modal(CODE_MODAL, "Enter your code", "code", "The code from your email",
					DiscordVerification.CODE_LENGTH, DiscordVerification.CODE_LENGTH + 2, "123456");
		}
		if (type == MODAL_SUBMIT && action.equals(CODE_MODAL)) {
			String token = interaction.path("token").asString("");
			String code = field(interaction, "code");
			try {
				executor.execute(() -> finish(token, userId, shorten(username), code));
			}
			catch (TaskRejectedException ex) {
				return message("PeachBot is busy right now. Try again in a minute.", true);
			}
			return Map.of("type", 5, "data", Map.of("flags", EPHEMERAL));
		}
		return message("PeachBot does not know that button. Use the Verify button in the verification channel.",
				false);
	}

	private void finish(String token, String userId, String username, String code) {
		Outcome outcome;
		try {
			outcome = verification.complete(userId, username, code);
		}
		catch (RuntimeException ex) {
			log.error("Verification of Discord user {} failed", userId, ex);
			outcome = null;
		}
		Map<String, Object> reply = switch (outcome) {
			case VERIFIED -> content("You are verified. Welcome to PeachHacks! The hacker channels are open to you now.",
					false);
			case ROLE_NOT_GIVEN -> content("Your code was right, but the Hacker role could not be added just now."
					+ " Press Verify again in a minute; you will not need another code.", false);
			case WRONG_CODE -> content("That code is not right. Check the email and try again.", true);
			case NO_CODE -> content("There is no code waiting for you: it expired, was already used, or was tried too"
					+ " many times. Press Verify to get a new one.", false);
			case NOT_ACCEPTED -> content("That application is not accepted at the moment, so there is nothing to"
					+ " verify yet.", false);
			case null -> content("Something went wrong on our side. Try again in a minute.", true);
		};
		try {
			client.editInteractionReply(token, reply);
		}
		catch (RuntimeException ex) {
			log.warn("Could not tell Discord user {} the result of their verification: {}", userId, ex.toString());
		}
	}

	private static String field(JsonNode interaction, String id) {
		for (JsonNode row : interaction.path("data").path("components")) {
			for (JsonNode input : row.path("components")) {
				if (id.equals(input.path("custom_id").asString(""))) {
					return input.path("value").asString("");
				}
			}
		}
		return "";
	}

	private static String shorten(String username) {
		return (username.length() > 64) ? username.substring(0, 64) : username;
	}

	private static Map<String, Object> message(String text, boolean enterCodeButton) {
		Map<String, Object> data = new LinkedHashMap<>(content(text, enterCodeButton));
		data.put("flags", EPHEMERAL);
		return Map.of("type", 4, "data", data);
	}

	private static Map<String, Object> content(String text, boolean enterCodeButton) {
		List<Object> components = enterCodeButton ? List.of(Map.of("type", 1, "components",
				List.of(Map.of("type", 2, "style", 1, "label", "Enter code", "custom_id", ENTER_CODE_BUTTON))))
				: List.of();
		return Map.of("content", text, "components", components);
	}

	private static Map<String, Object> modal(String id, String title, String fieldId, String label, int minLength,
			int maxLength, String placeholder) {
		Map<String, Object> input = Map.of("type", 4, "custom_id", fieldId, "label", label, "style", 1, "min_length",
				minLength, "max_length", maxLength, "required", true, "placeholder", placeholder);
		return Map.of("type", 9, "data", Map.of("custom_id", id, "title", title, "components",
				List.of(Map.of("type", 1, "components", List.of(input)))));
	}

}
