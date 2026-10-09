package com.peachhacks.backend.discord;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/**
 * Discord signs every interaction with the application's Ed25519 key over the timestamp
 * header followed by the raw body. Anything that fails this check did not come from Discord.
 */
final class InteractionSignature {

	// The fixed DER prefix of an Ed25519 SubjectPublicKeyInfo; the 32 raw key bytes follow it.
	private static final byte[] X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

	private final PublicKey key;

	InteractionSignature(String publicKeyHex) {
		try {
			byte[] raw = HexFormat.of().parseHex(publicKeyHex);
			byte[] encoded = new byte[X509_PREFIX.length + raw.length];
			System.arraycopy(X509_PREFIX, 0, encoded, 0, X509_PREFIX.length);
			System.arraycopy(raw, 0, encoded, X509_PREFIX.length, raw.length);
			this.key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalStateException("DISCORD_PUBLIC_KEY is not a valid Ed25519 public key in hex.", ex);
		}
	}

	boolean valid(String signatureHex, String timestamp, byte[] body) {
		if (signatureHex == null || timestamp == null) {
			return false;
		}
		try {
			Signature verifier = Signature.getInstance("Ed25519");
			verifier.initVerify(key);
			verifier.update(timestamp.getBytes(StandardCharsets.UTF_8));
			verifier.update(body);
			return verifier.verify(HexFormat.of().parseHex(signatureHex));
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			return false;
		}
	}

}
