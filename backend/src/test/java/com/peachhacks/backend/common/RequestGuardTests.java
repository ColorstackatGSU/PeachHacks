package com.peachhacks.backend.common;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.peachhacks.backend.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@ExtendWith(OutputCaptureExtension.class)
class RequestGuardTests {

	private final BodyLimitFilter filter = new BodyLimitFilter();

	private final AtomicInteger bytesRead = new AtomicInteger();

	private final FilterChain readsTheBody = (request, response) -> {
		byte[] buffer = new byte[8192];
		for (int count = request.getInputStream().read(buffer); count >= 0; count = request.getInputStream()
			.read(buffer)) {
			bytesRead.addAndGet(count);
		}
	};

	@Test
	void aBodyThatDeclaresNoLengthIsCutOffOnceTheLimitHasBeenRead() {
		int limit = (int) BodyLimitFilter.DEFAULT_MAX_BYTES;
		for (String path : new String[] { "/public/pre-registrations", "/public/unsubscribe", "/admin/auth/login",
				"/admin/auth/set-password" }) {
			bytesRead.set(0);
			BodyTooLargeException refused = catchThrowableOfType(BodyTooLargeException.class,
					() -> filter.doFilter(chunked(path, limit + 1), new MockHttpServletResponse(), readsTheBody));

			assertThat(refused).as(path).isNotNull();
			assertThat(refused.answer().getStatus().value()).isEqualTo(413);
			assertThat(refused.answer().getCode()).isEqualTo("PAYLOAD_TOO_LARGE");
			assertThat(bytesRead.get()).as("stopped at the first read that crossed the limit").isLessThanOrEqualTo(limit);
			assertThatCode(() -> filter.doFilter(chunked(path, limit), new MockHttpServletResponse(), readsTheBody))
				.as(path + " at exactly the limit")
				.doesNotThrowAnyException();
		}
	}

	@Test
	void aChunkedRegistrationGetsTheResumeErrorAtThreeMegabytes() {
		int limit = (int) BodyLimitFilter.REGISTRATION_MAX_BYTES;

		BodyTooLargeException refused = catchThrowableOfType(BodyTooLargeException.class, () -> filter
			.doFilter(chunked("/public/registrations", limit + 1), new MockHttpServletResponse(), readsTheBody));

		assertThat(refused.answer().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(refused.answer().getFieldErrors()).containsEntry("resume", "Resume must be 2 MB or smaller");
		assertThatCode(() -> filter.doFilter(chunked("/public/registrations", limit), new MockHttpServletResponse(),
				readsTheBody))
			.doesNotThrowAnyException();
	}

	@Test
	void aDeclaredLengthOverTheLimitIsRefusedBeforeAnythingIsRead() {
		MockHttpServletRequest request = request("/public/pre-registrations",
				(int) BodyLimitFilter.DEFAULT_MAX_BYTES + 1);

		BodyTooLargeException refused = catchThrowableOfType(BodyTooLargeException.class,
				() -> filter.doFilter(request, new MockHttpServletResponse(), readsTheBody));

		assertThat(refused).isNotNull();
		assertThat(bytesRead.get()).isZero();
	}

	@Test
	void otherRoutesAndOtherMethodsAreNotLimited() throws Exception {
		MockHttpServletRequest signedIn = request("/admin/emails", 200_000);
		MockHttpServletRequest read = request("/public/status", 200_000);
		read.setMethod("GET");

		filter.doFilter(signedIn, new MockHttpServletResponse(), readsTheBody);
		filter.doFilter(read, new MockHttpServletResponse(), readsTheBody);

		assertThat(bytesRead.get()).isEqualTo(400_000);
	}

	@Test
	void theForwardedHeaderIsDescribedOnceAndTheLastEntryIsTheClient(CapturedOutput output) {
		ClientAddress trusting = new ClientAddress(properties(true));
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/public/pre-registrations");
		request.setRemoteAddr("10.0.0.1");
		request.addHeader("X-Forwarded-For", "198.51.100.7, 203.0.113.9");
		request.addHeader("X-Forwarded-For", "192.0.2.44");

		assertThat(trusting.of(request)).isEqualTo("192.0.2.44");
		assertThat(trusting.of(request)).isEqualTo("192.0.2.44");
		assertThat(trusting.of(new MockHttpServletRequest())).as("no header").isEqualTo("127.0.0.1");

		assertThat(output.getOut().lines().filter(line -> line.contains("X-Forwarded-For")).toList()).hasSize(1)
			.first()
			.asString()
			.contains("carries 3 entries")
			.contains("192.0.2.44")
			.contains("the last entry");
		assertThat(new ClientAddress(properties(false)).of(request)).as("not behind a proxy").isEqualTo("10.0.0.1");
	}

	@Test
	void anExportLineNamesTheFiltersThatWereSetAndCountsRowsWithoutTheHeader() {
		Csv csv = new Csv(List.of("id", "name"));
		assertThat(csv.rows()).isZero();
		csv.row(List.of(1, "Ada")).row(List.of(2, "Grace"));

		assertThat(csv.rows()).isEqualTo(2);
		assertThat(Csv.filters("q", null, "school", " ", "checkedIn", null)).isEqualTo("no filters");
		assertThat(Csv.filters("q", "ada\r\nforged line", "school", "Georgia State University", "checkedIn", true))
			.isEqualTo("q=ada??forged line, school=Georgia State University, checkedIn=true");
	}

	private static RateLimitProperties properties(boolean trustForwardedFor) {
		return new RateLimitProperties(null, null, null, null, null, null, null, null, trustForwardedFor);
	}

	private static MockHttpServletRequest request(String path, int length) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
		request.setServletPath(path);
		request.setContent(new byte[length]);
		return request;
	}

	/** What a chunked upload looks like to the application: a body, and no length. */
	private static MockHttpServletRequest chunked(String path, int length) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", path) {

			@Override
			public int getContentLength() {
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return -1;
			}

		};
		request.setServletPath(path);
		request.setContent(new byte[length]);
		return request;
	}

}
