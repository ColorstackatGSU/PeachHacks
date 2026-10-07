package com.peachhacks.backend;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.peachhacks.backend.config.EmailProperties;
import com.peachhacks.backend.config.GoogleWalletProperties;
import com.peachhacks.backend.ticket.GoogleWallet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleWalletTests {

	static final String SAVE_PREFIX = "https://pay.google.com/gp/v/save/";

	static final KeyPair KEYS = generateKeys();

	private static final EmailProperties EMAIL = new EmailProperties("", null, null, "https://www.peachhacks.com/",
			null, Duration.ZERO);

	@Test
	void saveLinkCarriesASignedPassWhoseBarcodeIsTheTicketUrl() throws Exception {
		GoogleWallet wallet = new GoogleWallet(
				new GoogleWalletProperties("3388000000012345678", "wallet@project.iam.gserviceaccount.com",
						pem(KEYS).replace("\n", "\\n"), null),
				EMAIL);
		UUID registrationId = UUID.randomUUID();
		String ticketUrl = "https://www.peachhacks.com/ticket?t=abcDEF_123-xyz";

		String url = wallet.saveUrl(registrationId, "Ada", "Lovelace", "Georgia State University", ticketUrl)
			.orElseThrow();
		String claims = verifiedClaims(url, KEYS.getPublic());

		assertThat((String) JsonPath.read(claims, "$.iss")).isEqualTo("wallet@project.iam.gserviceaccount.com");
		assertThat((String) JsonPath.read(claims, "$.aud")).isEqualTo("google");
		assertThat((String) JsonPath.read(claims, "$.typ")).isEqualTo("savetowallet");
		assertThat((List<String>) JsonPath.read(claims, "$.origins")).containsExactly("https://www.peachhacks.com");
		assertThat((String) JsonPath.read(claims, "$.payload.eventTicketObjects[0].classId"))
			.isEqualTo("3388000000012345678.peachhacks_2027");
		assertThat(claims).as("the console-made class is referenced, not redefined")
			.doesNotContain("eventTicketClasses")
			.doesNotContain("generic");
		assertThat((String) JsonPath.read(claims, "$.payload.eventTicketObjects[0].state")).isEqualTo("ACTIVE");
		String objectId = JsonPath.read(claims, "$.payload.eventTicketObjects[0].id");
		assertThat(objectId).isEqualTo("3388000000012345678.ticket_" + registrationId.toString().replace("-", ""))
			.matches("[A-Za-z0-9._-]+");
		assertThat((String) JsonPath.read(claims, "$.payload.eventTicketObjects[0].barcode.type")).isEqualTo("QR_CODE");
		assertThat((String) JsonPath.read(claims, "$.payload.eventTicketObjects[0].barcode.value")).isEqualTo(ticketUrl);
		assertThat((String) JsonPath.read(claims, "$.payload.eventTicketObjects[0].ticketHolderName"))
			.isEqualTo("Ada Lovelace");
		assertThat((List<String>) JsonPath.read(claims, "$.payload.eventTicketObjects[0].textModulesData[*].body"))
			.containsExactly("Georgia State University");

		GoogleWallet custom = new GoogleWallet(new GoogleWalletProperties("3388000000012345678",
				"wallet@project.iam.gserviceaccount.com", pem(KEYS), "workshop_pass"), EMAIL);
		String customClaims = verifiedClaims(custom.saveUrl(registrationId, "Ada", "Lovelace", "GSU", ticketUrl)
			.orElseThrow(), KEYS.getPublic());
		assertThat((String) JsonPath.read(customClaims, "$.payload.eventTicketObjects[0].classId"))
			.isEqualTo("3388000000012345678.workshop_pass");

		String again = wallet.saveUrl(registrationId, "Ada", "Lovelace", "Georgia State University", ticketUrl)
			.orElseThrow();
		assertThat((String) JsonPath.read(verifiedClaims(again, KEYS.getPublic()), "$.payload.eventTicketObjects[0].id"))
			.isEqualTo(objectId);

		String longest = wallet
			.saveUrl(registrationId, "A".repeat(100), "B".repeat(100), "C".repeat(255),
					"https://www.peachhacks.com/ticket?t=" + "t".repeat(64))
			.orElseThrow();
		assertThat(longest.length() - SAVE_PREFIX.length()).as("JWT length").isLessThanOrEqualTo(1800);
	}

	@Test
	void theFeatureIsOffWhenUnconfiguredOrTheKeyIsUnreadable() {
		String key = pem(KEYS);
		List<GoogleWalletProperties> off = List.of(new GoogleWalletProperties(null, null, null, null),
				new GoogleWalletProperties("", "wallet@project.iam.gserviceaccount.com", key, null),
				new GoogleWalletProperties("3388000000012345678", " ", key, null),
				new GoogleWalletProperties("3388000000012345678", "wallet@project.iam.gserviceaccount.com", "", null),
				new GoogleWalletProperties("3388000000012345678", "wallet@project.iam.gserviceaccount.com",
						"-----BEGIN PRIVATE KEY-----\nnot a key\n-----END PRIVATE KEY-----", null),
				new GoogleWalletProperties("3388000000012345678", "wallet@project.iam.gserviceaccount.com",
						"-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----", null));
		for (GoogleWalletProperties properties : off) {
			GoogleWallet wallet = new GoogleWallet(properties, EMAIL);
			assertThat(wallet.saveUrl(UUID.randomUUID(), "Ada", "Lovelace", "GSU", "https://x.test/ticket?t=1"))
				.isEmpty();
		}
		assertThat(new GoogleWalletProperties("1", "a@b.c", key, null).toString()).doesNotContain(key.substring(40, 80));
	}

	static String verifiedClaims(String url, PublicKey publicKey) throws Exception {
		assertThat(url).startsWith(SAVE_PREFIX);
		String jwt = url.substring(SAVE_PREFIX.length());
		String[] parts = jwt.split("\\.");
		assertThat(parts).hasSize(3);
		Base64.Decoder decoder = Base64.getUrlDecoder();
		String header = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8);
		assertThat((String) JsonPath.read(header, "$.alg")).isEqualTo("RS256");
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initVerify(publicKey);
		signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(signature.verify(decoder.decode(parts[2]))).as("signature verifies").isTrue();
		return new String(decoder.decode(parts[1]), StandardCharsets.UTF_8);
	}

	static String pem(KeyPair keys) {
		return "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(keys.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
	}

	static KeyPair generateKeys() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
