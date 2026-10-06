package com.peachhacks.backend.email;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import com.peachhacks.backend.common.Csv;
import com.peachhacks.backend.config.EmailProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDeliveryTests {

	private final List<String> requestBodies = new CopyOnWriteArrayList<>();

	private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();

	private final AtomicInteger responseStatus = new AtomicInteger(200);

	private HttpServer server;

	private final EmailComposer composer = new EmailComposer(new EmailProperties("", null,
			"PeachHacks <hello@peachhacks.com>", "https://www.peachhacks.com/", Duration.ZERO));

	@BeforeEach
	void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/emails", exchange -> {
			requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
			byte[] body = "{\"id\":\"stub\"}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(responseStatus.get(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void resendSenderPostsTheMessageWithTheApiKey() {
		ResendEmailSender sender = new ResendEmailSender("re_test_key", "PeachHacks <hello@peachhacks.com>",
				"http://127.0.0.1:" + server.getAddress().getPort());

		sender.send(composer.compose("ada@example.com", "Hi {{firstName}}", "Hello {{firstName}} {{lastName}}", "Ada",
				"Lovelace", "tok123"));

		assertThat(authorizationHeaders).containsExactly("Bearer re_test_key");
		String json = requestBodies.get(0);
		assertThat((String) JsonPath.read(json, "$.from")).isEqualTo("PeachHacks <hello@peachhacks.com>");
		assertThat((List<String>) JsonPath.read(json, "$.to")).containsExactly("ada@example.com");
		assertThat((String) JsonPath.read(json, "$.subject")).isEqualTo("Hi Ada");
		assertThat((String) JsonPath.read(json, "$.text")).startsWith("Hello Ada Lovelace");
		assertThat((String) JsonPath.read(json, "$.html")).contains("Hello Ada Lovelace");
		assertThat((String) JsonPath.read(json, "$.headers.List-Unsubscribe"))
			.isEqualTo("<https://www.peachhacks.com/unsubscribe.html?token=tok123>");
	}

	@Test
	void resendSenderFailsWhenTheProviderRejectsTheMessage() {
		responseStatus.set(422);
		ResendEmailSender sender = new ResendEmailSender("re_test_key", "PeachHacks <hello@peachhacks.com>",
				"http://127.0.0.1:" + server.getAddress().getPort());

		assertThatThrownBy(() -> sender.send(composer.compose("ada@example.com", "Hi", "Hello", "Ada", "L", null)))
			.isInstanceOf(RestClientException.class);
	}

	@Test
	void composerEscapesHtmlAndAddsTheUnsubscribeLinkOnlyForBulkMail() {
		EmailMessage bulk = composer.compose("ada@example.com", "Subject", "Hi {{ firstName }},\n\n<b>bold</b> & more\nnext line",
				"<script>alert(1)</script>", "L", "tok/1+2");

		assertThat(bulk.html()).contains("Hi &lt;script&gt;alert(1)&lt;/script&gt;,")
			.contains("&lt;b&gt;bold&lt;/b&gt; &amp; more<br>next line")
			.doesNotContain("<script>")
			.contains("https://www.peachhacks.com/unsubscribe.html?token=tok%2F1%2B2");
		assertThat(bulk.text()).contains("Hi <script>alert(1)</script>,")
			.contains("Unsubscribe: https://www.peachhacks.com/unsubscribe.html?token=tok%2F1%2B2");

		EmailMessage single = composer.compose("ada@example.com", "Subject", "Hello", "Ada", "L", null);
		assertThat(single.html()).doesNotContain("unsubscribe");
		assertThat(single.text()).isEqualTo("Hello");
		assertThat(single.headers()).isEmpty();
	}

	@Test
	void csvCellsThatLookLikeFormulasAreNeutralised() {
		assertThat(Csv.cell("=SUM(A1:A2)")).isEqualTo("'=SUM(A1:A2)");
		assertThat(Csv.cell("+1 404 555 0100")).isEqualTo("'+1 404 555 0100");
		assertThat(Csv.cell("-2+3")).isEqualTo("'-2+3");
		assertThat(Csv.cell("@cmd")).isEqualTo("'@cmd");
		assertThat(Csv.cell("=HYPERLINK(\"http://x\",\"y\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
		assertThat(Csv.cell("Georgia State, Atlanta")).isEqualTo("\"Georgia State, Atlanta\"");
		assertThat(Csv.cell("plain")).isEqualTo("plain");
		assertThat(Csv.cell(null)).isEmpty();
	}

}
