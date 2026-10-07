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
import com.peachhacks.backend.email.EmailComposer.Content;
import com.peachhacks.backend.email.EmailComposer.Footer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDeliveryTests {

	private static final String LOGO = "src=\"https://www.peachhacks.com/assets/email-logo.png\"";

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G' };

	private final List<String> requestBodies = new CopyOnWriteArrayList<>();

	private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();

	private final AtomicInteger responseStatus = new AtomicInteger(200);

	private HttpServer server;

	private final EmailProperties properties = new EmailProperties("", null, "PeachHacks <hello@peachhacks.com>",
			"https://www.peachhacks.com/", "https://admin.peachhacks.com/", Duration.ZERO);

	private final EmailComposer composer = new EmailComposer(properties);

	private final List<EmailMessage> sent = new ArrayList<>();

	private final MailService mail = new MailService(sent::add, composer, Runnable::run, properties);

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

		sender.send(composer.composeCampaign("ada@example.com", "Hi {{firstName}}",
				"Hello {{firstName}} {{lastName}}", "Ada", "Lovelace", Footer.announcement("tok123")));

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

		sender.send(plain());
		sender.send(plain().withAttachments(List.of(new EmailMessage.Attachment("ticket.png", "image/png", png, "ticket"),
				new EmailMessage.Attachment("notes.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8), null))));

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
		String url = "https://www.peachhacks.com/ticket?t=abc&x=1";

		mail.sendTicket("ada@example.com", "Ada", url, PNG, null);

		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.subject()).contains("You're in");
		assertThat(message.text()).contains("Hi Ada,")
			.contains("accepted")
			.contains("View your ticket: " + url)
			.doesNotContainIgnoringCase("wallet");
		assertThat(message.html()).contains("href=\"https://www.peachhacks.com/ticket?t=abc&amp;x=1\"")
			.contains(">View your ticket</a>")
			.contains("src=\"cid:peachhacks-ticket\"")
			.contains(">You&#39;re in!</h1>")
			.doesNotContainIgnoringCase("wallet");
		assertThat(message.attachments()).hasSize(1);
		EmailMessage.Attachment attachment = message.attachments().get(0);
		assertThat(attachment.filename()).isEqualTo("peachhacks-ticket.png");
		assertThat(attachment.contentType()).isEqualTo("image/png");
		assertThat(attachment.contentId()).isEqualTo("peachhacks-ticket");
		assertThat(attachment.content()).isEqualTo(PNG);

		mail.sendTicket("ada@example.com", "Ada", url, PNG, "https://pay.google.com/gp/v/save/a.b.c");
		assertThat(sent.get(1).text()).contains("Add to Google Wallet: https://pay.google.com/gp/v/save/a.b.c");
		assertThat(sent.get(1).html())
			.contains("href=\"https://pay.google.com/gp/v/save/a.b.c\"")
			.contains(">Add to Google Wallet</a>");
	}

	@Test
	void registrationConfirmationSaysATicketFollowsAcceptance() {
		mail.sendRegistrationConfirmation("ada@example.com", "Ada", null);

		assertThat(sent.get(0).text()).contains("We received your application")
			.contains("If you are accepted, we will email you your ticket");
		assertThat(sent.get(0).attachments()).isEmpty();
	}

	@Test
	void resendSenderFailsWhenTheProviderRejectsTheMessage() {
		responseStatus.set(422);
		ResendEmailSender sender = new ResendEmailSender("re_test_key", "PeachHacks <hello@peachhacks.com>",
				"http://127.0.0.1:" + server.getAddress().getPort());

		assertThatThrownBy(() -> sender.send(plain())).isInstanceOf(RestClientException.class);
	}

	@Test
	void campaignBodiesAreEscapedAndOnlyAnnouncementsCarryTheUnsubscribeLink() {
		EmailMessage bulk = composer.composeCampaign("ada@example.com", "Subject",
				"Hi {{ firstName }},\n\n<b>bold</b> & more\nnext line", "<script>alert(1)</script>", "L",
				Footer.announcement("tok/1+2"));

		assertThat(bulk.html()).contains("Hi &lt;script&gt;alert(1)&lt;/script&gt;,")
			.contains("&lt;b&gt;bold&lt;/b&gt; &amp; more<br>next line")
			.doesNotContain("<script")
			.doesNotContain("<b>")
			.contains(LOGO)
			.contains("https://www.peachhacks.com/unsubscribe.html?token=tok%2F1%2B2");
		assertThat(bulk.text()).contains("Hi <script>alert(1)</script>,")
			.contains("Unsubscribe: https://www.peachhacks.com/unsubscribe.html?token=tok%2F1%2B2");
		assertThat(bulk.headers()).containsEntry("List-Unsubscribe",
				"<https://www.peachhacks.com/unsubscribe.html?token=tok%2F1%2B2>");

		EmailMessage update = composer.composeCampaign("ada@example.com", "Subject", "Hello", "Ada", "L",
				Footer.REGISTERED);
		assertThat(update.html()).doesNotContainIgnoringCase("unsubscribe")
			.contains("You are receiving this because you registered for PeachHacks.");
		assertThat(update.text()).startsWith("Hello")
			.contains("You are receiving this because you registered for PeachHacks.")
			.endsWith("https://www.peachhacks.com")
			.doesNotContainIgnoringCase("unsubscribe");
		assertThat(update.headers()).isEmpty();

		EmailMessage sample = composer.composeCampaign("ada@example.com", "Subject", "Hello", "Ada", "L",
				Footer.announcementSample());
		assertThat(sample.text()).contains("Unsubscribe: https://www.peachhacks.com/unsubscribe.html");
		assertThat(sample.headers()).as("a test copy has no token to unsubscribe").isEmpty();
	}

	@Test
	void campaignUrlsBecomeLinksAndNothingElseIsMarkup() {
		EmailMessage message = composer.composeCampaign("ada@example.com", "Subject", """
				Register at https://www.peachhacks.com/register?a=1&b=<2>. Bring ID (see http://example.com/faq).

				Not links: javascript:alert(1) ftp://example.com/file <a href="https://evil.example">click</a>
				""", "Ada", "L", Footer.REGISTERED);

		assertThat(message.html())
			.contains("href=\"https://www.peachhacks.com/register?a=1&amp;b=\"")
			.contains(">https://www.peachhacks.com/register?a=1&amp;b=</a>&lt;2&gt;. Bring ID")
			.contains("href=\"http://example.com/faq\"")
			.contains(">http://example.com/faq</a>).</p>")
			.contains("javascript:alert(1) ftp://example.com/file &lt;a href=&quot;")
			.doesNotContain("href=\"javascript")
			.doesNotContain("href=\"ftp")
			.doesNotContain("<a href=\"https://evil.example\">click");
		assertThat(message.text()).contains("Register at https://www.peachhacks.com/register?a=1&b=<2>.");
	}

	@Test
	void essentialEmailsSayWhyTheyWereSentAndNeverOfferToUnsubscribe() {
		sendEverySystemEmail("Ada", "Grace");

		assertThat(sent).hasSize(8);
		for (EmailMessage message : sent) {
			assertThat(message.headers()).as(message.subject()).doesNotContainKey("List-Unsubscribe");
			assertThat(message.html()).as(message.subject())
				.doesNotContainIgnoringCase("unsubscribe")
				.contains("You are receiving this because");
			assertThat(message.text()).as(message.subject())
				.doesNotContainIgnoringCase("unsubscribe")
				.contains("You are receiving this because");
		}
		assertThat(sent.get(0).text()).contains("because you signed up for PeachHacks.");
		assertThat(sent.get(1).text()).contains("because you registered for PeachHacks.");
		assertThat(sent.get(3).text()).contains("because you registered for PeachHacks.");
		assertThat(sent.get(4).text()).contains("because you registered for PeachHacks.");
	}

	@Test
	void everySystemEmailIsBrandedHasAHeadingAndATextAlternativeWithTheSameLinks() {
		sendEverySystemEmail("Ada", "Grace");

		for (EmailMessage message : sent) {
			assertThat(message.html()).as(message.subject())
				.startsWith("<!doctype html>")
				.contains(LOGO)
				.contains("alt=\"PeachHacks\"")
				.containsPattern("<h1 [^>]*>[^<]+</h1>")
				.contains("PeachHacks · ColorStack at Georgia State University")
				.contains("mso-hide:all")
				.doesNotContain("{{");
			assertThat(message.text()).as(message.subject())
				.contains("PeachHacks · ColorStack at Georgia State University")
				.doesNotContain("<")
				.doesNotContain("{{");
		}
		assertButton(sent.get(2), "Confirm your school email", "https://www.peachhacks.com/confirm-email?token=t&x=1");
		assertButton(sent.get(3), "View your ticket", "https://www.peachhacks.com/ticket?t=abc");
		assertButton(sent.get(4), "View your ticket", "https://www.peachhacks.com/ticket?t=abc");
		assertButton(sent.get(4), "Add to Google Wallet", "https://pay.google.com/gp/v/save/a.b.c");
		assertButton(sent.get(5), "Set your password", "https://admin.peachhacks.com/#/set-password?token=tok");
		assertButton(sent.get(6), "Set your password", "https://admin.peachhacks.com/#/set-password?token=tok");
		assertButton(sent.get(7), "Choose a new password", "https://admin.peachhacks.com/#/set-password?token=tok");
		assertThat(sent.get(0).html()).doesNotContain("v:roundrect");
		assertThat(sent.get(1).html()).doesNotContain("v:roundrect");
	}

	@Test
	void namesAreEscapedInEverySystemEmail() {
		String name = "<script>alert('x')</script> & \"Ada\"";

		sendEverySystemEmail(name, name);

		for (EmailMessage message : sent) {
			assertThat(message.html()).as(message.subject())
				.doesNotContain("<script")
				.doesNotContain("\"Ada\"")
				.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;Ada&quot;");
			assertThat(message.text()).as(message.subject()).contains(name);
		}
	}

	@Test
	void schoolEmailConfirmationSaysWhoWeAreAndMasksThePersonalAddress() {
		mail.sendSchoolEmailConfirmation("ada@school.edu", "Ada", "jordan.lee@gmail.com",
				"https://www.peachhacks.com/confirm-email?token=t", 14);

		EmailMessage message = sent.get(0);
		assertThat(message.to()).isEqualTo("ada@school.edu");
		assertThat(message.subject()).isEqualTo("Confirm your school email for PeachHacks");
		assertThat(message.text()).startsWith("Hi Ada,")
			.contains("a student hackathon run by ColorStack at Georgia State University")
			.contains("with the personal email j***@gmail.com.")
			.contains("Confirm your school email: https://www.peachhacks.com/confirm-email?token=t")
			.contains("The link works for 14 days.")
			.contains("If this was not you, you can ignore this email")
			.doesNotContain("jordan")
			.doesNotContainIgnoringCase("urgent")
			.doesNotContainIgnoringCase("immediately");
		assertThat(message.html()).contains("j***@gmail.com").doesNotContain("jordan");
		assertThat(MailService.mask("a@b.co")).isEqualTo("a***@b.co");
		assertThat(MailService.mask("not-an-address")).doesNotContain("not-an-address");
	}

	@Test
	void aLocalWebBaseUrlStillUsesTheLiveLogoSoItLoadsInARealInbox() {
		EmailComposer local = new EmailComposer(
				new EmailProperties("", null, null, "http://localhost:5173", null, Duration.ZERO));

		EmailMessage message = local.compose("ada@example.com", "Subject",
				Content.of("Preview", "Heading", List.of("Hello")).withPrimary("Open", "http://localhost:5173/x"),
				Footer.REGISTERED);

		assertThat(message.html()).contains(LOGO)
			.contains("href=\"http://localhost:5173/x\"")
			.contains("href=\"http://localhost:5173\"");
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
	void adminInviteLinksToChoosingAPasswordAndNeverCarriesOne() {
		mail.sendAdminInvite("new@peachhacks.com", "Ada", "Grace", "tok_en-1", Duration.ofDays(7));

		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.to()).isEqualTo("new@peachhacks.com");
		assertThat(message.text()).contains("Hi Ada,")
			.contains("Grace added you as an admin")
			.contains("Set your password: https://admin.peachhacks.com/#/set-password?token=tok_en-1")
			.contains("works for 7 days")
			.contains("ask Grace to send a new one")
			.doesNotContainIgnoringCase("unsubscribe");
		assertThat(message.headers()).isEmpty();
	}

	@Test
	void volunteerInviteExplainsCheckInAndLinksToChoosingAPassword() {
		mail.sendVolunteerInvite("door@peachhacks.com", "Ada", "Grace", "tok_en-1", Duration.ofDays(1));

		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.to()).isEqualTo("door@peachhacks.com");
		assertThat(message.subject()).contains("check-in volunteer");
		assertThat(message.text()).contains("Hi Ada,")
			.contains("Grace added you as a check-in volunteer")
			.contains("check them in")
			.contains("https://admin.peachhacks.com/#/set-password?token=tok_en-1")
			.contains("works for 1 day and")
			.doesNotContain("as an admin")
			.doesNotContainIgnoringCase("unsubscribe");
	}

	@Test
	void passwordResetSaysHowLongTheLinkLastsAndThatIgnoringItIsSafe() {
		mail.sendPasswordReset("new@peachhacks.com", "Ada", "tok_en-1", Duration.ofHours(1));

		assertThat(sent).hasSize(1);
		EmailMessage message = sent.get(0);
		assertThat(message.subject()).isEqualTo("Reset your PeachHacks admin password");
		assertThat(message.text()).contains("Hi Ada,")
			.contains("Choose a new password: https://admin.peachhacks.com/#/set-password?token=tok_en-1")
			.contains("works for 1 hour")
			.contains("your password stays the same")
			.doesNotContainIgnoringCase("unsubscribe");
		assertThat(MailService.validity(Duration.ofMinutes(30))).isEqualTo("30 minutes");
		assertThat(MailService.validity(Duration.ofHours(12))).isEqualTo("12 hours");
	}

	/**
	 * In order: pre-registration, registration, school email, ticket, ticket with a wallet
	 * link, admin invite, volunteer invite, password reset.
	 */
	private void sendEverySystemEmail(String name, String addedBy) {
		mail.sendPreRegistrationConfirmation("ada@example.com", name, "ada@school.edu");
		mail.sendRegistrationConfirmation("ada@example.com", name, "ada@school.edu");
		mail.sendSchoolEmailConfirmation("ada@school.edu", name, "ada@example.com",
				"https://www.peachhacks.com/confirm-email?token=t&x=1", 14);
		mail.sendTicket("ada@example.com", name, "https://www.peachhacks.com/ticket?t=abc", PNG, null);
		mail.sendTicketNow("ada@example.com", name, "https://www.peachhacks.com/ticket?t=abc", PNG,
				"https://pay.google.com/gp/v/save/a.b.c");
		mail.sendAdminInvite("new@peachhacks.com", name, addedBy, "tok", Duration.ofDays(7));
		mail.sendVolunteerInvite("door@peachhacks.com", name, addedBy, "tok", Duration.ofDays(7));
		mail.sendPasswordReset("new@peachhacks.com", name, "tok", Duration.ofHours(1));
	}

	/** A button for Outlook (VML) and for everyone else, the bare URL under it, and the same URL in the text. */
	private static void assertButton(EmailMessage message, String label, String url) {
		String escaped = url.replace("&", "&amp;");
		assertThat(message.html()).as(message.subject())
			.contains("<v:roundrect xmlns:v=\"urn:schemas-microsoft-com:vml\" xmlns:w=\"urn:schemas-microsoft-com:office:word\" href=\""
					+ escaped + "\"")
			.contains(">" + label + "</center>")
			.contains(">" + label + "</a>")
			.contains(">" + escaped + "</a>");
		assertThat(message.text()).as(message.subject()).contains(label + ": " + url);
	}

	private EmailMessage plain() {
		return composer.composeCampaign("ada@example.com", "Hi", "Hello", "Ada", "L", Footer.REGISTERED);
	}

}
