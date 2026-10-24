package com.liabletech.exportdocs.check;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.reading.LcReading;

/** A credit's terms as values to check against: amounts, dates, ports, goods and documents. */
public record LcTerms(LcReading reading, String number, String currency, BigDecimal amount, BigDecimal tolerancePlus,
		BigDecimal toleranceMinus, LocalDate issueDate, LocalDate expiry, String expiryPlace, LocalDate latestShipment,
		int presentationDays, boolean presentationDaysStated, Boolean partialAllowed, Boolean transhipmentAllowed,
		GoodsTerms goods, List<RequiredDoc> requiredDocs, LcRequirements.LcConditions conditions) {

	/** UCP 600 article 14(c): 21 days after shipment unless the credit says otherwise. */
	public static final int DEFAULT_PRESENTATION_DAYS = 21;

	private static final Pattern TOLERANCE = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})");

	private static final Pattern DAYS = Pattern.compile("^\\s*(\\d{1,3})");

	public static LcTerms of(LcReading lc) {
		BigDecimal plus = BigDecimal.ZERO;
		BigDecimal minus = BigDecimal.ZERO;
		Matcher tolerance = TOLERANCE.matcher(lc.tolerance() == null ? "" : lc.tolerance());
		if (tolerance.find()) {
			plus = new BigDecimal(tolerance.group(1));
			minus = new BigDecimal(tolerance.group(2));
		}
		int days = DEFAULT_PRESENTATION_DAYS;
		boolean stated = false;
		Matcher period = DAYS.matcher(lc.presentationPeriod() == null ? "" : lc.presentationPeriod());
		if (period.find()) {
			days = Integer.parseInt(period.group(1));
			stated = true;
		}
		return new LcTerms(lc, TradeText.text(lc.lcNumber()), currency(lc.currency()), TradeText.number(lc.amount()), plus,
				minus, TradeText.date(lc.issueDate()), TradeText.date(lc.expiryDate()), TradeText.text(lc.expiryPlace()),
				TradeText.date(lc.latestShipmentDate()), days, stated, allowed(lc.partialShipments()),
				allowed(lc.transhipment()), GoodsTerms.parse(lc.goodsDescription(), lc.additionalConditions()),
				LcRequirements.documents(lc.documentsRequired()),
				LcRequirements.conditions(lc.additionalConditions(), lc.documentsRequired()));
	}

	/** The most the invoice may draw: the credit amount plus its 39A tolerance. */
	public BigDecimal maxAmount() {
		if (amount == null) {
			return null;
		}
		return amount.multiply(BigDecimal.ONE.add(tolerancePlus.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
	}

	public Optional<RequiredDoc> required(DocType type) {
		return requiredDocs.stream().filter((doc) -> doc.readAs() == type).findFirst();
	}

	/** Whether the credit wants its number on a document, through 47A or the document's own wording. */
	public boolean lcNumberRequiredOn(DocType type) {
		return conditions.lcNumberOnAllDocuments() || required(type).map(RequiredDoc::showLcNumber).orElse(false);
	}

	/** The trade term's freight marking: C- and D-terms ship freight prepaid. */
	public String impliedFreight() {
		String term = goods.incoterm();
		if (term == null) {
			return null;
		}
		return switch (term) {
			case "CFR", "CIF", "CPT", "CIP", "DAP", "DPU", "DDP" -> "PREPAID";
			case "FOB", "FCA", "FAS", "EXW" -> "COLLECT";
			default -> null;
		};
	}

	/** Partial shipments: null when the credit makes them conditional. Silence allows them (UCP 600 art. 31(a)). */
	static Boolean allowed(String value) {
		String text = value == null ? "" : value.toUpperCase(Locale.ROOT);
		if (text.isBlank()) {
			return Boolean.TRUE;
		}
		if (text.contains("NOT") || text.contains("PROHIBITED")) {
			return Boolean.FALSE;
		}
		if (text.contains("CONDITIONAL")) {
			return null;
		}
		return text.contains("ALLOWED") || text.contains("PERMITTED") ? Boolean.TRUE : null;
	}

	private static String currency(String value) {
		String text = TradeText.text(value);
		if (text == null) {
			return null;
		}
		Matcher code = Pattern.compile("\\b([A-Z]{3})\\b").matcher(text.toUpperCase(Locale.ROOT));
		return code.find() ? code.group(1) : text.toUpperCase(Locale.ROOT);
	}

}
