package com.peachhacks.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

/**
 * Runs bean validation by hand so that endpoints can apply other checks (honeypot,
 * registration gate) before the body is validated.
 */
@Component
public class RequestValidator {

	private final Validator validator;

	public RequestValidator(Validator validator) {
		this.validator = validator;
	}

	public void validate(Object request) {
		Set<ConstraintViolation<Object>> violations = validator.validate(request);
		if (violations.isEmpty()) {
			return;
		}
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (ConstraintViolation<Object> violation : violations) {
			String field = violation.getPropertyPath().toString();
			String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
			fieldErrors.merge(field, violation.getMessage(), (first, second) -> isPresenceCode(code) ? second : first);
		}
		throw ApiException.validation("Please check the highlighted fields.", fieldErrors);
	}

	/** "Required" messages win when a field fails several constraints at once. */
	static boolean isPresenceCode(String code) {
		return "NotBlank".equals(code) || "NotNull".equals(code) || "NotEmpty".equals(code);
	}

}
