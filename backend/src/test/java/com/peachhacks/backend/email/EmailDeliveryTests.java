package com.peachhacks.backend.email;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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
			"PeachHacks <hello@peachhacks.com>", "https://www.peachhacks.com/", "https://admin.peachhacks.com/",
			Duration.ZERO));

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
	void resendSenderSendsAttachmentsInResendsShape() {
		ResendEmailSender sender = new ResendEmailSender("re_test_key", "PeachHacks <hello@peachhacks.com>",
				"http://127.0.0.1:" + server.getAddress().getPort());
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };

		sender.send(composer.compose("ada@example.com", "Hi", "Hello", "Ada", "L", null));
		sender.send(composer.compose("ada@example.com", "Hi", "Hello", "Ada", "L", null)
			.withAttachments(List.of(new EmailMessage.Attachment("ticket.png", "image/png", png, "ticket"),
					new EmailMessage.Attachment("notes.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8),
							null))));

		assertThat(requestBodies.get(0)).doesNotContain("\"attachments\"");
		String json = requestBodies.get(1);
		assertThat((Integer) JsonPath.read(json, "$.attachments.length()")).isEqualTo(2);
		assertThat((String) JsonPath.read(json, "$.attachments[0].filename")).isEqualTo("ticket.png");
		assertThat((String) JsonPath.read(json, "$.attachments[0].content_type")).isEqualTo("image/png");
		assertThat((String) JsonPath.read(json, "$.attachments[0].content_id")).isEqualTo("ticket");
		assertThat(Base64.getDecoder().decode((String) JsonPath.read(json, "$.attachments[0].content")))
			.isEqualTo(png);
		assertThat((String) JsonPath.read(json, "$.attachments[1].filename")).isEqualTo("notes.txt");
		assertThat((List<?>) JsonPath.read(json, "$.attachments[1][?(@.content_id)]")).isEmpty();
	}

	@Test
	void ticketEmailLinksToTheTicketAndCarriesTheQrCodeInlineAndAttached() {
		EmailProperties properties = new EmailProperties("", null, null, "https://www.peachhacks.com/", null,
				Duration.ZERO);
		List<EmailMessage> sent = new ArrayList<>();
		MailService mail = new MailService(sent::add, new EmailComposer(properties), Runnable::run, properties);
		byte[] png = { (byte) 0x89, 'P', 'N', 'G' };
		String url = "https://www.peachhacks.com/ticket?t=abc&x=1";

		mail.sendTicket("ada@example.com", "Ada", url, png, null, "tok123");

		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.subject()).contains("You're in");
		assertThat(message.text()).contains("Hi Ada,")
			.contains("accepted")
			.contains("Your ticket: " + url)
			.doesNotContainIgnoringCase("wallet")
			.doesNotContain(EmailComposer.BLOCK_MARKER);
		assertThat(message.html()).contains("href=\"https://www.peachhacks.com/ticket?t=abc&amp;x=1\"")
			.contains("src=\"cid:peachhacks-ticket\"")
			.doesNotContainIgnoringCase("wallet")
			.doesNotContain(EmailComposer.BLOCK_MARKER);
		assertThat(message.attachments()).hasSize(1);
		EmailMessage.Attachment attachment = message.attachments().get(0);
		assertThat(attachment.filename()).isEqualTo("peachhacks-ticket.png");
		assertThat(attachment.contentType()).isEqualTo("image/png");
		assertThat(attachment.contentId()).isEqualTo("peachhacks-ticket");
		assertThat(attachment.content()).isEqualTo(png);

		mail.sendTicket("ada@example.com", "Ada", url, png, "https://pay.google.com/gp/v/save/a.b.c", "tok123");
		assertThat(sent.get(1).text()).contains("Add to Google Wallet: https://pay.google.com/gp/v/save/a.b.c");
		assertThat(sent.get(1).html())
			.contains("href=\"https://pay.google.com/gp/v/save/a.b.c\"")
			.contains(">Add to Google Wallet</a>");
	}

	@Test
	void registrationConfirmationSaysATicketFollowsAcceptance() {
		EmailProperties properties = new EmailProperties("", null, null, null, null, Duration.ZERO);
		List<EmailMessage> sent = new ArrayList<>();
		MailService mail = new MailService(sent::add, new EmailComposer(properties), Runnable::run, properties);

		mail.sendRegistrationConfirmation("ada@example.com", "Ada", "tok123");

		assertThat(sent.get(0).text()).contains("We received your application")
			.contains("If you are accepted, we will email you your ticket");
		assertThat(sent.get(0).attachments()).isEmpty();
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

	@Test
	void adminWelcomeLinksToTheAdminSiteAndNeverCarriesAPassword() {
		EmailProperties properties = new EmailProperties("", null, null, null, "https://admin.peachhacks.com/",
				Duration.ZERO);
		List<EmailMessage> sent = new ArrayList<>();
		MailService mail = new MailService(sent::add, new EmailComposer(properties), Runnable::run, properties);

		mail.sendAdminWelcome("new@peachhacks.com", "Ada", "Grace");

		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.to()).isEqualTo("new@peachhacks.com");
		assertThat(message.text()).contains("Hi Ada,")
			.contains("Grace added you as an admin")
			.contains("https://admin.peachhacks.com")
			.doesNotContain("admin.peachhacks.com/")
			.doesNotContainIgnoringCase("unsubscribe");
	}

	@Test
	void volunteerWelcomeExplainsCheckInAndNeverCarriesAPassword() {
		EmailProperties properties = new EmailProperties("", null, null, null, "https://admin.peachhacks.com/",
				Duration.ZERO);
		List<EmailMessage> sent = new ArrayList<>();
		MailService mail = new MailService(sent::add, new EmailComposer(properties), Runnable::run, properties);

		mail.sendVolunteerWelcome("door@peachhacks.com", "Ada", "Grace");

		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.to()).isEqualTo("door@peachhacks.com");
		assertThat(message.subject()).contains("check-in volunteer");
		assertThat(message.text()).contains("Hi Ada,")
			.contains("Grace added you as a check-in volunteer")
			.contains("check them in")
			.contains("https://admin.peachhacks.com")
			.contains("ask them for it")
			.doesNotContain("as an admin")
			.doesNotContainIgnoringCase("unsubscribe");
	}

}
