package com.peachhacks.backend.acceptance;

import java.math.BigDecimal;

import com.peachhacks.backend.config.AcceptanceProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostSchoolShareTests {

	private static final BigDecimal SEVENTY = new BigDecimal("0.70");

	@Test
	void anEmptyGroupHasNoShareAndNothingToFix() {
		HostSchoolShare share = HostSchoolShare.of(0, 0, SEVENTY);

		assertThat(share.share()).isNull();
		assertThat(share.met()).isTrue();
		assertThat(share.moreHostNeeded()).isZero();
		assertThat(share.fewerOthersNeeded()).isZero();
	}

	@Test
	void exactlyAtTheTargetIsMet() {
		for (long total : new long[] { 10, 30, 70, 90, 110, 1000 }) {
			HostSchoolShare share = HostSchoolShare.of(total * 7 / 10, total, SEVENTY);

			assertThat(share.met()).as("%d of %d", share.host(), total).isTrue();
			assertThat(share.moreHostNeeded()).isZero();
			assertThat(share.fewerOthersNeeded()).isZero();
			assertThat(share.other()).isEqualTo(total - share.host());
		}
		assertThat(HostSchoolShare.of(7, 10, SEVENTY).share()).isEqualTo(0.7);
	}

	@Test
	void justUnderTheTargetSaysHowManyWouldFixIt() {
		HostSchoolShare share = HostSchoolShare.of(69, 100, SEVENTY);
		assertThat(share.met()).isFalse();
		// 73 of 104 is 70.2%; 72 of 103 is 69.9%.
		assertThat(share.moreHostNeeded()).isEqualTo(4);
		// 69 of 98 is 70.4%; 69 of 99 is 69.7%.
		assertThat(share.fewerOthersNeeded()).isEqualTo(2);

		share = HostSchoolShare.of(6, 10, SEVENTY);
		assertThat(share.share()).isEqualTo(0.6);
		assertThat(share.moreHostNeeded()).isEqualTo(4);
		assertThat(share.fewerOthersNeeded()).isEqualTo(2);

		share = HostSchoolShare.of(2, 3, SEVENTY);
		assertThat(share.met()).isFalse();
		assertThat(share.moreHostNeeded()).isEqualTo(1);
		assertThat(share.fewerOthersNeeded()).isEqualTo(1);
	}

	@Test
	void theNumbersNeededAlwaysReachTheTargetAndOneFewerNeverDoes() {
		for (long total = 1; total <= 60; total++) {
			for (long host = 0; host <= total; host++) {
				HostSchoolShare share = HostSchoolShare.of(host, total, SEVENTY);
				assertThat(share.met()).as("%d of %d", host, total).isEqualTo(host * 10 >= total * 7);
				if (share.met()) {
					continue;
				}
				long more = share.moreHostNeeded();
				assertThat((host + more) * 10).isGreaterThanOrEqualTo((total + more) * 7);
				assertThat((host + more - 1) * 10).isLessThan((total + more - 1) * 7);
				long fewer = share.fewerOthersNeeded();
				assertThat(fewer).isBetween(1L, total - host);
				assertThat(host * 10).isGreaterThanOrEqualTo((total - fewer) * 7);
				assertThat(host * 10).isLessThan((total - fewer + 1) * 7);
			}
		}
	}

	@Test
	void nobodyFromTheHostSchoolMeansEveryoneElseWouldHaveToGo() {
		HostSchoolShare share = HostSchoolShare.of(0, 3, SEVENTY);

		assertThat(share.share()).isZero();
		assertThat(share.moreHostNeeded()).isEqualTo(7);
		assertThat(share.fewerOthersNeeded()).isEqualTo(3);
	}

	@Test
	void aTargetOfEveryoneCannotBeReachedByAddingPeople() {
		HostSchoolShare share = HostSchoolShare.of(4, 5, BigDecimal.ONE);

		assertThat(share.met()).isFalse();
		assertThat(share.moreHostNeeded()).isNull();
		assertThat(share.fewerOthersNeeded()).isEqualTo(1);
		assertThat(HostSchoolShare.of(5, 5, BigDecimal.ONE).met()).isTrue();
		assertThat(HostSchoolShare.of(0, 5, BigDecimal.ZERO).met()).isTrue();
	}

	@Test
	void theTargetMustBeAFractionAndEverySettingHasADefault() {
		AcceptanceProperties defaults = new AcceptanceProperties(" ", null, null);
		assertThat(defaults.hostSchoolName()).isEqualTo("Georgia State University");
		assertThat(defaults.hostSchoolTarget()).isEqualByComparingTo("0.70");
		assertThat(defaults.nonHostMinimumAge()).isEqualTo(18);
		assertThat(new AcceptanceProperties(null, null, 21).nonHostMinimumAge()).isEqualTo(21);

		assertThatThrownBy(() -> new AcceptanceProperties(null, new BigDecimal("70"), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HOST_SCHOOL_TARGET");
		assertThatThrownBy(() -> new AcceptanceProperties(null, null, -1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("NON_HOST_MINIMUM_AGE");
	}

}
