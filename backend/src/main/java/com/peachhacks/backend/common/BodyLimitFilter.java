package com.peachhacks.backend.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import com.peachhacks.backend.registration.ResumeUpload;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps the body of the unauthenticated POSTs by the bytes actually read, so a request
 * with no Content-Length (chunked) is held to the same limit as one that declares its
 * size. A declared size over the limit is refused before anything is read. The refusal
 * surfaces while the controller's body is parsed and is answered by
 * GlobalExceptionHandler.
 */
@Component
public class BodyLimitFilter extends OncePerRequestFilter {

	/**
	 * A 2 MB resume is about 2.8 MB as base64; the rest of the allowance is for the form's
	 * other fields.
	 */
	public static final long REGISTRATION_MAX_BYTES = 3L * 1024 * 1024;

	public static final long DEFAULT_MAX_BYTES = 64L * 1024;

	private static final String REGISTRATIONS = "/public/registrations";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String path = request.getServletPath() + ((request.getPathInfo() != null) ? request.getPathInfo() : "");
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			chain.doFilter(request, response);
		}
		else if (path.equals(REGISTRATIONS)) {
			chain.doFilter(new LimitedRequest(request, REGISTRATION_MAX_BYTES,
					() -> ApiException.invalidField("resume", ResumeUpload.TOO_LARGE)), response);
		}
		else if (path.startsWith("/public/") || path.startsWith("/admin/auth/") || path.startsWith("/discord/")
				|| path.startsWith("/platform/")) {
			chain.doFilter(new LimitedRequest(request, DEFAULT_MAX_BYTES,
					() -> new ApiException(HttpStatus.valueOf(413), "PAYLOAD_TOO_LARGE",
							"The request is too large.")),
					response);
		}
		else {
			chain.doFilter(request, response);
		}
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private final long maxBytes;

		private final Supplier<ApiException> answer;

		private ServletInputStream stream;

		private LimitedRequest(HttpServletRequest request, long maxBytes, Supplier<ApiException> answer) {
			super(request);
			this.maxBytes = maxBytes;
			this.answer = answer;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (getContentLengthLong() > maxBytes) {
				throw new BodyTooLargeException(answer.get());
			}
			if (stream == null) {
				stream = new LimitedStream(super.getInputStream(), maxBytes, answer);
			}
			return stream;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			String encoding = getCharacterEncoding();
			Charset charset = (encoding != null) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
			return new BufferedReader(new InputStreamReader(getInputStream(), charset));
		}

	}

	private static final class LimitedStream extends ServletInputStream {

		private final ServletInputStream delegate;

		private final long maxBytes;

		private final Supplier<ApiException> answer;

		private long read;

		private LimitedStream(ServletInputStream delegate, long maxBytes, Supplier<ApiException> answer) {
			this.delegate = delegate;
			this.maxBytes = maxBytes;
			this.answer = answer;
		}

		@Override
		public int read() throws IOException {
			int value = delegate.read();
			if (value >= 0) {
				count(1);
			}
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int count = delegate.read(buffer, offset, length);
			if (count > 0) {
				count(count);
			}
			return count;
		}

		private void count(int bytes) throws IOException {
			read += bytes;
			if (read > maxBytes) {
				throw new BodyTooLargeException(answer.get());
			}
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			delegate.setReadListener(listener);
		}

		@Override
		public void close() throws IOException {
			delegate.close();
		}

	}

}
