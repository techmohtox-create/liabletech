package com.liabletech.freight;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.liabletech.freight.chase.ChasePlan;
import com.liabletech.freight.invoice.GstMode;

/** Due dates, reminder dates and GST, without Spring. */
class FreightRulesTests {

	private static final LocalDate DUE = LocalDate.of(2026, 11, 30);

	private static final List<Integer> OFFSETS = List.of(-2, 0, 7, 15, 30);

	@Test
	void remindersFollowTheOffsetsThenRepeat() {
		assertThat(ChasePlan.next(DUE, OFFSETS, 15, LocalDate.of(2026, 11, 1), null)).isEqualTo(DUE.minusDays(2));
		assertThat(ChasePlan.next(DUE, OFFSETS, 15, DUE.minusDays(2), null)).isEqualTo(DUE);
		assertThat(ChasePlan.next(DUE, OFFSETS, 15, DUE.plusDays(3), null)).isEqualTo(DUE.plusDays(7));
		assertThat(ChasePlan.next(DUE, OFFSETS, 15, DUE.plusDays(30), null)).isEqualTo(DUE.plusDays(45));
		assertThat(ChasePlan.next(DUE, OFFSETS, 15, DUE.plusDays(50), null)).isEqualTo(DUE.plusDays(60));
	}

	@Test
	void aPromiseMovesTheNextReminderToTheDayAfter() {
		LocalDate promised = DUE.plusDays(12);
		assertThat(ChasePlan.next(DUE, OFFSETS, 15, DUE.plusDays(3), promised)).isEqualTo(promised.plusDays(1));
		// A promise already broken no longer holds anything back.
		assertThat(ChasePlan.next(DUE, OFFSETS, 15, DUE.plusDays(20), DUE.plusDays(12))).isEqualTo(DUE.plusDays(30));
	}

	@Test
	void forwardGstSplitsWithinAStateAndIsIntegratedAcrossStates() {
		GstMode.Totals sameState = GstMode.FORWARD.compute(new BigDecimal("33000"), BigDecimal.valueOf(5), "27", "27",
				BigDecimal.ZERO);
		assertThat(sameState.cgst()).isEqualByComparingTo("825");
		assertThat(sameState.sgst()).isEqualByComparingTo("825");
		assertThat(sameState.igst()).isEqualByComparingTo("0");
		assertThat(sameState.total()).isEqualByComparingTo("34650");

		GstMode.Totals interstate = GstMode.FORWARD.compute(new BigDecimal("33000"), BigDecimal.valueOf(5), "27", "24",
				BigDecimal.ZERO);
		assertThat(interstate.igst()).isEqualByComparingTo("1650");
	}

	@Test
	void advancesAreDeductedAndTheTotalIsRoundedToTheRupee() {
		GstMode.Totals totals = GstMode.FORWARD.compute(new BigDecimal("42010.50"), BigDecimal.valueOf(5), "27", "29",
				new BigDecimal("10000"));
		// 42010.50 + IGST 2100.53 - 10000 = 34111.03, payable 34111.00
		assertThat(totals.igst()).isEqualByComparingTo("2100.53");
		assertThat(totals.total()).isEqualByComparingTo("34111");
		assertThat(totals.roundOff()).isEqualByComparingTo("-0.03");
	}

	@Test
	void reverseChargeAddsNoTax() {
		GstMode.Totals totals = GstMode.RCM.compute(new BigDecimal("72000"), BigDecimal.valueOf(5), "27", "27",
				new BigDecimal("15000"));
		assertThat(totals.cgst().add(totals.sgst()).add(totals.igst())).isEqualByComparingTo("0");
		assertThat(totals.total()).isEqualByComparingTo("57000");
	}

}
