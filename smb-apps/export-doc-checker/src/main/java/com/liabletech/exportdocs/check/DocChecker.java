package com.liabletech.exportdocs.check;

import static com.liabletech.exportdocs.check.Severity.CRITICAL;
import static com.liabletech.exportdocs.check.Severity.INFO;
import static com.liabletech.exportdocs.check.Severity.WARNING;
import static com.liabletech.exportdocs.check.TradeText.blank;
import static com.liabletech.exportdocs.check.TradeText.money;
import static com.liabletech.exportdocs.check.TradeText.qty;
import static com.liabletech.exportdocs.check.TradeText.show;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.reading.InsuranceReading;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.OriginReading;
import com.liabletech.exportdocs.reading.PackingListReading;
import com.liabletech.exportdocs.reading.TransportDocReading;
import com.liabletech.platform.text.Fuzzy;

/**
 * Examines a presentation the way the negotiating bank will: each document against the credit
 * (UCP 600 and international standard banking practice) and the documents against each other
 * (article 14(d): data must not conflict). Findings say what is wrong, where the rule comes from
 * and who has to fix it.
 *
 * <p>
 * The check never suggests altering a fact. A late shipment is put to the buyer as an amendment or
 * a waiver, not to the forwarder as a different date.
 */
public final class DocChecker {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private static final BigDecimal WEIGHT_ROUNDING = new BigDecimal("0.5");

	private static final Set<String> DEFECTS = Set.of("DAMAGED", "DAMAGE", "TORN", "WET", "STAINED", "BROKEN", "RUSTY",
			"DENTED", "LEAKING", "LEAKAGE", "INSUFFICIENT", "CRUSHED", "CONTAMINATED", "UNPROTECTED", "SECOND HAND",
			"OLD BAGS", "BURST", "SLACK", "MOULDY", "MOLDY");

	private final CheckInput in;

	private final LcTerms lc;

	private final LocalDate presentation;

	private final List<FindingDraft> out = new ArrayList<>();

	private DocChecker(CheckInput in) {
		this.in = in;
		this.lc = in.lc() == null ? null : LcTerms.of(in.lc());
		this.presentation = in.presentationDate() != null ? in.presentationDate() : in.today();
	}

	public static List<FindingDraft> check(CheckInput in) {
		DocChecker checker = new DocChecker(in);
		if (checker.lc != null) {
			checker.credit();
		}
		checker.invoice();
		checker.packingList();
		checker.transport();
		checker.origin();
		checker.insurance();
		checker.notCalledFor();
		List<FindingDraft> findings = new ArrayList<>(checker.out);
		findings.sort(Comparator.comparing(FindingDraft::severity));
		return findings;
	}

	// ---------------------------------------------------------------- the credit itself

	private void credit() {
		if (lc.expiry() != null) {
			if (presentation.isAfter(lc.expiry())) {
				add(CRITICAL, "LC_EXPIRED", "", "The credit will have expired",
						"Presenting on %s is after the expiry date %s (field 31D). An expired credit cannot be honoured."
							.formatted(show(presentation), show(lc.expiry())),
						"Presented by " + show(lc.expiry()), show(presentation), "UCP 600 art. 6(e)", Fixer.BUYER,
						"Extend the expiry date in field 31D to at least " + show(presentation.plusDays(10)) + ".", DocType.LC);
			}
			else {
				long days = ChronoUnit.DAYS.between(presentation, lc.expiry());
				if (days <= 5) {
					add(WARNING, "LC_EXPIRY_SOON", "", "The credit expires in " + days + (days == 1 ? " day" : " days"),
							"Documents must reach the nominated bank by %s. A discrepancy found after that cannot be corrected."
								.formatted(show(lc.expiry())),
							null, null, "UCP 600 art. 6(e)", Fixer.EXPORTER,
							"Present the documents before " + show(lc.expiry()) + ".", DocType.LC);
				}
			}
		}
		if (lc.expiryPlace() != null && !upper(lc.expiryPlace()).contains("INDIA")) {
			add(WARNING, "LC_EXPIRY_ABROAD", "", "The credit expires abroad, in " + lc.expiryPlace(),
					"Documents must reach the bank in %s by the expiry date, not just leave India. Courier time comes out of your presentation period. Credits for Indian exporters are usually made to expire in India and to be available with any bank in India."
						.formatted(lc.expiryPlace()),
					"Expiry in INDIA", lc.expiryPlace(), "UCP 600 art. 6(d)", Fixer.BUYER,
					"Change the place of expiry in field 31D to INDIA, with the credit available with any bank in India (41a).",
					DocType.LC);
		}
		if (in.transport() == null && lc.latestShipment() != null) {
			long days = ChronoUnit.DAYS.between(in.today(), lc.latestShipment());
			if (days < 0) {
				add(CRITICAL, "LC_SHIPMENT_DATE_PASSED", "", "The latest shipment date has passed",
						"Field 44C allowed shipment until %s. Goods shipped now would be late and the documents refused."
							.formatted(show(lc.latestShipment())),
						"Shipped by " + show(lc.latestShipment()), null, "UCP 600 art. 14(c); field 44C", Fixer.BUYER,
						"Extend the latest shipment date in field 44C, and the expiry in 31D with it.", DocType.LC);
			}
			else if (days <= 7) {
				add(WARNING, "LC_SHIPMENT_SOON", "", "Ship by " + show(lc.latestShipment()),
						"The bill of lading's on-board date must not be later than %s (field 44C): %d days from today."
							.formatted(show(lc.latestShipment()), days),
						null, null, "Field 44C", Fixer.EXPORTER,
						"Get the goods on board by " + show(lc.latestShipment()) + ", or ask for an extension now.",
						DocType.LC);
			}
		}
		if (lc.presentationDaysStated() && lc.presentationDays() < LcTerms.DEFAULT_PRESENTATION_DAYS) {
			add(WARNING, "LC_SHORT_PRESENTATION", "", "Only " + lc.presentationDays() + " days to present after shipment",
					"Field 48 allows %d days after shipment instead of the usual 21. Have every other document ready before the goods sail."
						.formatted(lc.presentationDays()),
					"21 days", lc.presentationDays() + " days", "UCP 600 art. 14(c); field 48", Fixer.BUYER,
					"Allow 21 days after shipment for presentation (field 48).", DocType.LC);
		}
		String rules = upper(in.lc().applicableRules());
		if (!rules.isBlank() && !rules.contains("UCP")) {
			add(INFO, "LC_RULES", "", "The credit is not subject to UCP 600",
					"Field 40E says " + in.lc().applicableRules() + ". The checks here follow UCP 600; ask your bank how this credit is examined.",
					null, null, "Field 40E", Fixer.EXPORTER, null, DocType.LC);
		}
		for (String clause : lc.conditions().softClauses()) {
			add(WARNING, "LC_SOFT_CLAUSE", Integer.toHexString(clause.hashCode()), "A condition depends on the buyer",
					"This condition needs the buyer or their agent to issue, sign or approve something. If they do not, you cannot present complying documents and the credit stops protecting you: \""
							+ clause + "\"",
					null, clause, "Credit terms (46A/47A)", Fixer.BUYER,
					"Delete this condition, or let the beneficiary issue the document: \"" + abbreviate(clause, 140) + "\"",
					DocType.LC);
		}
	}

	// ---------------------------------------------------------------- commercial invoice

	private void invoice() {
		InvoiceReading inv = in.invoice();
		if (inv == null) {
			return;
		}
		BigDecimal total = invoiceTotal(inv);
		if (lc != null) {
			String currency = upper(inv.currency());
			if (lc.currency() != null && !currency.isBlank() && !currency.contains(lc.currency())) {
				add(CRITICAL, "INV_CURRENCY", "", "Invoice is not in the credit's currency",
						"The credit is in %s; the invoice is in %s.".formatted(lc.currency(), inv.currency()), lc.currency(),
						inv.currency(), "UCP 600 art. 18(a)(iii)", Fixer.EXPORTER,
						"Make out the invoice in " + lc.currency() + ".", DocType.INVOICE);
			}
			BigDecimal max = lc.maxAmount();
			if (max != null && total != null && total.compareTo(max) > 0) {
				add(CRITICAL, "INV_AMOUNT_OVER", "", "Invoice amount is more than the credit allows",
						"The invoice totals %s; the credit is for %s%s, so at most %s can be drawn.".formatted(
								money(lc.currency(), total), money(lc.currency(), lc.amount()),
								lc.tolerancePlus().signum() > 0 ? " plus " + qty(lc.tolerancePlus()) + "% (39A)" : "",
								money(lc.currency(), max)),
						"At most " + money(lc.currency(), max), money(lc.currency(), total), "UCP 600 art. 18(b), 30",
						Fixer.EXPORTER, "Invoice no more than " + money(lc.currency(), max)
								+ " under the credit; collect any excess outside it.",
						DocType.INVOICE);
			}
			if (Boolean.FALSE.equals(lc.partialAllowed()) && lc.amount() != null && total != null) {
				BigDecimal shortfall = lc.toleranceMinus().max(BigDecimal.valueOf(5));
				BigDecimal floor = lc.amount().multiply(BigDecimal.ONE.subtract(shortfall.movePointLeft(2)));
				if (total.compareTo(floor) < 0) {
					add(WARNING, "INV_UNDER_DRAWN", "", "Drawing less than the credit while partial shipments are not allowed",
							"The invoice draws %s of %s. With partial shipments not allowed (43P), a drawing more than %s%% short is accepted only if the full quantity was shipped and the unit price is not reduced."
								.formatted(money(lc.currency(), total), money(lc.currency(), lc.amount()), qty(shortfall)),
							"About " + money(lc.currency(), lc.amount()), money(lc.currency(), total), "UCP 600 art. 30(c), 31",
							Fixer.EXPORTER, null, DocType.INVOICE);
				}
			}
			invoiceQuantity(inv);
			invoiceUnitPrice(inv);
			invoiceDescription(inv);
			invoiceTerms(inv);
			party(DocType.INVOICE, "INV_APPLICANT", "buyer", in.lc().applicant(), inv.buyer(), "UCP 600 art. 18(a)(ii)",
					"Make the invoice out to the applicant exactly as in field 50: ");
			party(DocType.INVOICE, "INV_BENEFICIARY", "seller", in.lc().beneficiary(), inv.seller(), "UCP 600 art. 18(a)(i)",
					"Issue the invoice in the beneficiary's name exactly as in field 59: ");
			lcNumber(DocType.INVOICE, inv.lcNumber(), Fixer.EXPORTER);
			lc.required(DocType.INVOICE).filter(RequiredDoc::signed).ifPresent((required) -> {
				if ("NO".equalsIgnoreCase(TradeText.text(inv.signed()))) {
					add(CRITICAL, "INV_UNSIGNED", "", "The credit asks for a signed invoice",
							"Field 46A item %d asks for a signed commercial invoice; this one is not signed.".formatted(required.number()),
							"Signed", "Not signed", "Field 46A", Fixer.EXPORTER, "Sign the original invoices.", DocType.INVOICE);
				}
			});
			String origin = lc.goods().origin();
			if (origin != null && !blank(inv.countryOfOrigin()) && !upper(inv.countryOfOrigin()).contains(origin)) {
				add(WARNING, "INV_ORIGIN", "", "Country of origin differs from the credit",
						"Field 45A gives the origin as %s; the invoice says %s.".formatted(origin, inv.countryOfOrigin()),
						origin, inv.countryOfOrigin(), "UCP 600 art. 14(d)", Fixer.EXPORTER, "Show the origin as " + origin + ".",
						DocType.INVOICE);
			}
			port(DocType.INVOICE, "INV_PORT_LOADING", "port of loading", in.lc().portOfLoading(), inv.portOfLoading(), WARNING,
					Fixer.EXPORTER, "UCP 600 art. 14(d)");
			port(DocType.INVOICE, "INV_PORT_DISCHARGE", "port of discharge", in.lc().portOfDischarge(), inv.portOfDischarge(),
					WARNING, Fixer.EXPORTER, "UCP 600 art. 14(d)");
			datedBeforeCredit(DocType.INVOICE, inv.invoiceDate());
		}
		invoiceArithmetic(inv, total);
		datedAfterPresentation(DocType.INVOICE, inv.invoiceDate());
	}

	private void invoiceQuantity(InvoiceReading inv) {
		GoodsTerms goods = lc.goods();
		Quantity invoiced = invoiceQuantityOf(inv);
		if (goods.quantity() == null || invoiced == null) {
			return;
		}
		BigDecimal credit = QtyUnit.convert(goods.quantity(), goods.quantityUnit(), invoiced.unit());
		if (credit == null) {
			add(WARNING, "INV_QTY_UNITS", "", "Quantity is in different units from the credit",
					"Field 45A gives %s; the invoice shows %s %s. Show the quantity in the credit's unit as well."
						.formatted(goods.quantityText(), qty(invoiced.value()), invoiced.unit().label()),
					goods.quantityText(), qty(invoiced.value()) + " " + invoiced.unit().label(), "UCP 600 art. 18(c)",
					Fixer.EXPORTER, "Show the quantity as in 45A (" + goods.quantityText() + ").", DocType.INVOICE);
			return;
		}
		BigDecimal tolerance = goods.quantityAbout() ? BigDecimal.TEN
				: goods.quantityUnit().countable() ? BigDecimal.ZERO : BigDecimal.valueOf(5);
		BigDecimal upper = credit.multiply(BigDecimal.ONE.add(tolerance.movePointLeft(2)));
		BigDecimal lower = credit.multiply(BigDecimal.ONE.subtract(tolerance.movePointLeft(2)));
		String rule = goods.quantityAbout() ? "UCP 600 art. 30(a)" : "UCP 600 art. 30(b)";
		String allowance = tolerance.signum() == 0 ? "no tolerance for goods counted in units"
				: qty(tolerance) + "% tolerance" + (goods.quantityAbout() ? " (\"about\")" : " for bulk goods");
		if (invoiced.value().compareTo(upper) > 0) {
			add(CRITICAL, "INV_QTY_OVER", "", "More goods invoiced than the credit allows",
					"The invoice shows %s %s against %s in field 45A (%s).".formatted(qty(invoiced.value()),
							invoiced.unit().label(), goods.quantityText(), allowance),
					"At most " + qty(upper) + " " + invoiced.unit().label(), qty(invoiced.value()) + " " + invoiced.unit().label(),
					rule, Fixer.EXPORTER, "Invoice at most " + qty(upper) + " " + invoiced.unit().label() + " under the credit.",
					DocType.INVOICE);
		}
		else if (invoiced.value().compareTo(lower) < 0) {
			if (Boolean.FALSE.equals(lc.partialAllowed())) {
				add(CRITICAL, "INV_QTY_SHORT", "", "Short shipment while partial shipments are not allowed",
						"The invoice shows %s %s against %s in field 45A, and field 43P does not allow partial shipments."
							.formatted(qty(invoiced.value()), invoiced.unit().label(), goods.quantityText()),
						"At least " + qty(lower) + " " + invoiced.unit().label(),
						qty(invoiced.value()) + " " + invoiced.unit().label(), "UCP 600 art. 30(b), 31",
						in.transport() == null ? Fixer.EXPORTER : Fixer.BUYER,
						in.transport() == null ? "Ship the full quantity of " + goods.quantityText() + "."
								: "Allow partial shipments (43P), or accept the shortfall.",
						DocType.INVOICE);
			}
			else {
				add(INFO, "INV_QTY_PARTIAL", "", "Partial shipment",
						"The invoice covers %s %s of the %s in the credit. Partial shipments are allowed."
							.formatted(qty(invoiced.value()), invoiced.unit().label(), goods.quantityText()),
						null, null, "UCP 600 art. 31", Fixer.EXPORTER, null, DocType.INVOICE);
			}
		}
	}

	private void invoiceUnitPrice(InvoiceReading inv) {
		GoodsTerms goods = lc.goods();
		if (goods.unitPrice() == null || inv.lines() == null) {
			return;
		}
		QtyUnit defaultUnit = QtyUnit.parse(inv.quantityUnit());
		BigDecimal tolerance = goods.priceAbout() ? BigDecimal.TEN : BigDecimal.ZERO;
		for (InvoiceReading.InvoiceLine line : inv.lines()) {
			BigDecimal price = TradeText.number(line.unitPrice());
			QtyUnit unit = QtyUnit.parse(line.unit()) != null ? QtyUnit.parse(line.unit()) : defaultUnit;
			if (price == null || unit == null) {
				continue;
			}
			BigDecimal perCreditUnit = QtyUnit.convert(BigDecimal.ONE, goods.priceUnit(), unit);
			if (perCreditUnit == null) {
				continue;
			}
			BigDecimal invoicePrice = price.multiply(perCreditUnit);
			String agreed = money(goods.priceCurrency(), goods.unitPrice()) + " per " + goods.priceUnit().label();
			String charged = money(goods.priceCurrency(), invoicePrice) + " per " + goods.priceUnit().label();
			if (invoicePrice.compareTo(goods.unitPrice().multiply(BigDecimal.ONE.add(tolerance.movePointLeft(2)))
				.add(new BigDecimal("0.005"))) > 0) {
				add(CRITICAL, "INV_UNIT_PRICE", "", "Unit price is higher than in the credit",
						"Field 45A gives %s; the invoice charges %s.".formatted(agreed, charged), agreed, charged,
						"UCP 600 art. 30", Fixer.EXPORTER, "Invoice at " + agreed + ".", DocType.INVOICE);
				return;
			}
			if (invoicePrice.compareTo(goods.unitPrice().multiply(BigDecimal.ONE.subtract(tolerance.movePointLeft(2)))
				.subtract(new BigDecimal("0.005"))) < 0) {
				add(WARNING, "INV_UNIT_PRICE_LOW", "", "Unit price differs from the credit",
						"Field 45A gives %s; the invoice shows %s. A different unit price conflicts with the credit even when it is lower."
							.formatted(agreed, charged),
						agreed, charged, "UCP 600 art. 14(d), 30(c)", Fixer.EXPORTER, "Show the unit price as " + agreed + ".",
						DocType.INVOICE);
				return;
			}
		}
	}

	private void invoiceDescription(InvoiceReading inv) {
		GoodsTerms goods = lc.goods();
		if (goods.keyWords().isEmpty()) {
			return;
		}
		String described = invoiceDescriptionText(inv);
		if (described.isBlank()) {
			return;
		}
		List<String> missing = goods.missingFrom(described);
		if (missing.isEmpty()) {
			return;
		}
		double covered = 1 - (double) missing.size() / goods.keyWords().size();
		add(covered < 0.6 ? CRITICAL : WARNING, "INV_DESCRIPTION", "", "Goods description does not match the credit",
				"The invoice must describe the goods as field 45A does. Missing from the invoice: " + String.join(", ", missing)
						+ ".",
				abbreviate(goods.description(), 400), abbreviate(described, 400), "UCP 600 art. 18(c)", Fixer.EXPORTER,
				"Describe the goods word for word as in 45A: " + abbreviate(goods.description().replace('\n', ' '), 220),
				DocType.INVOICE);
		String reference = goods.orderReference();
		if (reference != null && !Fuzzy.code(described + " " + nz(inv.orderReference())).contains(Fuzzy.code(reference))) {
			add(WARNING, "INV_ORDER_REFERENCE", "", "The order reference in the credit is not on the invoice",
					"Field 45A refers to " + reference + "; the invoice does not mention it.", reference,
					blank(inv.orderReference()) ? null : inv.orderReference(), "UCP 600 art. 18(c); ISBP", Fixer.EXPORTER,
					"Quote " + reference + " on the invoice as 45A does.", DocType.INVOICE);
		}
	}

	private void invoiceTerms(InvoiceReading inv) {
		GoodsTerms goods = lc.goods();
		if (goods.incoterm() == null) {
			return;
		}
		String wanted = goods.incoterm() + (goods.incotermPlace() == null ? "" : " " + goods.incotermPlace());
		String term = blank(inv.incoterm()) ? null : GoodsTerms.canonicalTerm(upper(inv.incoterm()).replaceAll("\\s+", " ").trim());
		if (term == null) {
			add(WARNING, "INV_TERMS_MISSING", "", "Trade term not shown on the invoice",
					"The credit is on %s terms; show the same term and place on the invoice.".formatted(wanted), wanted, null,
					"UCP 600 art. 18(c); ISBP", Fixer.EXPORTER, "Show " + wanted + " on the invoice.", DocType.INVOICE);
			return;
		}
		String found = term + (blank(inv.incotermPlace()) ? "" : " " + inv.incotermPlace());
		if (!term.startsWith(goods.incoterm())) {
			add(CRITICAL, "INV_TERMS", "", "Trade term differs from the credit",
					"The credit is on %s terms; the invoice says %s.".formatted(wanted, found), wanted, found,
					"UCP 600 art. 18(c), 14(d)", Fixer.EXPORTER, "Show " + wanted + " on the invoice.", DocType.INVOICE);
		}
		else if (goods.incotermPlace() != null && !blank(inv.incotermPlace())
				&& Ports.compare(goods.incotermPlace(), inv.incotermPlace()) == Ports.Match.DIFFERENT) {
			add(WARNING, "INV_TERMS_PLACE", "", "Named place of the trade term differs",
					"The credit says %s; the invoice says %s.".formatted(wanted, found), wanted, found, "UCP 600 art. 14(d)",
					Fixer.EXPORTER, "Show " + wanted + " on the invoice.", DocType.INVOICE);
		}
	}

	private void invoiceArithmetic(InvoiceReading inv, BigDecimal total) {
		if (inv.lines() == null) {
			return;
		}
		BigDecimal sum = BigDecimal.ZERO;
		boolean complete = !inv.lines().isEmpty();
		int lineNo = 0;
		for (InvoiceReading.InvoiceLine line : inv.lines()) {
			lineNo++;
			BigDecimal quantity = TradeText.number(line.quantity());
			BigDecimal price = TradeText.number(line.unitPrice());
			BigDecimal amount = TradeText.number(line.amount());
			if (amount == null) {
				complete = false;
				continue;
			}
			sum = sum.add(amount);
			if (quantity != null && price != null) {
				BigDecimal expected = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
				if (expected.subtract(amount).abs().compareTo(amount.abs().multiply(new BigDecimal("0.0005")).max(new BigDecimal("0.01"))) > 0) {
					add(WARNING, "INV_LINE_MATH", String.valueOf(lineNo), "Invoice line " + lineNo + " does not add up",
							"%s × %s = %s, but the line shows %s.".formatted(qty(quantity), price.toPlainString(),
									money(null, expected), money(null, amount)),
							money(null, expected), money(null, amount), "UCP 600 art. 14(d)", Fixer.EXPORTER,
							"Correct the amount on invoice line " + lineNo + ".", DocType.INVOICE);
				}
			}
		}
		BigDecimal stated = TradeText.number(inv.totalAmount());
		if (complete && stated != null && sum.subtract(stated).abs().compareTo(new BigDecimal("0.01")) > 0 && total != null) {
			add(WARNING, "INV_TOTAL_MATH", "", "Invoice lines do not add up to the total",
					"The lines add up to %s; the invoice total says %s.".formatted(money(null, sum), money(null, stated)),
					money(null, sum), money(null, stated), "UCP 600 art. 14(d)", Fixer.EXPORTER,
					"Correct the invoice total.", DocType.INVOICE);
		}
	}

	// ---------------------------------------------------------------- packing list

	private void packingList() {
		PackingListReading pl = in.packing();
		if (pl == null) {
			return;
		}
		InvoiceReading inv = in.invoice();
		if (inv != null) {
			Quantity invoiced = invoiceQuantityOf(inv);
			Quantity packed = quantity(pl.totalQuantity(), pl.quantityUnit());
			if (invoiced != null && packed != null) {
				BigDecimal comparable = QtyUnit.convert(packed.value(), packed.unit(), invoiced.unit());
				if (comparable != null && comparable.subtract(invoiced.value()).abs().compareTo(new BigDecimal("0.001")) > 0) {
					add(CRITICAL, "PL_QTY", "", "Packing list quantity differs from the invoice",
							"The invoice shows %s %s; the packing list %s %s.".formatted(qty(invoiced.value()),
									invoiced.unit().label(), qty(packed.value()), packed.unit().label()),
							qty(invoiced.value()) + " " + invoiced.unit().label(), qty(packed.value()) + " " + packed.unit().label(),
							"UCP 600 art. 14(d)", Fixer.EXPORTER, "Make the packing list quantity match the invoice.",
							DocType.PACKING_LIST, DocType.INVOICE);
				}
			}
			if (!blank(pl.invoiceNumber()) && !blank(inv.invoiceNumber())
					&& !TradeText.sameReference(pl.invoiceNumber(), inv.invoiceNumber())) {
				add(WARNING, "PL_INVOICE_REF", "", "Packing list refers to another invoice number",
						"The packing list quotes invoice %s; the invoice is %s.".formatted(pl.invoiceNumber(), inv.invoiceNumber()),
						inv.invoiceNumber(), pl.invoiceNumber(), "UCP 600 art. 14(d)", Fixer.EXPORTER,
						"Quote invoice " + inv.invoiceNumber() + " on the packing list.", DocType.PACKING_LIST, DocType.INVOICE);
			}
			conflict("PL_NET_VS_INVOICE", "net weight", DocType.INVOICE, inv.netWeightKg(), DocType.PACKING_LIST,
					pl.netWeightKg(), " kg", CRITICAL, Fixer.EXPORTER);
			conflict("PL_GROSS_VS_INVOICE", "gross weight", DocType.INVOICE, inv.grossWeightKg(), DocType.PACKING_LIST,
					pl.grossWeightKg(), " kg", CRITICAL, Fixer.EXPORTER);
			conflict("PL_PACKAGES_VS_INVOICE", "number of packages", DocType.INVOICE, inv.packages(), DocType.PACKING_LIST,
					pl.totalPackages(), "", CRITICAL, Fixer.EXPORTER);
		}
		BigDecimal net = TradeText.number(pl.netWeightKg());
		BigDecimal gross = TradeText.number(pl.grossWeightKg());
		if (net != null && gross != null && net.compareTo(gross) > 0) {
			add(WARNING, "PL_NET_OVER_GROSS", "", "Net weight is more than gross weight",
					"The packing list shows net %s kg and gross %s kg.".formatted(qty(net), qty(gross)), null, null,
					"UCP 600 art. 14(d)", Fixer.EXPORTER, "Correct the packing list weights.", DocType.PACKING_LIST);
		}
		packingTotals(pl, net, gross);
		if (lc != null) {
			lcNumber(DocType.PACKING_LIST, pl.lcNumber(), Fixer.EXPORTER);
			generalDescription(DocType.PACKING_LIST, "PL_DESCRIPTION", pl.goodsDescription() + " " + linesText(pl));
			datedBeforeCredit(DocType.PACKING_LIST, pl.documentDate());
		}
		datedAfterPresentation(DocType.PACKING_LIST, pl.documentDate());
	}

	private void packingTotals(PackingListReading pl, BigDecimal net, BigDecimal gross) {
		if (pl.lines() == null || pl.lines().isEmpty()) {
			return;
		}
		BigDecimal lineNet = sum(pl.lines().stream().map(PackingListReading.PackingLine::netWeightKg).toList());
		BigDecimal lineGross = sum(pl.lines().stream().map(PackingListReading.PackingLine::grossWeightKg).toList());
		BigDecimal linePackages = sum(pl.lines().stream().map(PackingListReading.PackingLine::packages).toList());
		BigDecimal packages = TradeText.number(pl.totalPackages());
		List<String> wrong = new ArrayList<>();
		if (lineNet != null && net != null && lineNet.subtract(net).abs().compareTo(WEIGHT_ROUNDING) > 0) {
			wrong.add("net weight (lines " + qty(lineNet) + " kg, total " + qty(net) + " kg)");
		}
		if (lineGross != null && gross != null && lineGross.subtract(gross).abs().compareTo(WEIGHT_ROUNDING) > 0) {
			wrong.add("gross weight (lines " + qty(lineGross) + " kg, total " + qty(gross) + " kg)");
		}
		if (linePackages != null && packages != null && linePackages.compareTo(packages) != 0) {
			wrong.add("packages (lines " + qty(linePackages) + ", total " + qty(packages) + ")");
		}
		if (!wrong.isEmpty()) {
			add(WARNING, "PL_TOTALS", "", "Packing list lines do not add up to its totals",
					"Check the " + String.join("; ", wrong) + ".", null, null, "UCP 600 art. 14(d)", Fixer.EXPORTER,
					"Correct the packing list totals.", DocType.PACKING_LIST);
		}
	}

	// ---------------------------------------------------------------- transport document

	private void transport() {
		TransportDocReading t = in.transport();
		if (t == null) {
			return;
		}
		boolean air = air(t);
		LocalDate shipped = shipmentDate(t);
		if (lc != null) {
			RequiredDoc required = lc.required(DocType.TRANSPORT).orElse(null);
			String typeLabel = air ? "an air waybill" : "a bill of lading";
			if (required != null && (required.kind() == RequiredDoc.Kind.AIR_TRANSPORT) != air) {
				add(CRITICAL, "BL_WRONG_TYPE", "", "Wrong kind of transport document",
						"Field 46A item %d asks for %s; this is %s.".formatted(required.number(), required.kind().label().toLowerCase(Locale.ROOT), typeLabel),
						required.kind().label(), typeLabel, "Field 46A", Fixer.FORWARDER, null, DocType.TRANSPORT);
			}
			if (!air && !"YES".equalsIgnoreCase(nz(t.shippedOnBoard()).trim()) && blank(t.onBoardDate())) {
				add(CRITICAL, "BL_NOT_ON_BOARD", "", "No dated \"shipped on board\" notation",
						"A bill of lading must show that the goods were shipped on board a named vessel, with the date. A \"received for shipment\" bill without a dated on-board notation is refused.",
						"Shipped on board, dated", nz(t.shippedOnBoard()), "UCP 600 art. 20(a)(ii)", Fixer.FORWARDER,
						"Add the dated \"shipped on board\" notation, signed for the carrier.", DocType.TRANSPORT);
			}
			if (shipped != null && lc.latestShipment() != null && shipped.isAfter(lc.latestShipment())) {
				long late = ChronoUnit.DAYS.between(lc.latestShipment(), shipped);
				add(CRITICAL, "BL_LATE_SHIPMENT", "", "Shipped after the latest shipment date",
						"The goods went on board on %s, %d %s after the latest shipment date %s (field 44C). This cannot be corrected on the documents: ask the buyer to amend 44C before you present, or to accept the discrepancy."
							.formatted(show(shipped), late, late == 1 ? "day" : "days", show(lc.latestShipment())),
						"On board by " + show(lc.latestShipment()), show(shipped), "Field 44C; UCP 600 art. 14(c)",
						Fixer.BUYER, "Extend the latest shipment date (44C) to at least " + show(shipped) + ".",
						DocType.TRANSPORT, DocType.LC);
			}
			if (shipped != null) {
				LocalDate deadline = shipped.plusDays(lc.presentationDays());
				if (lc.expiry() != null && lc.expiry().isBefore(deadline)) {
					deadline = lc.expiry();
				}
				if (presentation.isAfter(deadline)) {
					add(CRITICAL, "BL_PRESENTED_LATE", "", "Presented too late after shipment",
							"Documents must be presented within %d days of shipment (%s) and before expiry: by %s. Presenting on %s is late."
								.formatted(lc.presentationDays(), show(shipped), show(deadline), show(presentation)),
							"By " + show(deadline), show(presentation), "UCP 600 art. 14(c)", Fixer.BUYER,
							"Accept the late presentation.", DocType.TRANSPORT);
				}
				else if (ChronoUnit.DAYS.between(presentation, deadline) <= 3) {
					add(WARNING, "BL_PRESENT_SOON", "", "Present by " + show(deadline),
							"The presentation period ends on %s (%d days after shipment, or expiry if earlier)."
								.formatted(show(deadline), lc.presentationDays()),
							null, null, "UCP 600 art. 14(c)", Fixer.EXPORTER,
							"Present the documents by " + show(deadline) + ".", DocType.TRANSPORT);
				}
			}
			String rule = air ? "UCP 600 art. 23(a)(iii)" : "UCP 600 art. 20(a)(iii)";
			port(DocType.TRANSPORT, "BL_PORT_LOADING", air ? "airport of departure" : "port of loading",
					in.lc().portOfLoading(), t.portOfLoading(), CRITICAL, Fixer.FORWARDER, rule);
			port(DocType.TRANSPORT, "BL_PORT_DISCHARGE", air ? "airport of destination" : "port of discharge",
					in.lc().portOfDischarge(), t.portOfDischarge(), CRITICAL, Fixer.FORWARDER, rule);
			if (required != null) {
				consignee(t, required);
				notifyParty(t, required);
				originals(t, required);
			}
			freight(t, required);
			if (upper(t.documentType()).contains("CHARTER") || upper(t.clauses()).contains("CHARTER PARTY")) {
				add(CRITICAL, "BL_CHARTER_PARTY", "", "Charter party bill of lading",
						"The bill of lading is subject to a charter party. A bill of lading under article 20 must not be"
								+ (lc.conditions().charterPartyRefused() ? ", and field 47A refuses it." : "."),
						"Liner bill of lading", "Charter party", "UCP 600 art. 20(a)(vi)", Fixer.FORWARDER,
						"Issue a liner bill of lading that is not subject to a charter party.", DocType.TRANSPORT);
			}
			lcNumber(DocType.TRANSPORT, t.lcNumber(), Fixer.FORWARDER);
			generalDescription(DocType.TRANSPORT, "BL_DESCRIPTION", t.goodsDescription());
		}
		String clauses = upper(t.clauses()).trim();
		if (!clauses.isEmpty()) {
			Set<String> found = DEFECTS.stream().filter((word) -> (" " + clauses + " ").contains(" " + word + " "))
				.collect(Collectors.toCollection(LinkedHashSet::new));
			if (!found.isEmpty()) {
				add(CRITICAL, "BL_NOT_CLEAN", "", "The bill of lading is not clean",
						"It carries a clause about defective goods or packaging (" + String.join(", ", found).toLowerCase(Locale.ROOT)
								+ "): \"" + t.clauses().trim() + "\". Banks accept only clean transport documents.",
						"No clause about defects", t.clauses().trim(), "UCP 600 art. 27", Fixer.FORWARDER,
						"Get a clean bill of lading: fix or repack the goods, then ask the carrier to issue it without the clause.",
						DocType.TRANSPORT);
			}
			else {
				add(INFO, "BL_CLAUSE", "", "Clause on the transport document",
						"\"" + t.clauses().trim() + "\". Make sure it does not declare a defect in the goods or packaging.",
						null, null, "UCP 600 art. 27", Fixer.FORWARDER, null, DocType.TRANSPORT);
			}
		}
		if (!air && blank(t.carrier()) && blank(t.signedBy())) {
			add(WARNING, "BL_CARRIER", "", "Carrier's name and signature not found",
					"A bill of lading must name the carrier and be signed by the carrier, the master or a named agent for one of them.",
					null, null, "UCP 600 art. 20(a)(i)", Fixer.FORWARDER,
					"Show the carrier's name and sign as carrier, master or named agent.", DocType.TRANSPORT);
		}
		PackingListReading pl = in.packing();
		if (pl != null) {
			conflict("BL_PACKAGES_VS_PL", "number of packages", DocType.PACKING_LIST, pl.totalPackages(), DocType.TRANSPORT,
					t.packages(), "", CRITICAL, Fixer.FORWARDER);
			conflict("BL_GROSS_VS_PL", "gross weight", DocType.PACKING_LIST, pl.grossWeightKg(), DocType.TRANSPORT,
					t.grossWeightKg(), " kg", CRITICAL, Fixer.FORWARDER);
			textConflict("BL_MARKS_VS_PL", "shipping marks", DocType.PACKING_LIST, pl.marks(), DocType.TRANSPORT, t.marks());
			containers(pl, t);
		}
		else if (in.invoice() != null) {
			conflict("BL_GROSS_VS_INVOICE", "gross weight", DocType.INVOICE, in.invoice().grossWeightKg(), DocType.TRANSPORT,
					t.grossWeightKg(), " kg", CRITICAL, Fixer.FORWARDER);
		}
		if (in.invoice() != null && !blank(t.invoiceNumber()) && !blank(in.invoice().invoiceNumber())
				&& !TradeText.sameReference(t.invoiceNumber(), in.invoice().invoiceNumber())) {
			add(WARNING, "BL_INVOICE_REF", "", "Transport document quotes another invoice number",
					"It quotes invoice %s; the invoice is %s.".formatted(t.invoiceNumber(), in.invoice().invoiceNumber()),
					in.invoice().invoiceNumber(), t.invoiceNumber(), "UCP 600 art. 14(d)", Fixer.FORWARDER,
					"Quote invoice " + in.invoice().invoiceNumber() + ".", DocType.TRANSPORT, DocType.INVOICE);
		}
	}

	private void consignee(TransportDocReading t, RequiredDoc required) {
		String wanted = required.consignee();
		if (wanted == null) {
			return;
		}
		String consignee = upper(t.consignee());
		if (consignee.isBlank()) {
			add(WARNING, "BL_CONSIGNEE", "", "Consignee not read from the transport document",
					"Field 46A asks for it to be consigned " + wanted.toLowerCase(Locale.ROOT).replace("order", "to order") + ".",
					wanted, null, "Field 46A", Fixer.FORWARDER, null, DocType.TRANSPORT);
			return;
		}
		boolean ok;
		if (wanted.startsWith("ORDER")) {
			ok = consignee.contains("ORDER");
			if (ok && wanted.startsWith("ORDER OF ")) {
				String party = wanted.substring("ORDER OF ".length());
				ok = party.contains("SHIPPER") ? consignee.contains("SHIPPER") : Fuzzy.coverage(party, consignee) >= 0.6;
			}
		}
		else if (wanted.contains("APPLICANT")) {
			ok = TradeText.name(in.lc().applicant(), t.consignee()) != TradeText.NameMatch.DIFFERENT;
		}
		else {
			ok = Fuzzy.coverage(wanted, consignee) >= 0.6;
		}
		if (!ok) {
			String expected = wanted.startsWith("ORDER") ? "TO THE " + wanted : wanted;
			add(CRITICAL, "BL_CONSIGNEE", "", "Consignee is not as the credit requires",
					"Field 46A item %d asks for the bill of lading to be made out %s; it shows \"%s\"."
						.formatted(required.number(), expected, t.consignee().trim()),
					expected, t.consignee().trim(), "Field 46A", Fixer.FORWARDER, "Show the consignee as: " + expected + ".",
					DocType.TRANSPORT);
		}
	}

	private void notifyParty(TransportDocReading t, RequiredDoc required) {
		String wanted = required.notifyParty();
		if (wanted == null) {
			return;
		}
		if (blank(t.notifyParty())) {
			add(CRITICAL, "BL_NOTIFY", "", "Notify party missing",
					"Field 46A item %d asks to notify %s.".formatted(required.number(), wanted.toLowerCase(Locale.ROOT)),
					wanted.equals("APPLICANT") ? TradeText.firstLine(in.lc().applicant()) : wanted, null, "Field 46A",
					Fixer.FORWARDER, "Show the notify party as " + notifyName(wanted) + ".", DocType.TRANSPORT);
			return;
		}
		TradeText.NameMatch match = wanted.equals("APPLICANT") ? TradeText.name(in.lc().applicant(), t.notifyParty())
				: Fuzzy.coverage(wanted, t.notifyParty()) >= 0.6 ? TradeText.NameMatch.SAME : TradeText.NameMatch.DIFFERENT;
		if (match == TradeText.NameMatch.DIFFERENT) {
			add(CRITICAL, "BL_NOTIFY", "", "Notify party is not the one the credit names",
					"Field 46A item %d asks to notify %s; the bill of lading notifies \"%s\".".formatted(required.number(),
							notifyName(wanted), t.notifyParty().trim()),
					notifyName(wanted), t.notifyParty().trim(), "Field 46A", Fixer.FORWARDER,
					"Show the notify party as " + notifyName(wanted) + ".", DocType.TRANSPORT);
		}
		else if (match == TradeText.NameMatch.SIMILAR) {
			add(WARNING, "BL_NOTIFY_SPELLING", "", "Notify party's name is spelt differently",
					"Field 46A asks to notify %s; the bill of lading shows \"%s\".".formatted(notifyName(wanted),
							t.notifyParty().trim()),
					notifyName(wanted), t.notifyParty().trim(), "UCP 600 art. 14(d)", Fixer.FORWARDER,
					"Spell the notify party as " + notifyName(wanted) + ".", DocType.TRANSPORT);
		}
	}

	private String notifyName(String wanted) {
		if (!wanted.equals("APPLICANT")) {
			return wanted;
		}
		String name = TradeText.firstLine(in.lc().applicant());
		return name == null ? "the applicant" : "the applicant, " + name;
	}

	private void originals(TransportDocReading t, RequiredDoc required) {
		if (!required.fullSet()) {
			return;
		}
		BigDecimal issued = TradeText.number(t.originalsIssued());
		if (issued == null) {
			return;
		}
		if (required.originals() != null && issued.intValue() != required.originals()) {
			add(WARNING, "BL_ORIGINALS", "", "Number of originals differs from the credit",
					"Field 46A asks for a full set of %d/%d; the bill of lading says %d originals were issued."
						.formatted(required.originals(), required.originals(), issued.intValue()),
					required.originals() + "/" + required.originals(), issued.intValue() + " originals", "UCP 600 art. 20(a)(iv)",
					Fixer.FORWARDER, "Issue " + required.originals() + " originals.", DocType.TRANSPORT);
		}
		else if (issued.intValue() > 1) {
			add(INFO, "BL_FULL_SET", "", "Present the full set of " + issued.intValue() + " originals",
					"The credit asks for the full set; every original issued must go to the bank.", null, null,
					"UCP 600 art. 20(a)(iv)", Fixer.EXPORTER, null, DocType.TRANSPORT);
		}
	}

	private void freight(TransportDocReading t, RequiredDoc required) {
		String explicit = required == null ? null : required.freight();
		String wanted = explicit != null ? explicit : lc.impliedFreight();
		String freight = upper(t.freight());
		if ("PREPAID".equals(wanted)) {
			if (freight.contains("COLLECT") || freight.contains("TO PAY")) {
				add(CRITICAL, "BL_FREIGHT", "", "Freight marked collect, but the credit needs prepaid",
						(explicit != null ? "Field 46A asks for the bill of lading to be marked freight prepaid"
								: "The credit is on " + lc.goods().incoterm() + " terms, so freight is paid by you")
								+ "; it says \"" + t.freight().trim() + "\".",
						"FREIGHT PREPAID", t.freight().trim(), explicit != null ? "Field 46A" : "Credit terms (45A)",
						Fixer.FORWARDER, "Mark the bill of lading FREIGHT PREPAID.", DocType.TRANSPORT);
			}
			else if (!freight.contains("PREPAID") && explicit != null) {
				add(WARNING, "BL_FREIGHT_MARKING", "", "Freight prepaid marking not found",
						"Field 46A asks for the bill of lading to be marked freight prepaid.", "FREIGHT PREPAID",
						blank(t.freight()) ? null : t.freight().trim(), "Field 46A", Fixer.FORWARDER,
						"Mark the bill of lading FREIGHT PREPAID.", DocType.TRANSPORT);
			}
		}
		else if ("COLLECT".equals(explicit) && freight.contains("PREPAID")) {
			add(WARNING, "BL_FREIGHT", "", "Freight marked prepaid, but the credit says collect",
					"Field 46A asks for freight collect; the bill of lading says \"" + t.freight().trim() + "\".", "FREIGHT COLLECT",
					t.freight().trim(), "Field 46A", Fixer.FORWARDER, "Mark the bill of lading FREIGHT COLLECT.",
					DocType.TRANSPORT);
		}
	}

	private void containers(PackingListReading pl, TransportDocReading t) {
		Set<String> packed = containerSet(pl.containerNumbers());
		Set<String> shipped = containerSet(t.containerNumbers());
		if (packed.isEmpty() || shipped.isEmpty() || packed.equals(shipped)) {
			return;
		}
		add(WARNING, "BL_CONTAINERS_VS_PL", "", "Container numbers differ",
				"The packing list shows %s; the transport document %s.".formatted(String.join(", ", packed),
						String.join(", ", shipped)),
				String.join(", ", packed), String.join(", ", shipped), "UCP 600 art. 14(d)", Fixer.FORWARDER,
				"Make the container numbers match the packing list.", DocType.TRANSPORT, DocType.PACKING_LIST);
	}

	// ---------------------------------------------------------------- certificate of origin

	private void origin() {
		OriginReading o = in.origin();
		if (o == null) {
			return;
		}
		String wanted = lc != null && lc.goods().origin() != null ? lc.goods().origin()
				: in.invoice() != null && !blank(in.invoice().countryOfOrigin()) ? upper(in.invoice().countryOfOrigin()).trim()
						: null;
		if (wanted != null && !blank(o.countryOfOrigin()) && !upper(o.countryOfOrigin()).contains(wanted)
				&& !wanted.contains(upper(o.countryOfOrigin()).trim())) {
			add(CRITICAL, "COO_COUNTRY", "", "Country of origin differs",
					"The certificate gives %s; the %s says %s.".formatted(o.countryOfOrigin(),
							lc != null && lc.goods().origin() != null ? "credit" : "invoice", wanted),
					wanted, o.countryOfOrigin(), "UCP 600 art. 14(d)", Fixer.CHAMBER,
					"Certify the origin as " + wanted + ".", DocType.ORIGIN);
		}
		if (lc != null) {
			lc.required(DocType.ORIGIN).ifPresent((required) -> {
				String issuer = required.issuedBy();
				if (issuer != null && !blank(o.issuer())) {
					String body = issuer.replaceAll("\\b(LOCAL|ANY|RECOGNI[SZ]ED|COMPETENT|AUTHORITY|AN|THE|INDIAN|OFFICIAL)\\b", " ")
						.trim();
					if (!body.isBlank() && Fuzzy.coverage(body, o.issuer()) < 0.5) {
						add(CRITICAL, "COO_ISSUER", "", "Certificate issued by the wrong body",
								"Field 46A item %d asks for it to be issued by %s; this one is from %s.".formatted(
										required.number(), issuer.toLowerCase(Locale.ROOT), o.issuer().trim()),
								issuer, o.issuer().trim(), "Field 46A", Fixer.CHAMBER,
								"Get the certificate issued by " + issuer.toLowerCase(Locale.ROOT) + ".", DocType.ORIGIN);
					}
				}
			});
			lcNumber(DocType.ORIGIN, o.lcNumber(), Fixer.CHAMBER);
			generalDescription(DocType.ORIGIN, "COO_DESCRIPTION", o.goodsDescription());
			datedBeforeCredit(DocType.ORIGIN, o.issueDate());
		}
		if (in.invoice() != null && !blank(o.invoiceNumber()) && !blank(in.invoice().invoiceNumber())
				&& !TradeText.sameReference(o.invoiceNumber(), in.invoice().invoiceNumber())) {
			add(WARNING, "COO_INVOICE_REF", "", "Certificate of origin quotes another invoice",
					"It quotes invoice %s; the invoice is %s.".formatted(o.invoiceNumber(), in.invoice().invoiceNumber()),
					in.invoice().invoiceNumber(), o.invoiceNumber(), "UCP 600 art. 14(d)", Fixer.CHAMBER,
					"Quote invoice " + in.invoice().invoiceNumber() + ".", DocType.ORIGIN, DocType.INVOICE);
		}
		if (in.packing() != null) {
			conflict("COO_GROSS_VS_PL", "gross weight", DocType.PACKING_LIST, in.packing().grossWeightKg(), DocType.ORIGIN,
					o.grossWeightKg(), " kg", WARNING, Fixer.CHAMBER);
		}
		datedAfterPresentation(DocType.ORIGIN, o.issueDate());
	}

	// ---------------------------------------------------------------- insurance

	private void insurance() {
		InsuranceReading s = in.insurance();
		if (s == null) {
			return;
		}
		if (upper(s.documentType()).contains("COVER")) {
			add(CRITICAL, "INS_COVER_NOTE", "", "A cover note is not an insurance document",
					"Banks do not accept cover notes. Present the insurance policy or certificate.", "Policy or certificate",
					s.documentType(), "UCP 600 art. 28(c)", Fixer.INSURER, "Issue the insurance certificate or policy.",
					DocType.INSURANCE);
		}
		if (lc == null) {
			return;
		}
		RequiredDoc required = lc.required(DocType.INSURANCE).orElse(null);
		String currency = upper(s.currency()).trim();
		if (lc.currency() != null && !currency.isEmpty() && !currency.contains(lc.currency())) {
			add(CRITICAL, "INS_CURRENCY", "", "Insurance is not in the credit's currency",
					"The credit is in %s; the insurance is in %s.".formatted(lc.currency(), s.currency()), lc.currency(),
					s.currency(), "UCP 600 art. 28(f)(i)", Fixer.INSURER, "Insure in " + lc.currency() + ".",
					DocType.INSURANCE);
		}
		BigDecimal insured = TradeText.number(s.insuredAmount());
		BigDecimal percent = required != null && required.insurancePercent() != null ? required.insurancePercent()
				: BigDecimal.valueOf(110);
		BigDecimal value = in.invoice() != null ? invoiceTotal(in.invoice()) : null;
		if (value == null) {
			value = lc.amount();
		}
		if (insured != null && value != null) {
			BigDecimal minimum = value.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
			if (insured.add(new BigDecimal("0.01")).compareTo(minimum) < 0) {
				add(CRITICAL, "INS_AMOUNT", "", "Insured for too little",
						"Cover must be at least %s%% of the %s value: %s. The certificate insures %s.".formatted(qty(percent),
								lc.goods().incoterm() == null ? "CIF/CIP" : lc.goods().incoterm(),
								money(lc.currency(), minimum), money(lc.currency(), insured)),
						"At least " + money(lc.currency(), minimum), money(lc.currency(), insured), "UCP 600 art. 28(f)(ii)",
						Fixer.INSURER, "Insure for at least " + money(lc.currency(), minimum) + ".", DocType.INSURANCE);
			}
		}
		LocalDate shipped = in.transport() == null ? null : shipmentDate(in.transport());
		LocalDate issued = TradeText.date(s.issueDate());
		LocalDate effective = TradeText.date(s.effectiveDate());
		if (shipped != null && issued != null && issued.isAfter(shipped)
				&& (effective == null || effective.isAfter(shipped))) {
			add(CRITICAL, "INS_DATE", "", "Insurance dated after shipment",
					"The insurance is dated %s but the goods were shipped on %s, and it does not say cover started on or before shipment."
						.formatted(show(issued), show(shipped)),
					"On or before " + show(shipped), show(issued), "UCP 600 art. 28(e)", Fixer.INSURER,
					"Date the insurance on or before " + show(shipped)
							+ ", or state that cover is effective from that date.",
					DocType.INSURANCE);
		}
		if (required != null) {
			String risks = upper(s.risks());
			for (String risk : required.risks()) {
				boolean covered = switch (risk) {
					case "ICC (A)", "ICC (B)", "ICC (C)" -> {
						String letter = risk.substring(5, 6);
						yield risks.matches("(?s).*(CARGO\\s+CLAUSES?\\s*\\(?\\s*" + letter + "\\s*\\)?|ICC\\s*\\(?\\s*" + letter + "\\s*\\)?).*");
					}
					case "ALL RISKS" -> risks.contains("ALL RISKS") || risks.matches("(?s).*CARGO\\s+CLAUSES?\\s*\\(?\\s*A.*");
					case "WAR" -> risks.matches("(?s).*\\bWAR\\b.*");
					case "STRIKES" -> risks.matches("(?s).*\\b(STRIKES?|SRCC)\\b.*");
					case "THEFT" -> risks.matches("(?s).*\\b(THEFT|TPND|PILFERAGE)\\b.*");
					default -> true;
				};
				if (!covered) {
					add(CRITICAL, "INS_RISK", risk, "Risk not covered: " + risk,
							"Field 46A item %d asks for cover against %s; the insurance does not mention it.".formatted(
									required.number(), risk),
							risk, blank(s.risks()) ? null : abbreviate(s.risks(), 200), "UCP 600 art. 28(g)", Fixer.INSURER,
							"Add cover for " + risk + ".", DocType.INSURANCE);
				}
			}
		}
		else {
			String term = lc.goods().incoterm();
			if (term != null && !term.equals("CIF") && !term.equals("CIP")) {
				add(INFO, "INS_NOT_REQUIRED", "", "The credit does not ask for insurance",
						"On " + term + " terms the buyer insures. This document need not be presented.", null, null,
						"UCP 600 art. 14(g)", Fixer.EXPORTER, null, DocType.INSURANCE);
			}
		}
		port(DocType.INSURANCE, "INS_VOYAGE_FROM", "voyage start", in.lc().portOfLoading(), s.voyageFrom(), WARNING,
				Fixer.INSURER, "UCP 600 art. 28(f)(iii)");
		port(DocType.INSURANCE, "INS_VOYAGE_TO", "voyage end", in.lc().portOfDischarge(), s.voyageTo(), WARNING,
				Fixer.INSURER, "UCP 600 art. 28(f)(iii)");
		lcNumber(DocType.INSURANCE, s.lcNumber(), Fixer.INSURER);
	}

	/** Documents the credit does not call for are disregarded by the bank, but must not conflict. */
	private void notCalledFor() {
		if (lc == null || lc.requiredDocs().isEmpty()) {
			return;
		}
		List<DocType> present = new ArrayList<>();
		if (in.packing() != null) {
			present.add(DocType.PACKING_LIST);
		}
		if (in.origin() != null) {
			present.add(DocType.ORIGIN);
		}
		for (DocType type : present) {
			if (lc.required(type).isEmpty()) {
				add(INFO, "DOC_NOT_REQUIRED", type.name(), "The credit does not ask for a " + type.label().toLowerCase(Locale.ROOT),
						"The bank will not examine it, but if you present it, it must not contradict the other documents.",
						null, null, "UCP 600 art. 14(g)", Fixer.EXPORTER, null, type);
			}
		}
	}

	// ---------------------------------------------------------------- shared checks

	private void lcNumber(DocType type, String shown, Fixer fixer) {
		if (lc == null || lc.number() == null) {
			return;
		}
		String code = type.name().replace("PACKING_LIST", "PL").replace("TRANSPORT", "BL").replace("ORIGIN", "COO")
			.replace("INSURANCE", "INS").replace("INVOICE", "INV");
		if (!blank(shown)) {
			if (!TradeText.sameReference(shown, lc.number()) && !Fuzzy.code(shown).contains(Fuzzy.code(lc.number()))) {
				add(CRITICAL, code + "_LC_NUMBER", "", "Wrong LC number on the " + type.label().toLowerCase(Locale.ROOT),
						"It quotes %s; the credit is %s.".formatted(shown.trim(), lc.number()), lc.number(), shown.trim(),
						"UCP 600 art. 14(d)", fixer, "Quote LC number " + lc.number() + ".", type);
			}
		}
		else if (lc.lcNumberRequiredOn(type)) {
			add(CRITICAL, code + "_LC_NUMBER", "", "LC number missing from the " + type.label().toLowerCase(Locale.ROOT),
					"The credit asks for its number on this document" + (lc.conditions().lcNumberOnAllDocuments()
							? " (47A: all documents must show it)." : " (46A)."),
					lc.number(), null, lc.conditions().lcNumberOnAllDocuments() ? "Field 47A" : "Field 46A", fixer,
					"Show LC number " + lc.number() + " on the " + type.label().toLowerCase(Locale.ROOT) + ".", type);
		}
	}

	private void party(DocType type, String code, String role, String expectedParty, String actualParty, String rule,
			String fixPrefix) {
		String name = TradeText.firstLine(expectedParty);
		if (name == null) {
			return;
		}
		TradeText.NameMatch match = TradeText.name(expectedParty, actualParty);
		switch (match) {
			case DIFFERENT -> add(CRITICAL, code, "", capitalize(role) + " on the " + type.label().toLowerCase(Locale.ROOT)
					+ " is not the party the credit names",
					"The credit names %s; the %s shows \"%s\".".formatted(name, type.label().toLowerCase(Locale.ROOT),
							TradeText.firstLine(actualParty)),
					name, TradeText.firstLine(actualParty), rule, Fixer.EXPORTER, fixPrefix + name + ".", type);
			case SIMILAR -> add(WARNING, code + "_SPELLING", "", capitalize(role) + "'s name is spelt differently",
					"The credit names %s; the %s shows \"%s\". Names should be written exactly as in the credit."
						.formatted(name, type.label().toLowerCase(Locale.ROOT), TradeText.firstLine(actualParty)),
					name, TradeText.firstLine(actualParty), "UCP 600 art. 14(d)", Fixer.EXPORTER, fixPrefix + name + ".",
					type);
			case UNKNOWN -> add(INFO, code + "_UNREAD", "", capitalize(role) + " not read from the "
					+ type.label().toLowerCase(Locale.ROOT),
					"Check that it shows " + name + " exactly as in the credit.", name, null, rule, Fixer.EXPORTER, null, type);
			default -> {
			}
		}
	}

	private void port(DocType type, String code, String what, String required, String actual, Severity severity,
			Fixer fixer, String rule) {
		if (blank(required) || blank(actual)) {
			return;
		}
		if (Ports.compare(required, actual) == Ports.Match.DIFFERENT) {
			add(severity, code, "", capitalize(what) + " differs from the credit",
					"The credit says %s; the %s says %s.".formatted(required.trim(), type.label().toLowerCase(Locale.ROOT),
							actual.trim()),
					required.trim(), actual.trim(), rule, fixer, "Show the " + what + " as " + required.trim() + ".", type);
		}
	}

	private void conflict(String code, String what, DocType firstType, String first, DocType secondType, String second,
			String unit, Severity severity, Fixer fixer) {
		BigDecimal a = TradeText.number(first);
		BigDecimal b = TradeText.number(second);
		if (a == null || b == null) {
			return;
		}
		BigDecimal allowed = unit.isEmpty() ? BigDecimal.ZERO : WEIGHT_ROUNDING;
		if (a.subtract(b).abs().compareTo(allowed) > 0) {
			add(severity, code, "", capitalize(what) + " differs between documents",
					"The %s shows %s%s; the %s shows %s%s.".formatted(firstType.label().toLowerCase(Locale.ROOT), qty(a), unit,
							secondType.label().toLowerCase(Locale.ROOT), qty(b), unit),
					qty(a) + unit, qty(b) + unit, "UCP 600 art. 14(d)", fixer,
					"Make the " + what + " on the " + secondType.label().toLowerCase(Locale.ROOT) + " match the "
							+ firstType.label().toLowerCase(Locale.ROOT) + " (" + qty(a) + unit + "), or correct whichever is wrong.",
					secondType, firstType);
		}
	}

	private void textConflict(String code, String what, DocType firstType, String first, DocType secondType, String second) {
		String a = TradeText.normalize(first);
		String b = TradeText.normalize(second);
		if (a.isEmpty() || b.isEmpty() || a.contains(b) || b.contains(a)) {
			return;
		}
		add(WARNING, code, "", capitalize(what) + " differ between documents",
				"The %s shows \"%s\"; the %s shows \"%s\".".formatted(firstType.label().toLowerCase(Locale.ROOT), first.trim(),
						secondType.label().toLowerCase(Locale.ROOT), second.trim()),
				first.trim(), second.trim(), "UCP 600 art. 14(d)", Fixer.FORWARDER,
				"Make the " + what + " match the " + firstType.label().toLowerCase(Locale.ROOT) + ".", secondType, firstType);
	}

	private void generalDescription(DocType type, String code, String text) {
		if (lc.goods().keyWords().isEmpty() || TradeText.normalize(text).isEmpty()) {
			return;
		}
		Set<String> words = TradeText.words(text);
		if (lc.goods().keyWords().stream().noneMatch(words::contains)) {
			add(WARNING, code, "", "Goods described differently on the " + type.label().toLowerCase(Locale.ROOT),
					"Documents other than the invoice may describe the goods in general terms, but must not conflict with the credit. This one shares no words with field 45A.",
					abbreviate(lc.goods().description(), 200), abbreviate(text.trim(), 200), "UCP 600 art. 14(e)",
					type == DocType.TRANSPORT ? Fixer.FORWARDER : Fixer.EXPORTER,
					"Describe the goods consistently with 45A.", type);
		}
	}

	private void datedAfterPresentation(DocType type, String dateText) {
		LocalDate date = TradeText.date(dateText);
		if (date != null && date.isAfter(presentation)) {
			add(CRITICAL, type.name() + "_DATED_LATE", "", capitalize(type.label()) + " is dated after presentation",
					"It is dated %s; documents may not be dated later than the day they are presented (%s).".formatted(show(date),
							show(presentation)),
					"On or before " + show(presentation), show(date), "UCP 600 art. 14(i)", Fixer.EXPORTER,
					"Date it on or before " + show(presentation) + ".", type);
		}
	}

	private void datedBeforeCredit(DocType type, String dateText) {
		LocalDate date = TradeText.date(dateText);
		if (date != null && lc.issueDate() != null && date.isBefore(lc.issueDate())
				&& lc.conditions().noDocumentsBeforeIssue()) {
			add(CRITICAL, type.name() + "_DATED_EARLY", "", capitalize(type.label()) + " is dated before the credit",
					"Field 47A does not accept documents dated before the credit was issued (%s); this one is dated %s."
						.formatted(show(lc.issueDate()), show(date)),
					"On or after " + show(lc.issueDate()), show(date), "Field 47A", Fixer.EXPORTER,
					"Date it on or after " + show(lc.issueDate()) + ".", type);
		}
	}

	// ---------------------------------------------------------------- helpers

	static boolean air(TransportDocReading t) {
		return upper(t.documentType()).contains("AIR");
	}

	/**
	 * The date of shipment: the on-board notation's date, else the issue date of a bill of lading
	 * that says "shipped on board", or of an air waybill (UCP 600 art. 20(a)(ii), 23(a)(iii)).
	 */
	public static LocalDate shipmentDate(TransportDocReading t) {
		LocalDate onBoard = TradeText.date(t.onBoardDate());
		if (onBoard != null) {
			return onBoard;
		}
		if (air(t) || "YES".equalsIgnoreCase(nz(t.shippedOnBoard()).trim())) {
			return TradeText.date(t.issueDate());
		}
		return null;
	}

	static BigDecimal invoiceTotal(InvoiceReading inv) {
		BigDecimal total = TradeText.number(inv.totalAmount());
		if (total != null || inv.lines() == null) {
			return total;
		}
		return sum(inv.lines().stream().map(InvoiceReading.InvoiceLine::amount).toList());
	}

	private static Quantity invoiceQuantityOf(InvoiceReading inv) {
		Quantity stated = quantity(inv.totalQuantity(), inv.quantityUnit());
		if (stated != null || inv.lines() == null || inv.lines().isEmpty()) {
			return stated;
		}
		QtyUnit unit = null;
		BigDecimal total = BigDecimal.ZERO;
		for (InvoiceReading.InvoiceLine line : inv.lines()) {
			QtyUnit lineUnit = QtyUnit.parse(line.unit());
			BigDecimal value = TradeText.number(line.quantity());
			if (lineUnit == null || value == null) {
				return null;
			}
			if (unit == null) {
				unit = lineUnit;
			}
			BigDecimal converted = QtyUnit.convert(value, lineUnit, unit);
			if (converted == null) {
				return null;
			}
			total = total.add(converted);
		}
		return new Quantity(total, unit);
	}

	private static Quantity quantity(String value, String unit) {
		BigDecimal number = TradeText.number(value);
		QtyUnit parsed = QtyUnit.parse(unit);
		if (parsed == null && value != null) {
			parsed = QtyUnit.parse(value.replaceAll("[\\d,.\\s]+", " ").trim());
		}
		return number == null || parsed == null ? null : new Quantity(number, parsed);
	}

	private static String invoiceDescriptionText(InvoiceReading inv) {
		StringBuilder text = new StringBuilder(nz(inv.goodsDescription()));
		if (inv.lines() != null) {
			for (InvoiceReading.InvoiceLine line : inv.lines()) {
				text.append('\n').append(nz(line.description()));
			}
		}
		return text.toString().trim();
	}

	private static String linesText(PackingListReading pl) {
		return pl.lines() == null ? ""
				: pl.lines().stream().map(PackingListReading.PackingLine::description).filter(Objects::nonNull)
					.collect(Collectors.joining(" "));
	}

	private static Set<String> containerSet(String text) {
		if (blank(text)) {
			return Set.of();
		}
		return Arrays.stream(upper(text).split("[,;/\\s]+"))
			.map((c) -> c.replaceAll("[^A-Z0-9]", ""))
			.filter((c) -> c.matches("[A-Z]{4}\\d{6,7}"))
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static BigDecimal sum(List<String> values) {
		BigDecimal total = BigDecimal.ZERO;
		for (String value : values) {
			BigDecimal number = TradeText.number(value);
			if (number == null) {
				return null;
			}
			total = total.add(number);
		}
		return total;
	}

	private static String upper(String value) {
		return value == null ? "" : value.toUpperCase(Locale.ROOT);
	}

	private static String nz(String value) {
		return value == null ? "" : value;
	}

	private static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String abbreviate(String text, int max) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
	}

	private void add(Severity severity, String code, String key, String title, String detail, String expected,
			String found, String rule, Fixer fixer, String fix, DocType... documents) {
		out.add(new FindingDraft(severity, code, key, title, detail, abbreviate(expected, 1000), abbreviate(found, 1000),
				List.of(documents), rule, fixer, abbreviate(fix, 500)));
	}

	private record Quantity(BigDecimal value, QtyUnit unit) {
	}

}
