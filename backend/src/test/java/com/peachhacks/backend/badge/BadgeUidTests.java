package com.peachhacks.backend.badge;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import com.peachhacks.backend.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BadgeUidTests {

	@Test
	void everySpellingOfACardIsOneUid() {
		for (String spelling : new String[] { "04:A1:B2:C3:D4:E5:F6", "04-a1-b2-c3-d4-e5-f6", "04a1b2c3d4e5f6",
				"04A1B2C3D4E5F6", " 04 a1 b2 c3 d4 e5 f6 ", "04:a1-B2 c3\td4:E5:f6\n", "04A1B2C3-D4E5F6" }) {
			assertThat(BadgeUid.normalise(spelling)).as(spelling).isEqualTo("04A1B2C3D4E5F6");
		}
	}

	@Test
	void fourSevenAndTenByteUidsAreAccepted() {
		assertThat(BadgeUid.normalise("de:ad:be:ef")).isEqualTo("DEADBEEF");
		assertThat(BadgeUid.normalise("04a1b2c3d4e5f6")).isEqualTo("04A1B2C3D4E5F6");
		assertThat(BadgeUid.normalise("00-11-22-33-44-55-66-77-88-99")).isEqualTo("00112233445566778899");
	}

	@Test
	void anythingElseIsAFieldErrorOnUid() {
		for (String bad : Arrays.asList(null, "", "   ", ":-:", "04", "04A1B2C", "04A1B2C3D", "04A1B2C3D4E5",
				"04A1B2C3D4E5F", "04A1B2C3D4E5F6A", "04A1B2C3D4E5F6A7", "04A1B2C3D4E5F6A7B8C9D", "04A1B2C3D4E5F6A7B8C9D0E1",
				"0xA1B2C3D4E5F6", "04A1B2C3D4E5G6", "04_A1_B2_C3_D4_E5_F6", "04.A1.B2.C3.D4.E5.F6", "０４A1B2C3D4E5F6",
				"04A1B2C3D4E5F6".repeat(5), "04 A1 B2 C3 D4 E5 F6" + " ".repeat(60))) {
			assertThatThrownBy(() -> BadgeUid.normalise(bad)).as(String.valueOf(bad))
				.isInstanceOfSatisfying(ApiException.class, ex -> {
					assertThat(ex.getStatus().value()).isEqualTo(400);
					assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
					assertThat(ex.getFieldErrors()).containsOnlyKeys("uid");
				});
		}
	}

	@Test
	void aTapTimeIsBelievedOnlyInsideTheWindow() {
		Instant now = Instant.parse("2027-02-06T15:00:00Z");
		assertThat(BadgeService.tapTime(null, now)).isEqualTo(now);
		assertThat(BadgeService.tapTime(now, now)).isEqualTo(now);
		Instant earlier = now.minus(Duration.ofHours(5));
		assertThat(BadgeService.tapTime(earlier, now)).isEqualTo(earlier);
		assertThat(BadgeService.tapTime(now.minus(Duration.ofHours(72)), now)).isEqualTo(now.minus(Duration.ofHours(72)));
		assertThat(BadgeService.tapTime(now.minus(Duration.ofHours(72)).minusSeconds(1), now)).isEqualTo(now);
		assertThat(BadgeService.tapTime(now.plusSeconds(60), now)).isEqualTo(now.plusSeconds(60));
		assertThat(BadgeService.tapTime(now.plusSeconds(61), now)).isEqualTo(now);
		assertThat(BadgeService.tapTime(now.plus(Duration.ofDays(1)), now)).isEqualTo(now);
	}

}
