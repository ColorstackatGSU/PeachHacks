package com.peachhacks.backend.email;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.peachhacks.backend.config.EmailProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailComposer {

	private static final Pattern FIRST_NAME = Pattern.compile("\\{\\{\\s*firstName\\s*}}");

	private static final Pattern LAST_NAME = Pattern.compile("\\{\\{\\s*lastName\\s*}}");

	private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t]*\\n\\s*");

	private final EmailProperties properties;

	public EmailComposer(EmailProperties properties) {
		this.properties = properties;
	}

	public EmailMessage compose(String to, String subject, String body, String firstName, String lastName,
			String unsubscribeToken) {
		String personalSubject = personalize(subject, firstName, lastName).replaceAll("[\\r\\n]+", " ").strip();
		String personalBody = personalize(body, firstName, lastName).replace("\r\n", "\n").replace('\r', '\n').strip();
		String unsubscribeUrl = (unsubscribeToken != null) ? unsubscribeUrl(unsubscribeToken) : null;

		StringBuilder html = new StringBuilder();
		html.append("<!doctype html><html><body style=\"margin:0;padding:0;background:#fff7f0;")
			.append("font-family:Arial,Helvetica,sans-serif;color:#2b2b2b\">")
			.append("<div style=\"max-width:560px;margin:0 auto;padding:32px 24px\">")
			.append("<div style=\"font-size:24px;font-weight:bold;color:#e8703a;margin-bottom:24px\">PeachHacks</div>");
		for (String paragraph : PARAGRAPH_BREAK.split(personalBody)) {
			if (paragraph.isBlank()) {
				continue;
			}
			html.append("<p style=\"font-size:16px;line-height:1.5;margin:0 0 16px\">")
				.append(HtmlUtils.htmlEscape(paragraph.strip(), "UTF-8").replace("\n", "<br>"))
				.append("</p>");
		}
		StringBuilder text = new StringBuilder(personalBody);
		if (unsubscribeUrl != null) {
			html.append("<hr style=\"border:none;border-top:1px solid #f0d9c8;margin:24px 0 16px\">")
				.append("<p style=\"font-size:12px;line-height:1.5;color:#777777;margin:0\">")
				.append("You are receiving this because you signed up for PeachHacks. ")
				.append("<a style=\"color:#777777\" href=\"")
				.append(HtmlUtils.htmlEscape(unsubscribeUrl, "UTF-8"))
				.append("\">Unsubscribe</a></p>");
			text.append("\n\n--\nYou are receiving this because you signed up for PeachHacks.\nUnsubscribe: ")
				.append(unsubscribeUrl);
		}
		html.append("</div></body></html>");

		Map<String, String> headers = (unsubscribeUrl != null) ? Map.of("List-Unsubscribe", "<" + unsubscribeUrl + ">")
				: Map.of();
		return new EmailMessage(to, personalSubject, html.toString(), text.toString(), headers);
	}

	public String unsubscribeUrl(String token) {
		return properties.webBaseUrl() + "/unsubscribe.html?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
	}

	private static String personalize(String template, String firstName, String lastName) {
		String result = FIRST_NAME.matcher(template)
			.replaceAll(Matcher.quoteReplacement((firstName != null) ? firstName : ""));
		return LAST_NAME.matcher(result).replaceAll(Matcher.quoteReplacement((lastName != null) ? lastName : ""));
	}

}
