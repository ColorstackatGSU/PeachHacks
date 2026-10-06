package com.peachhacks.backend.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiError(String code, String message,
		@JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> fieldErrors) {

	public ApiError(String code, String message) {
		this(code, message, null);
	}

}
