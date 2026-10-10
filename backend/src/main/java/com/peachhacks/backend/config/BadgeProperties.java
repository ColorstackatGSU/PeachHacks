package com.peachhacks.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The lanyard colours are free text shown to whoever hands out lanyards. They have no
 * default: until they are set the colour is null and only the group is reported.
 */
@ConfigurationProperties(prefix = "app.badges")
public record BadgeProperties(String lanyardHostColor, String lanyardOtherColor) {

	public BadgeProperties {
		lanyardHostColor = clean(lanyardHostColor);
		lanyardOtherColor = clean(lanyardOtherColor);
	}

	private static String clean(String value) {
		return (value != null && !value.isBlank()) ? value.strip() : null;
	}

}
