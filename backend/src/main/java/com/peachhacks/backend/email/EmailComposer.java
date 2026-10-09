package com.peachhacks.backend.email;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.peachhacks.backend.config.EmailProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders every email in one branded layout: a table-based HTML message with inline
 * styles (the only styling Outlook and Gmail both keep) and a plain-text alternative that
 * carries the same links. Nothing here trusts its input: every piece of text is escaped,
 * and the only links are the ones passed as an Action or found as http(s) URLs in a
 * campaign body.
 */
@Component
public class EmailComposer {

	private static final Pattern FIRST_NAME = Pattern.compile("\\{\\{\\s*firstName\\s*}}");

	private static final Pattern LAST_NAME = Pattern.compile("\\{\\{\\s*lastName\\s*}}");

	private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t]*\\n\\s*");

	private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+");

	private static final String ORGANIZATION = "PeachHacks · ColorStack at Georgia State University";

	private static final String PRODUCTION_SITE = "https://www.peachhacks.com";

	private static final String LOGO_PATH = "/assets/email-logo.png";

	private static final String NAVY = "#001f3a";

	private static final String PEACH = "#FCA324";

	private static final String CREAM = "#f4f6ed";

	private static final String BODY_TEXT = "#233e56";

	private static final String MIST = "#b9d3dc";

	private static final String FONT = "'Open Sans',Arial,Helvetica,sans-serif";

	private static final String PARAGRAPH_STYLE = "margin:0 0 16px;font-family:" + FONT
			+ ";font-size:16px;line-height:1.6;color:" + BODY_TEXT;

	private static final int PREHEADER_LENGTH = 110;

	private static final int MAX_PRINTED_URL_LENGTH = 100;

	/** A link shown as a button, with the plain URL underneath for clients that drop the button. */
	public record Action(String label, String url) {
	}

	/** An image attached to the message and shown inline by its content id. */
	public record InlineImage(String contentId, String alt, int size) {
	}

	/**
	 * What a system email says, in reading order: heading, opening paragraphs, the primary
	 * action, an optional image, the secondary actions, closing paragraphs. Paragraphs are
	 * plain text. preheader is the preview line an inbox shows beside the subject.
	 */
	public record Content(String preheader, String heading, List<String> opening, Action primary,
			List<Action> secondary, InlineImage image, List<String> closing) {

		public Content {
			opening = List.copyOf(opening);
			secondary = (secondary != null) ? List.copyOf(secondary) : List.of();
			closing = (closing != null) ? List.copyOf(closing) : List.of();
		}

		public static Content of(String preheader, String heading, List<String> opening) {
			return new Content(preheader, heading, opening, null, null, null, null);
		}

		public Content withPrimary(String label, String url) {
			return new Content(preheader, heading, opening, new Action(label, url), secondary, image, closing);
		}

		/** Adds one; an email can carry several, shown in the order they were added. */
		public Content withSecondary(String label, String url) {
			List<Action> actions = new ArrayList<>(secondary);
			actions.add(new Action(label, url));
			return new Content(preheader, heading, opening, primary, actions, image, closing);
		}

		public Content withImage(InlineImage inline) {
			return new Content(preheader, heading, opening, primary, secondary, inline, closing);
		}

		public Content withClosing(List<String> paragraphs) {
			return new Content(preheader, heading, opening, primary, secondary, image, paragraphs);
		}

	}

	/**
	 * The closing line of every email: why the person is receiving it. Only an announcement
	 * carries an unsubscribe link and the List-Unsubscribe header; essential emails and event
	 * updates are always delivered and do not offer one.
	 */
	public record Footer(String reason, String unsubscribeToken, boolean sample) {

		public static final Footer SIGNED_UP = essential("You are receiving this because you signed up for PeachHacks.");

		public static final Footer REGISTERED = essential(
				"You are receiving this because you registered for PeachHacks.");

		public static final Footer SCHOOL_EMAIL = essential(
				"You are receiving this because this address was given as a school email for PeachHacks.");

		public static final Footer ADMIN_ACCOUNT = essential(
				"You are receiving this because you were given an account on the PeachHacks admin site.");

		public static final Footer SPONSOR_INQUIRY = essential(
				"You are receiving this because someone filled in the sponsor form on the PeachHacks site.");

		private static Footer essential(String reason) {
			return new Footer(reason, null, false);
		}

		public static Footer announcement(String unsubscribeToken) {
			return new Footer(SIGNED_UP.reason(), unsubscribeToken, false);
		}

		/** For the test copy and the preview, which have no unsubscribe token: the link is shown but not live. */
		public static Footer announcementSample() {
			return new Footer(SIGNED_UP.reason(), null, true);
		}

	}

	private final EmailProperties properties;

	private final String logoUrl;

	public EmailComposer(EmailProperties properties) {
		this.properties = properties;
		this.logoUrl = logoBase(properties.webBaseUrl()) + LOGO_PATH;
	}

	/** A system email. Text in content is used as written; nothing in it is a template. */
	public EmailMessage compose(String to, String subject, Content content, Footer footer) {
		StringBuilder html = new StringBuilder();
		StringBuilder text = new StringBuilder();
		html.append("<h1 class=\"ph-heading\" style=\"margin:0 0 20px;font-family:")
			.append(FONT)
			.append(";font-size:24px;line-height:1.3;font-weight:bold;color:")
			.append(NAVY)
			.append("\">")
			.append(escape(content.heading()))
			.append("</h1>");
		for (String paragraph : content.opening()) {
			appendParagraph(html, text, escape(paragraph), paragraph);
		}
		if (content.primary() != null) {
			appendAction(html, text, content.primary(), true);
		}
		if (content.image() != null) {
			InlineImage image = content.image();
			html.append("<p style=\"margin:0 0 20px\"><img src=\"cid:")
				.append(escape(image.contentId()))
				.append("\" width=\"")
				.append(image.size())
				.append("\" height=\"")
				.append(image.size())
				.append("\" alt=\"")
				.append(escape(image.alt()))
				.append("\" style=\"display:block;border:0;background:#ffffff\"></p>");
		}
		for (Action action : content.secondary()) {
			appendAction(html, text, action, false);
		}
		for (String paragraph : content.closing()) {
			appendParagraph(html, text, escape(paragraph), paragraph);
		}
		return message(to, subject.strip(), content.preheader(), html, text, footer);
	}

	/**
	 * An organizer-written campaign. The body is plain text: blank lines separate
	 * paragraphs, {{firstName}} and {{lastName}} are filled in, and bare http(s) URLs
	 * become links. Nothing else is markup. Links are made from the organizer's text
	 * before the names go in, so a name can never become a link.
	 */
	public EmailMessage composeCampaign(String to, String subject, String body, String firstName, String lastName,
			Footer footer) {
		String personalSubject = personalize(subject, firstName, lastName).replaceAll("[\\r\\n]+", " ").strip();
		String template = body.replace("\r\n", "\n").replace('\r', '\n').strip();
		String personalBody = personalize(template, firstName, lastName);
		StringBuilder html = new StringBuilder();
		StringBuilder text = new StringBuilder();
		for (String paragraph : PARAGRAPH_BREAK.split(template)) {
			if (!paragraph.isBlank()) {
				String linked = linkify(paragraph.strip());
				appendParagraph(html, text,
						personalize(linked, escapeOrNull(firstName), escapeOrNull(lastName)),
						personalize(paragraph.strip(), firstName, lastName));
			}
		}
		String flat = personalBody.replaceAll("\\s+", " ");
		String preheader = (flat.length() > PREHEADER_LENGTH) ? flat.substring(0, PREHEADER_LENGTH).strip() + "…"
				: flat;
		return message(to, personalSubject, preheader, html, text, footer);
	}

	public String unsubscribeUrl(String token) {
		return properties.webBaseUrl() + "/unsubscribe.html?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
	}

	private EmailMessage message(String to, String subject, String preheader, StringBuilder body, StringBuilder text,
			Footer footer) {
		String site = properties.webBaseUrl();
		String unsubscribeUrl = null;
		if (footer.unsubscribeToken() != null) {
			unsubscribeUrl = unsubscribeUrl(footer.unsubscribeToken());
		}
		else if (footer.sample()) {
			unsubscribeUrl = site + "/unsubscribe.html";
		}

		StringBuilder html = new StringBuilder(4096);
		html.append("<!doctype html><html lang=\"en\" xmlns:v=\"urn:schemas-microsoft-com:vml\"")
			.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"><head><meta charset=\"utf-8\">")
			.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
			.append("<meta name=\"color-scheme\" content=\"light dark\">")
			.append("<meta name=\"supported-color-schemes\" content=\"light dark\"><title>")
			.append(escape(subject))
			.append("</title>")
			.append("<!--[if mso]><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch>")
			.append("</o:OfficeDocumentSettings></xml><![endif]-->")
			.append("<!--[if !mso]><!--><link rel=\"stylesheet\"")
			.append(" href=\"https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;700&amp;display=swap\">")
			.append("<!--<![endif]-->")
			.append("<style>")
			.append("body{margin:0;padding:0}")
			.append("@media (max-width:620px){.ph-container{width:100%!important}")
			.append(".ph-card{padding:28px 20px 12px!important}.ph-band{padding-left:20px!important;")
			.append("padding-right:20px!important}}")
			.append("@media (prefers-color-scheme:dark){.ph-page{background:#00172b!important}")
			.append(".ph-card{background:#0b2d4d!important}.ph-card p,.ph-card h1,.ph-heading{color:")
			.append(CREAM)
			.append("!important}.ph-card a.ph-link{color:")
			.append(PEACH)
			.append("!important}.ph-card p.ph-url{color:")
			.append(MIST)
			.append("!important}}")
			.append("</style></head>")
			.append("<body class=\"ph-page\" style=\"margin:0;padding:0;background:")
			.append(CREAM)
			.append("\">")
			.append("<div style=\"display:none;max-height:0;max-width:0;overflow:hidden;opacity:0;")
			.append("mso-hide:all;font-size:1px;line-height:1px;color:")
			.append(CREAM)
			.append("\">")
			.append(escape(preheader))
			.append("&nbsp;&zwnj;".repeat(40))
			.append("</div>")
			.append("<table role=\"presentation\" class=\"ph-page\" width=\"100%\" cellpadding=\"0\"")
			.append(" cellspacing=\"0\" border=\"0\" bgcolor=\"")
			.append(CREAM)
			.append("\" style=\"background:")
			.append(CREAM)
			.append("\"><tr><td align=\"center\" style=\"padding:24px 12px\">")
			.append("<table role=\"presentation\" class=\"ph-container\" width=\"600\" cellpadding=\"0\"")
			.append(" cellspacing=\"0\" border=\"0\" style=\"width:600px;max-width:600px\">")
			.append("<tr><td class=\"ph-band\" align=\"center\" bgcolor=\"")
			.append(NAVY)
			.append("\" style=\"padding:28px 32px;background:")
			.append(NAVY)
			.append(";border-radius:12px 12px 0 0\"><a href=\"")
			.append(escape(site))
			.append("\" style=\"text-decoration:none\"><img src=\"")
			.append(escape(logoUrl))
			.append("\" width=\"200\" height=\"75\" alt=\"PeachHacks\" style=\"display:block;border:0;")
			.append("width:200px;height:75px;font-family:")
			.append(FONT)
			.append(";font-size:28px;font-weight:bold;line-height:75px;color:")
			.append(PEACH)
			.append("\"></a></td></tr>")
			.append("<tr><td class=\"ph-card\" bgcolor=\"#ffffff\" style=\"padding:36px 36px 20px;")
			.append("background:#ffffff\">")
			.append(body)
			.append("</td></tr>")
			.append("<tr><td class=\"ph-band\" bgcolor=\"")
			.append(NAVY)
			.append("\" style=\"padding:22px 32px;background:")
			.append(NAVY)
			.append(";border-radius:0 0 12px 12px;font-family:")
			.append(FONT)
			.append(";font-size:12px;line-height:1.6;color:")
			.append(MIST)
			.append("\"><p style=\"margin:0 0 6px;font-weight:bold;color:")
			.append(CREAM)
			.append("\">")
			.append(escape(ORGANIZATION))
			.append("</p><p style=\"margin:0 0 6px;color:")
			.append(MIST)
			.append("\">")
			.append(escape(footer.reason()));
		if (unsubscribeUrl != null) {
			html.append(" <a href=\"")
				.append(escape(unsubscribeUrl))
				.append("\" style=\"color:")
				.append(MIST)
				.append(";text-decoration:underline\">Unsubscribe</a>");
		}
		html.append("</p><p style=\"margin:0\"><a href=\"")
			.append(escape(site))
			.append("\" style=\"color:")
			.append(PEACH)
			.append(";text-decoration:underline\">")
			.append(escape(displayHost(site)))
			.append("</a></p></td></tr></table></td></tr></table></body></html>");

		text.append("--\n").append(ORGANIZATION).append('\n').append(footer.reason());
		if (unsubscribeUrl != null) {
			text.append("\nUnsubscribe: ").append(unsubscribeUrl);
		}
		text.append('\n').append(site);

		Map<String, String> headers = (footer.unsubscribeToken() != null)
				? Map.of("List-Unsubscribe", "<" + unsubscribeUrl + ">") : Map.of();
		return new EmailMessage(to, subject, html.toString(), text.toString(), headers);
	}

	private static void appendParagraph(StringBuilder html, StringBuilder text, String escaped, String plain) {
		html.append("<p style=\"").append(PARAGRAPH_STYLE).append("\">").append(escaped.replace("\n", "<br>")).append("</p>");
		text.append(plain).append("\n\n");
	}

	/**
	 * The button is drawn twice: as VML for Outlook on Windows, which ignores padding and
	 * rounded corners on links, and as a styled link for everything else.
	 */
	private static void appendAction(StringBuilder html, StringBuilder text, Action action, boolean primary) {
		String url = escape(action.url());
		String label = escape(action.label());
		String fill = primary ? PEACH : CREAM;
		// A Google Wallet link is a signed token of well over a thousand characters; printed
		// in full it buries the rest of the email.
		boolean shortEnough = action.url().length() <= MAX_PRINTED_URL_LENGTH;
		int width = Math.max(200, action.label().length() * 10 + 64);
		html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
			.append(" style=\"margin:4px 0 10px\"><tr><td>")
			.append("<!--[if mso]><v:roundrect xmlns:v=\"urn:schemas-microsoft-com:vml\"")
			.append(" xmlns:w=\"urn:schemas-microsoft-com:office:word\" href=\"")
			.append(url)
			.append("\" style=\"height:48px;v-text-anchor:middle;width:")
			.append(width)
			.append("px\" arcsize=\"17%\" strokecolor=\"")
			.append(primary ? PEACH : NAVY)
			.append("\" fillcolor=\"")
			.append(fill)
			.append("\"><w:anchorlock/><center style=\"font-family:Arial,sans-serif;font-size:16px;")
			.append("font-weight:bold;color:")
			.append(NAVY)
			.append("\">")
			.append(label)
			.append("</center></v:roundrect><![endif]-->")
			.append("<!--[if !mso]><!--><a href=\"")
			.append(url)
			.append("\" style=\"display:inline-block;padding:0 28px;border:2px solid ")
			.append(primary ? PEACH : NAVY)
			.append(";border-radius:8px;background:")
			.append(fill)
			.append(";font-family:")
			.append(FONT)
			.append(";font-size:16px;font-weight:bold;line-height:44px;color:")
			.append(NAVY)
			.append(";text-decoration:none\">")
			.append(label)
			.append("</a><!--<![endif]--></td></tr></table>")
			.append("<p class=\"ph-url\" style=\"margin:0 0 20px;font-family:")
			.append(FONT)
			.append(";font-size:13px;line-height:1.5;color:#5b7286;word-break:break-all\">")
			.append(shortEnough ? "Or open this link: " : "Button not working? ")
			.append("<a class=\"ph-link\" href=\"")
			.append(url)
			.append("\" style=\"color:")
			.append(NAVY)
			.append(";text-decoration:underline\">")
			.append(shortEnough ? url : "Open this link instead")
			.append("</a></p>");
		text.append(action.label()).append(": ").append(action.url()).append("\n\n");
	}

	/** Escapes the paragraph and turns its http(s) URLs into links; trailing punctuation stays outside. */
	private static String linkify(String paragraph) {
		StringBuilder out = new StringBuilder();
		Matcher matcher = URL.matcher(paragraph);
		int from = 0;
		while (matcher.find()) {
			String url = matcher.group().replaceAll("[.,;:!?)\\]]+$", "");
			int end = matcher.start() + url.length();
			out.append(escape(paragraph.substring(from, matcher.start())))
				.append("<a class=\"ph-link\" href=\"")
				.append(escape(url))
				.append("\" style=\"color:")
				.append(NAVY)
				.append(";text-decoration:underline\">")
				.append(escape(url))
				.append("</a>");
			from = end;
			matcher.region(end, paragraph.length());
		}
		return out.append(escape(paragraph.substring(from))).toString();
	}

	/** A local web base URL cannot serve the logo to a real inbox, so the live site's copy is used. */
	private static String logoBase(String webBaseUrl) {
		String host = displayHost(webBaseUrl);
		boolean local = host.equals("localhost") || host.startsWith("127.") || host.equals("[::1]")
				|| host.endsWith(".localhost");
		return local ? PRODUCTION_SITE : webBaseUrl;
	}

	private static String displayHost(String url) {
		try {
			String host = URI.create(url).getHost();
			if (host != null) {
				return host.startsWith("www.") ? host.substring(4) : host;
			}
		}
		catch (IllegalArgumentException ex) {
			// Falls through to the URL as written.
		}
		return url;
	}

	private static String escape(String value) {
		return HtmlUtils.htmlEscape(value, "UTF-8");
	}

	private static String escapeOrNull(String value) {
		return (value != null) ? escape(value) : null;
	}

	private static String personalize(String template, String firstName, String lastName) {
		String result = FIRST_NAME.matcher(template)
			.replaceAll(Matcher.quoteReplacement((firstName != null) ? firstName : ""));
		return LAST_NAME.matcher(result).replaceAll(Matcher.quoteReplacement((lastName != null) ? lastName : ""));
	}

}
