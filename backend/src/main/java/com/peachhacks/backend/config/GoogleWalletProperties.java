package com.peachhacks.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.google-wallet")
public record GoogleWalletProperties(String issuerId, String serviceAccountEmail, String privateKey,
		String classId) {

	public GoogleWalletProperties {
		issuerId = (issuerId != null) ? issuerId.trim() : "";
		serviceAccountEmail = (serviceAccountEmail != null) ? serviceAccountEmail.trim() : "";
		privateKey = (privateKey != null) ? privateKey.trim() : "";
		classId = (classId != null && !classId.isBlank()) ? classId.trim() : "peachhacks_2027";
	}

	public boolean configured() {
		return !issuerId.isEmpty() && !serviceAccountEmail.isEmpty() && !privateKey.isEmpty();
	}

	@Override
	public String toString() {
		return "GoogleWalletProperties[issuerId=" + issuerId + ", classId=" + classId + ", configured=" + configured()
				+ "]";
	}

}
