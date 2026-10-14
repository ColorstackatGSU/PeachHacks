package com.peachhacks.backend.stats;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.peachhacks.backend.common.Tokens;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

	private static final String REGISTRATION_OPEN = "registration_open";

	private static final String PREVIEW_KEY_HASH = "registration_preview_key_hash";

	private static final String WEB_CHECK_IN_ADMIN_ONLY = "web_check_in_admin_only";

	private final JdbcClient jdbc;

	public SettingsService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean isRegistrationOpen() {
		return flag(REGISTRATION_OPEN);
	}

	public void setRegistrationOpen(boolean open) {
		put(REGISTRATION_OPEN, Boolean.toString(open));
	}

	/**
	 * While on, volunteers are sent to the staff app for check-in and the check-in screen of
	 * the admin site is for admins only. Off until an admin turns it on.
	 */
	public boolean isWebCheckInAdminOnly() {
		return flag(WEB_CHECK_IN_ADMIN_ONLY);
	}

	public void setWebCheckInAdminOnly(boolean adminOnly) {
		put(WEB_CHECK_IN_ADMIN_ONLY, Boolean.toString(adminOnly));
	}

	/**
	 * The preview lets organizers use the registration form while it is closed to everyone
	 * else. Whoever sends the key is treated as if registration were open. Making a new key
	 * replaces the old one; only its hash is kept.
	 */
	public String createPreviewKey() {
		String key = Tokens.random();
		put(PREVIEW_KEY_HASH, Tokens.sha256(key));
		return key;
	}

	public void endPreview() {
		jdbc.sql("delete from settings where key = :key").param("key", PREVIEW_KEY_HASH).update();
	}

	public boolean isPreviewActive() {
		return previewKeyHash() != null;
	}

	public boolean isRegistrationOpenFor(String previewKey) {
		return isRegistrationOpen() || previewAllows(previewKey);
	}

	public boolean previewAllows(String previewKey) {
		if (previewKey == null || previewKey.isBlank() || previewKey.length() > 64) {
			return false;
		}
		String expected = previewKeyHash();
		return expected != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				Tokens.sha256(previewKey.strip()).getBytes(StandardCharsets.UTF_8));
	}

	private boolean flag(String key) {
		return jdbc.sql("select value from settings where key = :key")
			.param("key", key)
			.query(String.class)
			.optional()
			.map(Boolean::parseBoolean)
			.orElse(false);
	}

	private String previewKeyHash() {
		return jdbc.sql("select value from settings where key = :key")
			.param("key", PREVIEW_KEY_HASH)
			.query(String.class)
			.optional()
			.orElse(null);
	}

	private void put(String key, String value) {
		jdbc.sql("""
				insert into settings (key, value) values (:key, :value)
				on conflict (key) do update set value = excluded.value, updated_at = now()
				""").param("key", key).param("value", value).update();
	}

}
