package com.peachhacks.backend.common;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class Csv {

	private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final StringBuilder out = new StringBuilder();

	private int rows = -1;

	public Csv(List<String> header) {
		row(header);
	}

	/** Not counting the header. */
	public int rows() {
		return rows;
	}

	/**
	 * For the line that records an export: the filters that were set, given as name, value,
	 * name, value. Values come from the request, so line breaks are kept out of the log.
	 */
	public static String filters(Object... namesAndValues) {
		List<String> set = new ArrayList<>();
		for (int i = 0; i < namesAndValues.length; i += 2) {
			Object value = namesAndValues[i + 1];
			if (value != null && !value.toString().isBlank()) {
				set.add(namesAndValues[i] + "=" + value.toString().strip().replaceAll("\\p{Cntrl}", "?"));
			}
		}
		return set.isEmpty() ? "no filters" : String.join(", ", set);
	}

	public Csv row(List<?> cells) {
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(cell(cells.get(i)));
		}
		out.append("\r\n");
		rows++;
		return this;
	}

	/**
	 * Values a spreadsheet would evaluate as a formula (leading = + - @, tab or carriage
	 * return) are prefixed with a single quote so they stay text.
	 */
	public static String cell(Object value) {
		if (value == null) {
			return "";
		}
		String text = value.toString();
		if (text.isEmpty()) {
			return "";
		}
		char first = text.charAt(0);
		if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
			text = "'" + text;
		}
		if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
			return '"' + text.replace("\"", "\"\"") + '"';
		}
		return text;
	}

	public ResponseEntity<byte[]> toResponse(String baseName) {
		String filename = baseName + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv";
		return ResponseEntity.ok()
			.contentType(TEXT_CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(filename).build().toString())
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(out.toString().getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String toString() {
		return out.toString();
	}

}
