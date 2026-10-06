package com.peachhacks.backend.registration;

import com.peachhacks.backend.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Turns away a registration whose declared body cannot hold an acceptable resume before
 * any of it is read into memory. A 2 MB file is about 2.8 MB as base64; the rest of the
 * allowance is for the form's other fields.
 */
@Component
public class RegistrationBodyLimitInterceptor implements HandlerInterceptor {

	public static final long MAX_BODY_BYTES = 3L * 1024 * 1024;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (request.getContentLengthLong() > MAX_BODY_BYTES) {
			throw ApiException.invalidField("resume", ResumeUpload.TOO_LARGE);
		}
		return true;
	}

}
