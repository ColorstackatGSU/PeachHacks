package com.peachhacks.backend.ticket;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.peachhacks.backend.config.EmailProperties;
import com.peachhacks.backend.config.GoogleWalletProperties;
import com.peachhacks.backend.registration.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;

/**
 * Builds "Add to Google Wallet" links. The signed JWT carries the hacker's event ticket
 * object, so Google's API is never called from here; Google creates the pass when the
 * link is opened. The event ticket class (event name, dates, logo, colour) is made by
 * hand in the Google Pay and Wallet Console and only referenced by id, so it must exist.
 * Off unless the issuer id, service account and key are all present and the key parses.
 */
@Component
public class GoogleWallet {

	private static final String SAVE_URL = "https://pay.google.com/gp/v/save/";

	/**
	 * Long names and schools are cut so the JWT stays under 1800 characters, the length
	 * Google documents as safe for a save link.
	 */
	private static final int MAX_TEXT_LENGTH = 60;

	private static final Logger log = LoggerFactory.getLogger(GoogleWallet.class);

	private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

	private final JsonMapper json = JsonMapper.builder().build();

	private final GoogleWalletProperties properties;

	private final String origin;

	private final PrivateKey key;

	public GoogleWallet(GoogleWalletProperties properties, EmailProperties emailProperties) {
		this.properties = properties;
		this.origin = emailProperties.webBaseUrl();
		this.key = properties.configured() ? parseKey(properties.privateKey()) : null;
		if (this.key != null) {
			log.info("Google Wallet passes: on, class {}.{}", properties.issuerId(), properties.classId());
		}
	}

	public boolean enabled() {
		return key != null;
	}

	public Optional<String> saveUrl(Registration r, String ticketUrl) {
		return saveUrl(r.getId(), r.getFirstName(), r.getLastName(), r.getSchool(), ticketUrl);
	}

	/** The barcode is the ticket URL itself, so a scanned pass and a scanned email look the same. */
	public Optional<String> saveUrl(UUID registrationId, String firstName, String lastName, String school,
			String ticketUrl) {
		if (key == null) {
			return Optional.empty();
		}
		String classId = properties.issuerId() + "." + properties.classId();
		Map<String, Object> pass = new LinkedHashMap<>();
		// Derived from the registration, so saving twice updates one pass instead of adding another.
		pass.put("id", properties.issuerId() + ".ticket_" + registrationId.toString().replace("-", ""));
		pass.put("classId", classId);
		pass.put("state", "ACTIVE");
		pass.put("ticketHolderName", shorten(firstName + " " + lastName));
		pass.put("textModulesData", List.of(Map.of("id", "school", "header", "School", "body", shorten(school))));
		pass.put("barcode", Map.of("type", "QR_CODE", "value", ticketUrl));

		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", properties.serviceAccountEmail());
		claims.put("aud", "google");
		claims.put("typ", "savetowallet");
		claims.put("iat", Instant.now().getEpochSecond());
		claims.put("origins", List.of(origin));
		claims.put("payload", Map.of("eventTicketObjects", List.of(pass)));
		try {
			String signingInput = encode(Map.of("alg", "RS256", "typ", "JWT")) + "." + encode(claims);
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(key);
			signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
			return Optional.of(SAVE_URL + signingInput + "." + BASE64_URL.encodeToString(signature.sign()));
		}
		catch (GeneralSecurityException ex) {
			log.error("Could not sign a Google Wallet pass: {}", ex.toString());
			return Optional.empty();
		}
	}

	private String encode(Map<String, Object> value) {
		return BASE64_URL.encodeToString(json.writeValueAsBytes(value));
	}

	private static String shorten(String value) {
		String text = (value != null) ? value.strip() : "";
		return (text.length() > MAX_TEXT_LENGTH) ? text.substring(0, MAX_TEXT_LENGTH - 1) + "…" : text;
	}

	/**
	 * Hosts usually store a multi-line secret on one line with literal \n sequences, so
	 * those are accepted as well as real line breaks. The key itself is never logged.
	 */
	private static PrivateKey parseKey(String pem) {
		try {
			String body = pem.replace("\\n", "\n")
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replace("\"", "")
				.replaceAll("\\s", "");
			if (body.contains("-----")) {
				throw new IllegalArgumentException("expected a PKCS#8 \"BEGIN PRIVATE KEY\" block");
			}
			return KeyFactory.getInstance("RSA")
				.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			log.error("GOOGLE_WALLET_PRIVATE_KEY could not be read as an RSA private key in PEM format ({});"
					+ " Google Wallet passes are off", ex.getClass().getSimpleName());
			return null;
		}
	}

}
