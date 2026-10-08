package com.peachhacks.backend.discord;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WelcomeCardTests {

	@Test
	void drawsACardWithOrWithoutAnAvatarAndForAnyName() throws Exception {
		BufferedImage picture = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
		picture.createGraphics().setColor(Color.ORANGE);
		ByteArrayOutputStream avatar = new ByteArrayOutputStream();
		ImageIO.write(picture, "png", avatar);

		byte[] withAvatar = WelcomeCard.render("Raphael Omorose", "1557463940471066820", avatar.toByteArray());
		byte[] withoutAvatar = WelcomeCard.render("ada", "42", null);
		byte[] awkwardName = WelcomeCard.render("x".repeat(80), "7", new byte[] { 1, 2, 3 });
		byte[] noGlyphs = WelcomeCard.render("🍑🍑", "8", null);

		for (byte[] card : new byte[][] { withAvatar, withoutAvatar, awkwardName, noGlyphs }) {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(card));
			assertThat(image.getWidth()).isEqualTo(WelcomeCard.WIDTH);
			assertThat(image.getHeight()).isEqualTo(WelcomeCard.HEIGHT);
		}
		assertThat(withAvatar.length).isLessThan(1_000_000);
		Files.write(Path.of("target", "welcome-card.png"), withAvatar);
		Files.write(Path.of("target", "welcome-card-initial.png"), withoutAvatar);
	}

	@Test
	void drawsTheRecapChartForQuietAndBusyDays() throws Exception {
		long[] counts = { 24, 43, 32, 22, 36, 87, 57, 64, 19, 21, 0, 34, 32, 39 };
		List<RecapChart.Day> days = new ArrayList<>();
		for (int i = 0; i < counts.length; i++) {
			days.add(new RecapChart.Day(LocalDate.of(2026, 9, 24).plusDays(i), counts[i]));
		}

		byte[] chart = RecapChart.render(days, 1378, 39);
		byte[] empty = RecapChart.render(days.stream().map(day -> new RecapChart.Day(day.date(), 0)).toList(), 0, 0);

		for (byte[] picture : new byte[][] { chart, empty }) {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(picture));
			assertThat(image.getWidth()).isEqualTo(RecapChart.WIDTH);
			assertThat(image.getHeight()).isEqualTo(RecapChart.HEIGHT);
		}
		Files.write(Path.of("target", "recap-chart.png"), chart);
	}

}
