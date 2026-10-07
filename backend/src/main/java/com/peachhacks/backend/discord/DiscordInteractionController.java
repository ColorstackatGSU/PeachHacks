package com.peachhacks.backend.discord;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.config.DiscordProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The URL Discord calls for every button press ("Interactions Endpoint URL" in the
 * Developer Portal). Each call must be answered within three seconds, so the answer comes
 * from the database alone and the role itself is given in the background.
 */
@RestController
public class DiscordInteractionController {

	private static final int PING = 1;

	private static final int COMPONENT = 3;

	private static final int EPHEMERAL = 64;

	private static final int LINK_BUTTON = 5;

	private static final Pattern SNOWFLAKE = Pattern.compile("\\d{1,32}");

	private final JsonMapper json = JsonMapper.builder().build();

	private final DiscordProperties properties;

	private final DiscordVerification verification;

	private final InteractionSignature signature;

	public DiscordInteractionController(DiscordProperties properties, DiscordVerification verification) {
		this.properties = properties;
		this.verification = verification;
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
		String userId = interaction.path("member").path("user").path("id").asString("");
		if (!properties.guildId().equals(interaction.path("guild_id").asString(""))
				|| !SNOWFLAKE.matcher(userId).matches()) {
			return message("PeachBot only works inside the PeachHacks server.", false);
		}
		String action = interaction.path("data").path("custom_id").asString("");
		if (type != COMPONENT || !action.equals(DiscordVerification.VERIFY_BUTTON)) {
			return message("PeachBot does not know that button. Use the Verify button in the verification channel.",
					false);
		}
		return switch (verification.verify(userId)) {
			case VERIFIED -> message("You are verified. Welcome to PeachHacks! The hacker channels are open to you.",
					false);
			case NOT_ACCEPTED -> message("This Discord account is connected to a PeachHacks application, but that"
					+ " application is not accepted at the moment, so the hacker channels stay closed.", false);
			case NOT_CONNECTED -> message("This Discord account is not connected to an accepted PeachHacks application"
					+ " yet.\n\nOpen the hacker platform, sign in with the email you applied with, and press"
					+ " **Connect Discord**. That gives you the Hacker role right away. Only accepted hackers can"
					+ " sign in there.", true);
		};
	}

	private Map<String, Object> message(String text, boolean platformButton) {
		List<Object> components = platformButton
				? List.of(Map.of("type", 1, "components", List.of(Map.of("type", 2, "style", LINK_BUTTON, "label",
						"Open the hacker platform", "url", verification.connectUrl()))))
				: List.of();
		return Map.of("type", 4, "data", Map.of("content", text, "flags", EPHEMERAL, "components", components));
	}

}
