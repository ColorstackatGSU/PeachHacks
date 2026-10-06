package com.peachhacks.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(String resendApiKey, String resendBaseUrl, String from, String webBaseUrl,
		Duration campaignDelay) {

	public EmailProperties {
		resendApiKey = (resendApiKey != null) ? resendApiKey.trim() : "";
		resendBaseUrl = (resendBaseUrl != null && !resendBaseUrl.isBlank()) ? resendBaseUrl : "https://api.resend.com";
		from = (from != null && !from.isBlank()) ? from : "PeachHacks <hello@peachhacks.com>";
		webBaseUrl = (webBaseUrl != null && !webBaseUrl.isBlank()) ? webBaseUrl.replaceAll("/+$", "")
				: "http://localhost:5173";
		campaignDelay = (campaignDelay != null) ? campaignDelay : Duration.ofMillis(600);
	}

	public boolean resendEnabled() {
		return !resendApiKey.isEmpty();
	}

	@Override
	public String toString() {
		return "EmailProperties[from=" + from + ", webBaseUrl=" + webBaseUrl + ", resendEnabled=" + resendEnabled()
				+ "]";
	}

}
