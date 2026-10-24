package com.liabletech.amc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.ApplianceKind;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.label.CaptionParser;
import com.liabletech.amc.link.RenewalLinkController;
import com.liabletech.amc.reminder.ReminderMessages;
import com.liabletech.amc.reminder.ReminderService;
import com.liabletech.amc.reminder.ReminderSettings;
import com.liabletech.platform.account.Account;

/** The rules on their own: captions, appliance kinds, the reminder schedule and the words sent. */
class AmcRulesTests {

	private static final List<Integer> OFFSETS = ReminderSettings.parse(ReminderSettings.DEFAULT_OFFSETS);

	private static final LocalDate END = LocalDate.of(2026, 12, 31);

	@Test
	void captionGivesTheCustomerTheAmcAndTheRoom() {
		CaptionParser.Caption caption = CaptionParser.parse("9812345678 Sharma AMC 1499 bedroom");

		assertThat(caption.phone()).isEqualTo("+919812345678");
		assertThat(caption.name()).isEqualTo("Sharma");
		assertThat(caption.amcSold()).isTrue();
		assertThat(caption.amount()).isEqualByComparingTo("1499");
		assertThat(caption.location()).isEqualTo("Bedroom");
		assertThat(caption.paid()).isFalse();
	}

	@Test
	void captionUnderstandsHowTechniciansWrite() {
		CaptionParser.Caption noAmc = CaptionParser.parse("98765 43210 no amc");
		assertThat(noAmc.phone()).isEqualTo("+919876543210");
		assertThat(noAmc.amcSold()).isFalse();
		assertThat(noAmc.name()).isNull();

		CaptionParser.Caption comprehensive = CaptionParser.parse("Mrs Kulkarni, comprehensive amc Rs. 3,999 paid cash");
		assertThat(comprehensive.phone()).isNull();
		assertThat(comprehensive.amcSold()).isTrue();
		assertThat(comprehensive.amount()).isEqualByComparingTo("3999");
		assertThat(comprehensive.planHint()).isEqualTo("comprehensive");
		assertThat(comprehensive.paid()).isTrue();
		assertThat(comprehensive.name()).isEqualTo("Mrs Kulkarni");

		CaptionParser.Caption planByName = CaptionParser.parse("9000080027 Lakshmi Iyer RO care 2499 kitchen");
		assertThat(planByName.amcSold()).as("a plan name with its price is a sale").isTrue();
		assertThat(planByName.amount()).isEqualByComparingTo("2499");
		assertThat(planByName.planHint()).isEqualTo("care");
		assertThat(planByName.name()).isEqualTo("Lakshmi Iyer");

		assertThat(CaptionParser.parse(null).amcSold()).isFalse();
		assertThat(CaptionParser.parse("AMC 99999 hall").amount()).as("not a plausible AMC price").isNull();
	}

	@Test
	void applianceKindIsReadFromWhatTheAiOrAPersonWrites() {
		assertThat(ApplianceKind.parse("split AC")).isEqualTo(ApplianceKind.SPLIT_AC);
		assertThat(ApplianceKind.parse("SPLIT_AC")).isEqualTo(ApplianceKind.SPLIT_AC);
		assertThat(ApplianceKind.parse("Air conditioner")).isEqualTo(ApplianceKind.SPLIT_AC);
		assertThat(ApplianceKind.parse("window ac")).isEqualTo(ApplianceKind.WINDOW_AC);
		assertThat(ApplianceKind.parse("RO_PURIFIER")).isEqualTo(ApplianceKind.RO);
		assertThat(ApplianceKind.parse("geyser")).isEqualTo(ApplianceKind.WATER_HEATER);
		assertThat(ApplianceKind.parse("toaster")).isEqualTo(ApplianceKind.OTHER);
		assertThat(ApplianceKind.parse(" ")).isNull();
		assertThat(ApplianceKind.RO.family()).isEqualTo("RO");
		assertThat(ApplianceKind.CASSETTE_AC.family()).isEqualTo("AC");
	}

	@Test
	void reminderScheduleIsReadLeniently() {
		assertThat(ReminderSettings.parse("-30, -15; 0 7 +30 abc 200 -15")).containsExactly(-30, -15, 0, 7, 30);
		assertThat(ReminderSettings.parse(null)).isEmpty();
	}

	@Test
	void eachRenewalReminderGoesOnceOnItsDay() {
		Contract contract = contract(END, 3);

		assertThat(ReminderService.dueOffset(contract, OFFSETS, Set.of(), END.minusDays(30))).contains(-30);
		assertThat(ReminderService.dueOffset(contract, OFFSETS, Set.of(-30), END.minusDays(30))).isEmpty();
		assertThat(ReminderService.dueOffset(contract, OFFSETS, Set.of(), END.minusDays(20)))
			.as("10 days late is too late to send the 30-day reminder").isEmpty();
		assertThat(ReminderService.dueOffset(contract, OFFSETS, Set.of(-30), END.minusDays(13)))
			.as("a day or two late (the app was off) still goes").contains(-15);
		assertThat(ReminderService.dueOffset(contract, OFFSETS, Set.of(-30, -15, -7), END.plusDays(1))).contains(0);
		assertThat(ReminderService.dueOffset(contract, OFFSETS, Set.of(0), END.plusDays(1)))
			.as("never an older one after a newer one").isEmpty();
		assertThat(ReminderService.dueOffset(contract, OFFSETS, Set.of(-30, -15, -7, 0), END.plusDays(7))).contains(7);
	}

	@Test
	void includedVisitsAreSpreadOverTheYear() {
		Contract contract = contract(END, 3);

		assertThat(ReminderService.visitDates(contract)).extracting(ReminderService.Visit::dueOn)
			.containsExactly(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 7, 3), LocalDate.of(2026, 11, 1));
		assertThat(ReminderService.dueVisit(contract, 0, Set.of(), LocalDate.of(2026, 2, 28)))
			.hasValueSatisfying((v) -> assertThat(v.number()).isEqualTo(1));
		assertThat(ReminderService.dueVisit(contract, 0, Set.of(), LocalDate.of(2026, 2, 27))).isEmpty();
		assertThat(ReminderService.dueVisit(contract, 1, Set.of(), LocalDate.of(2026, 3, 1))).as("visit 1 done").isEmpty();
		assertThat(ReminderService.dueVisit(contract, 0, Set.of(1), LocalDate.of(2026, 3, 1))).as("already reminded").isEmpty();
		assertThat(ReminderService.visitDates(contract(END, 0))).isEmpty();
	}

	@Test
	void reminderIsWordedForHowFarTheEndDateIs() {
		Account account = account(null);
		Customer sunita = customer("Sunita Deshpande");
		Appliance ac = appliance();
		Contract contract = contract(END, 3);

		String before = ReminderMessages.renewal(account, sunita, ac, contract, new BigDecimal("1499"), END.minusDays(15),
				"https://example.test/p/renew/abc");
		assertThat(before).startsWith("Namaste Sunita ji, your AC Care AMC for the LG 1.5 TR split AC (bedroom) ends on 31 Dec 2026 (15 days left).")
			.contains("Renew for ₹1,499 to keep your 3 scheduled services")
			.contains("https://example.test/p/renew/abc")
			.contains("Reply YES to renew, or STOP")
			.endsWith("– CoolCare AC & RO, +91 90000 70000");

		String today = ReminderMessages.renewal(account, customer(null), ac, contract, new BigDecimal("1499"), END, "link");
		assertThat(today).startsWith("Namaste! Your AC Care AMC").contains("ends today");

		String lapsed = ReminderMessages.renewal(account, sunita, ac, contract, new BigDecimal("1499"), END.plusDays(30),
				"link");
		assertThat(lapsed).contains("ended on 31 Dec 2026").contains("whenever you are ready");
	}

	@Test
	void upiLinkCarriesTheExactAmount() {
		String link = RenewalLinkController.upiLink(account("coolcare@okaxis"), new BigDecimal("1499"), appliance());

		assertThat(link).isEqualTo("upi://pay?pa=coolcare@okaxis&pn=CoolCare%20AC%20%26%20RO&am=1499.00&cu=INR"
				+ "&tn=AMC%20renewal%20Split%20AC");
		assertThat(RenewalLinkController.upiLink(account(null), new BigDecimal("1499"), appliance())).isNull();
	}

	@Test
	void contractSaysHowLongIsLeft() {
		Contract contract = contract(END, 3);

		assertThat(contract.dueText(END.minusDays(9))).isEqualTo("due in 9 days");
		assertThat(contract.dueText(END)).isEqualTo("expires today");
		assertThat(contract.dueText(END.plusDays(1))).isEqualTo("expired 1 day ago");
	}

	private static Contract contract(LocalDate end, int visits) {
		return new Contract(1L, 1L, 1L, 1L, 1L, "AC Care AMC", new BigDecimal("1499"), end.minusYears(1).plusDays(1), end,
				visits, Contract.ACTIVE, null, "token", null, null, null, "OFFICE", OffsetDateTime.now());
	}

	private static Customer customer(String name) {
		return new Customer(1L, 1L, name, "+919800000000", null, null, false, null, OffsetDateTime.now());
	}

	private static Appliance appliance() {
		return new Appliance(1L, 1L, 1L, "SPLIT_AC", "LG", null, null, "1.5 TR", null, null, "bedroom", null,
				OffsetDateTime.now());
	}

	private static Account account(String upiId) {
		return new Account(1L, "CoolCare AC & RO", "Owner", "+919000070000", "owner@example.com", null, null, null, upiId,
				null, null, null, null, null, "BASIC", null, OffsetDateTime.now());
	}

}
