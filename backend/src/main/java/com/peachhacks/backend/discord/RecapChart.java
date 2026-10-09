package com.peachhacks.backend.discord;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/** The bar chart under the daily recap: applications per day, oldest on the left. */
final class RecapChart {

	record Day(LocalDate date, long count) {
	}

	static final int WIDTH = 900;

	static final int HEIGHT = 360;

	private static final Color BACKGROUND = new Color(0x00162a);

	private static final Color CREAM = new Color(0xf4f6ed);

	private static final Color MIST = new Color(0xb9d3dc);

	private static final Color PEACH = new Color(0xfca324);

	private static final Color[] BARS = { PEACH, new Color(0x67bed9), new Color(0x7fdca0), CREAM, new Color(0xff8a8a) };

	private static final int LEFT = 44;

	private static final int BASELINE = HEIGHT - 52;

	private static final int TALLEST = 188;

	private RecapChart() {
	}

	static byte[] render(List<Day> days, long total, long today) throws IOException {
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(BACKGROUND);
			g.fillRect(0, 0, WIDTH, HEIGHT);

			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14).deriveFont(Map.of(TextAttribute.TRACKING, 0.14f)));
			g.setColor(MIST);
			g.drawString("PEACHHACKS 2027", LEFT, 44);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
			g.setColor(CREAM);
			g.drawString("Applications, last " + days.size() + " days", LEFT, 74);

			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
			String totalText = String.format("%,d total", total);
			g.drawString(totalText, WIDTH - LEFT - g.getFontMetrics().stringWidth(totalText), 52);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
			g.setColor(PEACH);
			String todayText = "+" + today + " today";
			g.drawString(todayText, WIDTH - LEFT - g.getFontMetrics().stringWidth(todayText), 76);

			long peak = Math.max(1, days.stream().mapToLong(Day::count).max().orElse(1));
			float slot = (WIDTH - 2f * LEFT) / Math.max(1, days.size());
			float barWidth = slot * .78f;
			Font labels = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
			g.setFont(labels);
			FontMetrics metrics = g.getFontMetrics();
			for (int i = 0; i < days.size(); i++) {
				Day day = days.get(i);
				float x = LEFT + i * slot + (slot - barWidth) / 2;
				float height = Math.max(day.count() > 0 ? 6 : 2, TALLEST * day.count() / (float) peak);
				g.setColor((day.count() > 0) ? BARS[i % BARS.length] : new Color(0x1d3a55));
				g.fill(new RoundRectangle2D.Float(x, BASELINE - height, barWidth, height, 8, 8));
				g.setColor(MIST);
				String count = String.valueOf(day.count());
				g.drawString(count, x + (barWidth - metrics.stringWidth(count)) / 2, BASELINE - height - 8);
				String date = day.date().getMonthValue() + "/" + day.date().getDayOfMonth();
				g.drawString(date, x + (barWidth - metrics.stringWidth(date)) / 2, BASELINE + 22);
			}
		}
		finally {
			g.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

}
