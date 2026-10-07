package com.peachhacks.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApi(ApiException ex) {
		return ResponseEntity.status(ex.getStatus())
			.body(new ApiError(ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.merge(error.getField(), String.valueOf(error.getDefaultMessage()),
					(first, second) -> RequestValidator.isPresenceCode(error.getCode()) ? second : first);
		}
		return ResponseEntity.badRequest()
			.body(new ApiError("VALIDATION_ERROR", "Please check the highlighted fields.", fieldErrors));
	}

	@ExceptionHandler(BodyTooLargeException.class)
	ResponseEntity<ApiError> handleTooLarge(BodyTooLargeException ex) {
		return handleApi(ex.answer());
	}

	/** A body cut off by BodyLimitFilter reaches here wrapped in the JSON reader's own exception. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
		for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof BodyTooLargeException tooLarge) {
				return handleApi(tooLarge.answer());
			}
		}
		return ResponseEntity.badRequest()
			.body(new ApiError("VALIDATION_ERROR", "The request body is missing or malformed.", Map.of()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		if ("id".equals(ex.getName())) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "Not found."));
		}
		String message = "Invalid value for '" + ex.getName() + "'.";
		return ResponseEntity.badRequest()
			.body(new ApiError("VALIDATION_ERROR", message, Map.of(ex.getName(), message)));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleOther(Exception ex) {
		if (ex instanceof ErrorResponse errorResponse) {
			HttpStatusCode status = errorResponse.getStatusCode();
			return ResponseEntity.status(status).body(new ApiError(codeFor(status), messageFor(status)));
		}
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ApiError("INTERNAL_ERROR", "Something went wrong. Please try again."));
	}

	private static String codeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "VALIDATION_ERROR";
			case 401 -> "UNAUTHORIZED";
			case 403 -> "FORBIDDEN";
			case 404 -> "NOT_FOUND";
			case 405 -> "METHOD_NOT_ALLOWED";
			case 415 -> "UNSUPPORTED_MEDIA_TYPE";
			case 429 -> "RATE_LIMITED";
			default -> status.is4xxClientError() ? "BAD_REQUEST" : "INTERNAL_ERROR";
		};
	}

	private static String messageFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "The request is invalid.";
			case 404 -> "Not found.";
			case 405 -> "Method not allowed.";
			case 415 -> "Unsupported media type; send application/json.";
			default -> status.is4xxClientError() ? "The request could not be processed."
					: "Something went wrong. Please try again.";
		};
	}

}
