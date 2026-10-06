package com.peachhacks.backend.stats;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

	private static final String REGISTRATION_OPEN = "registration_open";

	private final JdbcClient jdbc;

	public SettingsService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean isRegistrationOpen() {
		return jdbc.sql("select value from settings where key = :key")
			.param("key", REGISTRATION_OPEN)
			.query(String.class)
			.optional()
			.map(Boolean::parseBoolean)
			.orElse(false);
	}

	public void setRegistrationOpen(boolean open) {
		jdbc.sql("""
				insert into settings (key, value) values (:key, :value)
				on conflict (key) do update set value = excluded.value, updated_at = now()
				""").param("key", REGISTRATION_OPEN).param("value", Boolean.toString(open)).update();
	}

}
