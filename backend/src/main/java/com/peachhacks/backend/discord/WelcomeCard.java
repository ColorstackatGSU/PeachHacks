package com.peachhacks.backend.discord;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Draws the picture PeachBot posts when someone joins the server: the PeachHacks night
 * sky and skyline with the newcomer's avatar and name. Everything is drawn here, so the
 * only things it needs from outside are the logo in the jar and a sans-serif font on the
 * machine (the Docker image installs one).
 */
final class WelcomeCard {

	static final int WIDTH = 1000;

	static final int HEIGHT = 380;

	private static final Color NIGHT = new Color(0x00162a);

	private static final Color BLUE = new Color(0x003f64);

	private static final Color NAVY = new Color(0x001f3a);

	private static final Color PEACH = new Color(0xfca324);

	private static final Color CREAM = new Color(0xf4f6ed);

	private static final Color MIST = new Color(0xb9d3dc);

	private static final Color FAR_BUILDING = new Color(0x0b4f7a);

	private static final Color NEAR_BUILDING = new Color(0x012a47);

	private static final int AVATAR = 196;

	private static final int AVATAR_X = 70;

	private static final int AVATAR_Y = 70;

	private static final int TEXT_X = 320;

	/** x, width and height of each building; the same skyline on every card. */
	private static final int[][] FAR = { { 0, 90, 70 }, { 110, 60, 120 }, { 200, 110, 84 }, { 340, 70, 138 },
			{ 430, 120, 96 }, { 580, 64, 150 }, { 660, 130, 78 }, { 810, 70, 124 }, { 900, 100, 90 } };

	private static final int[][] NEAR = { { 40, 100, 52 }, { 160, 70, 88 }, { 260, 130, 60 }, { 420, 60, 104 },
			{ 500, 140, 56 }, { 670, 80, 96 }, { 770, 120, 64 }, { 910, 90, 84 } };

	private WelcomeCard() {
	}

	/** avatar may be null or unreadable; the card then shows the first letter of the name instead. */
	static byte[] render(String name, String userId, byte[] avatar) throws IOException {
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			sky(g, userId);
			skyline(g);
			avatar(g, name, avatar);
			words(g, name);
		}
		finally {
			g.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	/** The stars are placed from the person's id, so each card's sky is a little different. */
	private static void sky(Graphics2D g, String userId) {
		g.setPaint(new GradientPaint(0, 0, NIGHT, 0, HEIGHT, BLUE));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		Random random = new Random(userId.hashCode());
		for (int i = 0; i < 46; i++) {
			int x = random.nextInt(WIDTH);
			int y = random.nextInt(HEIGHT - 150);
			float size = 1.5f + random.nextFloat() * 2.5f;
			g.setColor((i % 9 == 0) ? withAlpha(PEACH, 220) : withAlpha(CREAM, 120 + random.nextInt(120)));
			g.fill(new Ellipse2D.Float(x, y, size, size));
		}
		int moonX = WIDTH - 130;
		int moonY = 44;
		g.setPaint(new RadialGradientPaint(moonX + 36, moonY + 36, 110, new float[] { 0f, 1f },
				new Color[] { withAlpha(CREAM, 70), withAlpha(CREAM, 0) }));
		g.fillOval(moonX - 74, moonY - 74, 220, 220);
		g.setColor(CREAM);
		g.fillOval(moonX, moonY, 72, 72);
		g.setColor(new Color(0xdee0db));
		g.fillOval(moonX + 16, moonY + 18, 14, 14);
		g.fillOval(moonX + 42, moonY + 38, 18, 18);
		g.fillOval(moonX + 26, moonY + 50, 8, 8);
	}

	private static void skyline(Graphics2D g) {
		for (int[] building : FAR) {
			g.setColor(FAR_BUILDING);
			g.fillRect(building[0], HEIGHT - building[2], building[1], building[2]);
		}
		Random lights = new Random(2027);
		for (int[] building : NEAR) {
			g.setColor(NEAR_BUILDING);
			g.fillRect(building[0], HEIGHT - building[2], building[1], building[2]);
			for (int y = HEIGHT - building[2] + 10; y < HEIGHT - 8; y += 16) {
				for (int x = building[0] + 10; x < building[0] + building[1] - 10; x += 16) {
					if (lights.nextInt(4) == 0) {
						g.setColor(withAlpha(PEACH, 200));
						g.fillRect(x, y, 6, 8);
					}
				}
			}
		}
	}

	private static void avatar(Graphics2D g, String name, byte[] avatar) {
		g.setPaint(new RadialGradientPaint(AVATAR_X + AVATAR / 2f, AVATAR_Y + AVATAR / 2f, AVATAR * .78f,
				new float[] { .55f, 1f }, new Color[] { withAlpha(PEACH, 90), withAlpha(PEACH, 0) }));
		g.fillOval(AVATAR_X - 60, AVATAR_Y - 60, AVATAR + 120, AVATAR + 120);
		BufferedImage picture = read(avatar);
		Ellipse2D.Float circle = new Ellipse2D.Float(AVATAR_X, AVATAR_Y, AVATAR, AVATAR);
		if (picture != null) {
			Graphics2D clipped = (Graphics2D) g.create();
			clipped.setClip(circle);
			clipped.drawImage(picture, AVATAR_X, AVATAR_Y, AVATAR, AVATAR, null);
			clipped.dispose();
		}
		else {
			g.setColor(MIST);
			g.fill(circle);
			String initial = name.isBlank() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase();
			Font font = new Font(Font.SANS_SERIF, Font.BOLD, 96);
			if (font.canDisplayUpTo(initial) != -1) {
				initial = "?";
			}
			g.setFont(font);
			FontMetrics metrics = g.getFontMetrics();
			g.setColor(NAVY);
			g.drawString(initial, AVATAR_X + (AVATAR - metrics.stringWidth(initial)) / 2f,
					AVATAR_Y + (AVATAR - metrics.getHeight()) / 2f + metrics.getAscent());
		}
		g.setStroke(new BasicStroke(8f));
		g.setColor(PEACH);
		g.draw(circle);
		g.setStroke(new BasicStroke(3f));
		g.setColor(NAVY);
		g.draw(new Ellipse2D.Float(AVATAR_X + 6, AVATAR_Y + 6, AVATAR - 12, AVATAR - 12));
	}

	private static void words(Graphics2D g, String name) {
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22).deriveFont(Map.of(TextAttribute.TRACKING, 0.18f)));
		g.setColor(MIST);
		g.drawString("WELCOME TO", TEXT_X, 96);

		BufferedImage logo = logo();
		if (logo != null) {
			int width = 330;
			int height = Math.round(width * (float) logo.getHeight() / logo.getWidth());
			g.drawImage(logo, TEXT_X - 14, 92, width, height, null);
		}
		else {
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
			g.setColor(PEACH);
			g.drawString("PeachHacks", TEXT_X, 176);
		}

		int size = 54;
		Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
		String shown = (font.canDisplayUpTo(name) == -1 && !name.isBlank()) ? name : "";
		int room = WIDTH - TEXT_X - 150;
		while (size > 30 && g.getFontMetrics(font).stringWidth(shown) > room) {
			size -= 2;
			font = font.deriveFont((float) size);
		}
		FontMetrics metrics = g.getFontMetrics(font);
		while (!shown.isEmpty() && metrics.stringWidth(shown) > room) {
			shown = shown.substring(0, shown.length() - 2).stripTrailing() + "…";
		}
		g.setFont(font);
		g.setComposite(AlphaComposite.SrcOver.derive(.55f));
		g.setColor(new Color(0x000c18));
		g.drawString(shown, TEXT_X + 3, 263);
		g.setComposite(AlphaComposite.SrcOver);
		g.setColor(CREAM);
		g.drawString(shown, TEXT_X, 260);
	}

	private static BufferedImage read(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		try {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		}
		catch (IOException ex) {
			return null;
		}
	}

	private static BufferedImage logo() {
		try (InputStream in = WelcomeCard.class.getResourceAsStream("/discord/logo.png")) {
			return (in != null) ? ImageIO.read(in) : null;
		}
		catch (IOException ex) {
			return null;
		}
	}

	private static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

}
