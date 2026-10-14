package com.peachhacks.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BadgePropertiesTests {

	@Test
	void aColourThatIsNotSetIsNullAndASetOneIsTrimmed() {
		BadgeProperties unset = new BadgeProperties(null, "", "   ", null);
		assertThat(unset.lanyardHostColor()).isNull();
		assertThat(unset.lanyardOtherColor()).isNull();
		assertThat(unset.lanyardSponsorColor()).isNull();
		assertThat(unset.lanyardStaffColor()).isNull();

		BadgeProperties set = new BadgeProperties(" Peach ", "Navy", "Gold", " Black and white ");
		assertThat(set.lanyardHostColor()).isEqualTo("Peach");
		assertThat(set.lanyardOtherColor()).isEqualTo("Navy");
		assertThat(set.lanyardSponsorColor()).isEqualTo("Gold");
		assertThat(set.lanyardStaffColor()).isEqualTo("Black and white");
	}

}
