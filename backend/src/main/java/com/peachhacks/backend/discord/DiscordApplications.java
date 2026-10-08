package com.peachhacks.backend.discord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.peachhacks.backend.common.ApiException;
import com.peachhacks.backend.config.AcceptanceProperties;
import com.peachhacks.backend.config.DiscordProperties;
import com.peachhacks.backend.registration.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * What PeachBot tells the organizers' applications channel: a short post for each new
 * application, and a recap every evening with the totals and a chart. The channel shows
 * applicants' names and schools, so it has to be one only organizers can see.
 */
@Service
public class DiscordApplications {

	static final ZoneId ZONE = ZoneId.of("America/New_York");

	private static final int PEACH = 0xfca324;

	private static final int SKY = 0x67bed9;

	private static final int CHART_DAYS = 14;

	private static final String FOOTER = "PeachHacks 2027";

	private static final Logger log = LoggerFactory.getLogger(DiscordApplications.class);

	private final JdbcClient jdbc;

	private final DiscordClient client;

	private final DiscordProperties properties;

	private final AcceptanceProperties acceptance;

	private final TaskExecutor executor;

	public DiscordApplications(JdbcClient jdbc, DiscordClient client, DiscordProperties properties,
			AcceptanceProperties acceptance, @Qualifier("discordExecutor") TaskExecutor executor) {
		this.jdbc = jdbc;
		this.client = client;
		this.properties = properties;
		this.acceptance = acceptance;
		this.executor = executor;
	}

	/** Call after the registration is committed. Returns at once and never fails the caller. */
	public void announce(Registration registration) {
		if (!properties.applicationsConfigured()) {
			return;
		}
		try {
			executor.execute(() -> {
				try {
					long total = count("select count(*) from registrations");
					client.postMessage(properties.applicationsChannelId(), Map.of("embeds", List.of(Map.of(
							"title", "New application 🍑",
							"description", "## " + clean(registration.getFirstName() + " " + registration.getLastName()),
							"color", PEACH,
							"fields", List.of(field("School", clean(registration.getSchool()), true),
									field("Graduating", graduation(registration), true),
									field("Level of study", clean(registration.getLevelOfStudy()), false)),
							"footer", Map.of("text", FOOTER + " • Application #" + String.format("%,d", total)),
							"timestamp", registration.getCreatedAt().toString())),
							"allowed_mentions", Map.of("parse", List.of())));
				}
				catch (RuntimeException ex) {
					log.warn("Could not post application {} to Discord: {}", registration.getId(), ex.toString());
				}
			});
		}
		catch (TaskRejectedException ex) {
			log.warn("Discord queue is full; application {} was not posted", registration.getId());
		}
	}

	/** Every evening, Atlanta time. */
	@Scheduled(cron = "${app.discord.recap-cron:0 0 21 * * *}", zone = "America/New_York")
	void scheduledRecap() {
		if (!properties.applicationsConfigured()) {
			return;
		}
		try {
			postRecap();
		}
		catch (RuntimeException ex) {
			log.warn("Could not post the daily recap to Discord: {}", ex.toString());
		}
	}

	/** For an organizer who wants the recap now. */
	public void postRecapNow() {
		if (!properties.applicationsConfigured()) {
			throw ApiException.validation("PeachBot has no applications channel yet. Set DISCORD_APPLICATIONS_CHANNEL_ID.",
					null);
		}
		try {
			postRecap();
		}
		catch (RuntimeException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "DISCORD_ERROR", "Discord did not take the recap: " + ex);
		}
	}

	private void postRecap() {
		LocalDate today = LocalDate.now(ZONE);
		Map<LocalDate, Long> perDay = new LinkedHashMap<>();
		for (int i = CHART_DAYS - 1; i >= 0; i--) {
			perDay.put(today.minusDays(i), 0L);
		}
		jdbc.sql("""
				select cast(created_at at time zone 'America/New_York' as date) as day, count(*) as total
				from registrations
				where created_at >= now() - make_interval(days => cast(:days as integer))
				group by day
				""").param("days", CHART_DAYS + 1).query((rs, rowNum) -> {
			long applications = rs.getLong("total");
			perDay.computeIfPresent(rs.getObject("day", LocalDate.class), (day, none) -> applications);
			return rowNum;
		}).list();
		List<RecapChart.Day> days = new ArrayList<>();
		perDay.forEach((date, total) -> days.add(new RecapChart.Day(date, total)));
		long todayCount = perDay.get(today);
		long lastSeven = days.subList(CHART_DAYS - 7, CHART_DAYS).stream().mapToLong(RecapChart.Day::count).sum();
		long priorSeven = days.subList(0, CHART_DAYS - 7).stream().mapToLong(RecapChart.Day::count).sum();

		long total = count("select count(*) from registrations");
		String host = acceptance.hostSchoolName().toLowerCase(Locale.ROOT);
		long fromHost = jdbc
			.sql("select count(*) from registrations where left(lower(trim(school)), :length) = :host")
			.param("length", host.length())
			.param("host", host)
			.query(Long.class)
			.single();
		Instant last = jdbc.sql("select max(created_at) from registrations")
			.query(OffsetDateTime.class)
			.optional()
			.map(OffsetDateTime::toInstant)
			.orElse(null);
		List<String> schools = jdbc.sql("""
				select school, count(*) as total from registrations
				group by school order by total desc, school limit 3
				""").query((rs, rowNum) -> "**" + (rowNum + 1) + ".** " + clean(rs.getString("school")) + " — "
				+ String.format("%,d", rs.getLong("total"))).list();
		Map<String, Long> statuses = new LinkedHashMap<>();
		jdbc.sql("select status, count(*) as total from registrations group by status").query((rs, rowNum) -> {
			statuses.put(rs.getString("status"), rs.getLong("total"));
			return rowNum;
		}).list();

		List<Map<String, Object>> fields = new ArrayList<>();
		fields.add(field("Today", "**+" + todayCount + "**", true));
		fields.add(field("Last 7 days", String.format("%,d (%.0f/day)", lastSeven, lastSeven / 7.0), true));
		fields.add(field("Vs prior week", change(lastSeven, priorSeven), true));
		fields.add(field("Last application", (last != null) ? "<t:" + last.getEpochSecond() + ":R>" : "None yet", true));
		fields.add(field("Decisions",
				String.format("%,d accepted • %,d pending", statuses.getOrDefault("ACCEPTED", 0L),
						statuses.getOrDefault("PENDING", 0L)),
				true));
		fields.add(field(acceptance.hostSchoolName() + " vs other schools",
				String.format("%,d • %,d", fromHost, total - fromHost), true));
		fields.add(field("Top schools", schools.isEmpty() ? "No applications yet" : String.join("\n", schools), false));

		byte[] chart = null;
		try {
			chart = RecapChart.render(days, total, todayCount);
		}
		catch (Exception | LinkageError ex) {
			log.warn("Could not draw the recap chart: {}", ex.toString());
		}
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("author", Map.of("name", FOOTER));
		embed.put("title", "Daily recap — " + today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)));
		embed.put("description", String.format("**%,d** applications so far", total));
		embed.put("color", SKY);
		embed.put("fields", fields);
		embed.put("footer", Map.of("text", "Daily recap"));
		embed.put("timestamp", Instant.now().toString());
		if (chart != null) {
			embed.put("image", Map.of("url", "attachment://recap.png"));
		}
		client.postMessage(properties.applicationsChannelId(),
				Map.of("embeds", List.of(embed), "allowed_mentions", Map.of("parse", List.of())), "recap.png", chart);
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

	private static String change(long now, long before) {
		if (before == 0) {
			return (now == 0) ? "No change" : "▲ new";
		}
		long percent = Math.round((now - before) * 100.0 / before);
		return (percent >= 0 ? "▲ " : "▼ ") + Math.abs(percent) + "%";
	}

	private static String graduation(Registration registration) {
		if (registration.getGraduationYear() == null) {
			return "Not given";
		}
		String month = (registration.getGraduationMonth() != null)
				? Month.of(registration.getGraduationMonth()).getDisplayName(TextStyle.SHORT, Locale.US) + " " : "";
		return month + registration.getGraduationYear();
	}

	private static Map<String, Object> field(String name, String value, boolean inline) {
		return Map.of("name", name, "value", value.isBlank() ? "—" : value, "inline", inline);
	}

	/** What an applicant typed is shown as text, never as Discord formatting or a mention. */
	private static String clean(String value) {
		String text = (value != null) ? value.strip() : "";
		text = text.replaceAll("([\\\\*_~`|>#\\[\\]()@])", "\\\\$1");
		return (text.length() > 200) ? text.substring(0, 200) : text;
	}

}
