package com.peachhacks.backend.discord;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

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

}
