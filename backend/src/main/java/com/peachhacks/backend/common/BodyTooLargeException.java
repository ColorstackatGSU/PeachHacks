package com.peachhacks.backend.common;

import java.io.IOException;

/** Thrown while a request body is being read; answer is what the client should be told. */
public class BodyTooLargeException extends IOException {

	private final transient ApiException answer;

	public BodyTooLargeException(ApiException answer) {
		super(answer.getMessage());
		this.answer = answer;
	}

	public ApiException answer() {
		return answer;
	}

}
