package com.liabletech.exportdocs;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.liabletech.exportdocs.check.CheckInput;
import com.liabletech.exportdocs.check.DocChecker;
import com.liabletech.exportdocs.check.FindingDraft;
import com.liabletech.exportdocs.check.Fixer;
import com.liabletech.exportdocs.check.GoodsTerms;
import com.liabletech.exportdocs.check.LcRequirements;
import com.liabletech.exportdocs.check.LcTerms;
import com.liabletech.exportdocs.check.Ports;
import com.liabletech.exportdocs.check.QtyUnit;
import com.liabletech.exportdocs.check.RequiredDoc;
import com.liabletech.exportdocs.check.Severity;
import com.liabletech.exportdocs.check.TradeText;
import com.liabletech.exportdocs.document.DocFields;
import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.reading.DocClassifier;
import com.liabletech.exportdocs.reading.InsuranceReading;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.reading.Mt700Parser;
import com.liabletech.exportdocs.reading.PdfText;
import com.liabletech.exportdocs.reading.TransportDocReading;
import com.liabletech.exportdocs.whatsapp.SampleDocs;
import com.liabletech.exportdocs.whatsapp.SampleSet;

class ExportRulesTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

	private static final SampleSet.Docs PEPPER = SampleSet.pepper(TODAY, "HB/DC/26/0917");

	private static final SampleSet.Docs TOWELS = SampleSet.towels(TODAY, "GCB/LC/2026/3381");

	// ---------------------------------------------------------------- reading a SWIFT credit

	@Test
	void bankPrintoutOfAnMt700IsReadFromItsText() {
		byte[] pdf = SampleDocs.lc(PEPPER.lc(), "HAFSDEHHXXX HAFENSTADT BANK AG, HAMBURG", "MLBCINBBXXX MALABAR BANK");

		LcReading lc = Mt700Parser.parse(PdfText.extract(pdf)).orElseThrow();

		assertThat(lc.lcNumber()).isEqualTo("HB/DC/26/0917");
		assertThat(lc.currency()).isEqualTo("USD");
		assertThat(new BigDecimal(lc.amount())).isEqualByComparingTo("85000");
		assertThat(lc.tolerance()).isEqualTo("05/05");
		assertThat(lc.issueDate()).isEqualTo(TODAY.minusDays(42).toString());
		assertThat(lc.expiryDate()).isEqualTo(TODAY.plusDays(14).toString());
		assertThat(lc.expiryPlace()).isEqualTo("INDIA");
		assertThat(lc.latestShipmentDate()).isEqualTo(TODAY.minusDays(6).toString());
		assertThat(lc.applicant()).startsWith("NORDSEE SPICE IMPORT GMBH");
		assertThat(lc.beneficiary()).startsWith("KAVERI OVERSEAS").doesNotContain("/0047");
		assertThat(lc.availableWith()).isEqualTo("ANY BANK IN INDIA BY NEGOTIATION");
		assertThat(lc.partialShipments()).isEqualTo("NOT ALLOWED");
		assertThat(lc.portOfLoading()).isEqualTo("COCHIN, INDIA");
		assertThat(lc.goodsDescription()).startsWith("INDIAN BLACK PEPPER ASTA GRADE 550 G/L");
		assertThat(LcRequirements.documents(lc.documentsRequired())).hasSize(6);
		assertThat(lc.presentationPeriod()).isEqualTo("21/FROM THE DATE OF SHIPMENT");
		assertThat(lc.issuingBank()).contains("HAFENSTADT");
	}

	@Test
	void rawSwiftMessageIsReadToo() {
		String fin = """
				{1:F01MLBCINBBAXXX0000000000}{2:O7001200261001HAFSDEHHAXXX00000000002610011200N}{4:
				:27:1/1
				:40A:IRREVOCABLE
				:20:LC4471
				:31C:260915
				:40E:UCP LATEST VERSION
				:31D:261231INDIA
				:50:BUYER GMBH
				BERLIN
				:59:/123
				SELLER EXPORTS
				MUMBAI
				:32B:EUR12500,50
				:41D:ANY BANK
				BY NEGOTIATION
				:43P:ALLOWED
				:44C:261130
				:45A:+500 CARTONS OF BASMATI RICE
				:46A:+COMMERCIAL INVOICE
				+PACKING LIST
				:71D:ALL CHARGES OUTSIDE GERMANY FOR BENEFICIARY
				-}""";

		LcReading lc = Mt700Parser.parse(fin).orElseThrow();

		assertThat(lc.lcNumber()).isEqualTo("LC4471");
		assertThat(lc.amount()).isEqualTo("12500.50");
		assertThat(lc.currency()).isEqualTo("EUR");
		assertThat(lc.expiryDate()).isEqualTo("2026-12-31");
		assertThat(lc.applicant()).isEqualTo("BUYER GMBH\nBERLIN");
		assertThat(lc.beneficiary()).isEqualTo("SELLER EXPORTS\nMUMBAI");
		assertThat(lc.charges()).isEqualTo("ALL CHARGES OUTSIDE GERMANY FOR BENEFICIARY");
		assertThat(Mt700Parser.parse("COMMERCIAL INVOICE\nInvoice No. 12")).isEmpty();
	}

	@Test
	void documentsAreRecognisedByTitleCaptionAndFileName() {
		assertThat(DocClassifier.byText(PdfText.extract(SampleDocs.invoice(PEPPER.invoice())))).contains(DocType.INVOICE);
		assertThat(DocClassifier.byText(PdfText.extract(SampleDocs.packingList(PEPPER.packing())))).contains(DocType.PACKING_LIST);
		assertThat(DocClassifier.byText(PdfText.extract(SampleDocs.billOfLading(PEPPER.transport())))).contains(DocType.TRANSPORT);
		assertThat(DocClassifier.byText(PdfText.extract(SampleDocs.origin(PEPPER.origin())))).contains(DocType.ORIGIN);
		assertThat(DocClassifier.byText(PdfText.extract(SampleDocs.insurance(PEPPER.insurance())))).contains(DocType.INSURANCE);
		assertThat(DocClassifier.byHint("BL draft pls check")).contains(DocType.TRANSPORT);
		assertThat(DocClassifier.byHint("pl")).contains(DocType.PACKING_LIST);
		assertThat(DocClassifier.byHint("LC copy")).contains(DocType.LC);
		assertThat(DocClassifier.byHint("hello")).isEmpty();
	}

	// ---------------------------------------------------------------- what the credit asks for

	@Test
	void goodsTermsComeFromField45a() {
		GoodsTerms goods = GoodsTerms.parse(PEPPER.lc().goodsDescription(), PEPPER.lc().additionalConditions());

		assertThat(goods.quantity()).isEqualByComparingTo("20");
		assertThat(goods.quantityUnit()).isEqualTo(QtyUnit.MT);
		assertThat(goods.unitPrice()).isEqualByComparingTo("4250");
		assertThat(goods.priceUnit()).isEqualTo(QtyUnit.MT);
		assertThat(goods.incoterm()).isEqualTo("CIF");
		assertThat(goods.incotermPlace()).isEqualTo("HAMBURG");
		assertThat(goods.incotermsVersion()).isEqualTo("2020");
		assertThat(goods.orderReference()).isEqualTo("KO/PI/26/118");
		assertThat(goods.keyWords()).containsExactly("INDIAN", "BLACK", "PEPPER", "ASTA", "GRADE", "550", "MACHINE", "CLEANED");
	}

	@Test
	void packingSizesAreNotTakenForTheQuantity() {
		GoodsTerms goods = GoodsTerms.parse("ABOUT 18 MT TURMERIC FINGERS PACKED IN 25 KG PP BAGS\nC&F JEBEL ALI", "");

		assertThat(goods.quantity()).isEqualByComparingTo("18");
		assertThat(goods.quantityAbout()).isTrue();
		assertThat(goods.incoterm()).isEqualTo("CFR");
		assertThat(goods.incotermPlace()).isEqualTo("JEBEL ALI");
	}

	@Test
	void documentsRequiredAreReadItemByItem() {
		List<RequiredDoc> docs = LcRequirements.documents(PEPPER.lc().documentsRequired());

		assertThat(docs).extracting(RequiredDoc::kind)
			.containsExactly(RequiredDoc.Kind.INVOICE, RequiredDoc.Kind.PACKING_LIST, RequiredDoc.Kind.TRANSPORT,
					RequiredDoc.Kind.INSURANCE, RequiredDoc.Kind.ORIGIN, RequiredDoc.Kind.BENEFICIARY_CERTIFICATE);
		RequiredDoc invoice = docs.get(0);
		assertThat(invoice.signed()).isTrue();
		assertThat(invoice.originals()).isEqualTo(3);
		assertThat(invoice.showLcNumber()).isTrue();
		assertThat(docs.get(1).copies()).isEqualTo(3);
		RequiredDoc bl = docs.get(2);
		assertThat(bl.fullSet()).isTrue();
		assertThat(bl.originals()).isEqualTo(3);
		assertThat(bl.consignee()).isEqualTo("ORDER OF HAFENSTADT BANK AG");
		assertThat(bl.notifyParty()).isEqualTo("APPLICANT");
		assertThat(bl.freight()).isEqualTo("PREPAID");
		assertThat(bl.clean()).isTrue();
		RequiredDoc insurance = docs.get(3);
		assertThat(insurance.insurancePercent()).isEqualByComparingTo("110");
		assertThat(insurance.risks()).containsExactly("ICC (A)", "WAR", "STRIKES");
		assertThat(insurance.originals()).isEqualTo(2);
		assertThat(docs.get(4).issuedBy()).isEqualTo("CHAMBER OF COMMERCE");
	}

	@Test
	void additionalConditionsAndSoftClausesAreFound() {
		LcRequirements.LcConditions pepper = LcRequirements.conditions(PEPPER.lc().additionalConditions(),
				PEPPER.lc().documentsRequired());
		assertThat(pepper.lcNumberOnAllDocuments()).isTrue();
		assertThat(pepper.noDocumentsBeforeIssue()).isTrue();
		assertThat(pepper.discrepancyFee()).isEqualTo("USD 80");
		assertThat(pepper.softClauses()).isEmpty();

		LcReading brass = SampleSet.brass(TODAY, "EMB/IMP/26/22015");
		LcRequirements.LcConditions conditions = LcRequirements.conditions(brass.additionalConditions(),
				brass.documentsRequired());
		assertThat(conditions.softClauses()).singleElement().asString().contains("SIGNED BY APPLICANT'S REPRESENTATIVE");
		assertThat(LcTerms.of(brass).presentationDays()).isEqualTo(15);
	}

	// ---------------------------------------------------------------- the check

	@Test
	void pepperDocumentsHaveFiveDiscrepanciesEachWithItsFixer() {
		List<FindingDraft> findings = DocChecker.check(input(PEPPER, TODAY));

		assertThat(findings.stream().filter((f) -> f.severity() == Severity.CRITICAL))
			.extracting(FindingDraft::code, FindingDraft::fixer)
			.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("INV_DESCRIPTION", Fixer.EXPORTER),
					org.assertj.core.groups.Tuple.tuple("BL_LATE_SHIPMENT", Fixer.BUYER),
					org.assertj.core.groups.Tuple.tuple("BL_FREIGHT", Fixer.FORWARDER),
					org.assertj.core.groups.Tuple.tuple("BL_GROSS_VS_PL", Fixer.FORWARDER),
					org.assertj.core.groups.Tuple.tuple("INS_RISK", Fixer.INSURER));
		assertThat(findings.stream().filter((f) -> f.severity() == Severity.WARNING)).isEmpty();
		FindingDraft description = find(findings, "INV_DESCRIPTION");
		assertThat(description.detail()).contains("INDIAN, ASTA, GRADE, MACHINE, CLEANED");
		assertThat(find(findings, "BL_LATE_SHIPMENT").detail()).contains("2 days after the latest shipment date");
		assertThat(find(findings, "INS_RISK").key()).isEqualTo("STRIKES");
		assertThat(findings.getFirst().severity()).as("most serious first").isEqualTo(Severity.CRITICAL);
	}

	@Test
	void towelDocumentsComply() {
		List<FindingDraft> findings = DocChecker.check(input(TOWELS, TODAY));

		assertThat(findings).allSatisfy((f) -> assertThat(f.severity()).isEqualTo(Severity.INFO));
	}

	@Test
	void creditWithTermsWorthAmendingIsFlaggedBeforeShipping() {
		LcReading brass = SampleSet.brass(TODAY, "EMB/IMP/26/22015");

		List<FindingDraft> findings = DocChecker.check(new CheckInput(brass, null, null, null, null, null, TODAY, TODAY));

		assertThat(findings).extracting(FindingDraft::code)
			.containsExactlyInAnyOrder("LC_EXPIRY_ABROAD", "LC_SHORT_PRESENTATION", "LC_SOFT_CLAUSE");
		assertThat(findings).allSatisfy((f) -> assertThat(f.fixer()).isEqualTo(Fixer.BUYER));
	}

	@Test
	void drawingAboveTheToleranceIsADiscrepancy() {
		InvoiceReading over = invoice(PEPPER.invoice(), "21", "89300.00", "4252.38");

		List<FindingDraft> findings = DocChecker.check(new CheckInput(PEPPER.lc(), over, null, null, null, null, TODAY, TODAY));

		assertThat(codes(findings)).contains("INV_AMOUNT_OVER", "INV_UNIT_PRICE");
		assertThat(find(findings, "INV_AMOUNT_OVER").expected()).isEqualTo("At most USD 89,250.00");
	}

	@Test
	void bulkGoodsGetFivePercentButPiecesNone() {
		InvoiceReading bulk = invoice(PEPPER.invoice(), "20.9", "88825.00", "4250.00");
		assertThat(codes(DocChecker.check(new CheckInput(PEPPER.lc(), bulk, null, null, null, null, TODAY, TODAY))))
			.doesNotContain("INV_QTY_OVER");

		InvoiceReading towels = TOWELS.invoice();
		InvoiceReading oneMore = new InvoiceReading(towels.invoiceNumber(), towels.invoiceDate(), towels.seller(),
				towels.buyer(), towels.consignee(), towels.lcNumber(), towels.orderReference(), towels.currency(),
				towels.incoterm(), towels.incotermPlace(), towels.portOfLoading(), towels.portOfDischarge(),
				towels.finalDestination(), towels.countryOfOrigin(), towels.vesselOrFlight(), towels.goodsDescription(),
				List.of(new InvoiceReading.InvoiceLine(towels.lines().getFirst().description(), "63026010", "12001", "PCS",
						"3.55", "42603.55")),
				"12001", "PCS", "42603.55", towels.packages(), towels.netWeightKg(), towels.grossWeightKg(), towels.marks(),
				"YES", 0.9);
		assertThat(codes(DocChecker.check(new CheckInput(TOWELS.lc(), oneMore, null, null, null, null, TODAY, TODAY))))
			.contains("INV_QTY_OVER");
	}

	@Test
	void presentingAfterTheWindowIsADiscrepancy() {
		LocalDate late = TODAY.plusDays(15);

		List<FindingDraft> findings = DocChecker.check(input(PEPPER, late));

		assertThat(codes(findings)).contains("BL_PRESENTED_LATE", "LC_EXPIRED");
	}

	@Test
	void underInsuredAndLateInsuranceAreCaught() {
		InsuranceReading s = PEPPER.insurance();
		InsuranceReading weak = new InsuranceReading("CERTIFICATE", s.documentNumber(), TODAY.minusDays(1).toString(), "",
				s.insured(), "USD", "85000.00", "INSTITUTE CARGO CLAUSES (A), WAR AND STRIKES CLAUSES", s.voyageFrom(),
				s.voyageTo(), s.conveyance(), s.claimsPayableAt(), s.invoiceNumber(), s.lcNumber(), s.endorsement(), 0.9);

		List<FindingDraft> findings = DocChecker.check(new CheckInput(PEPPER.lc(), PEPPER.invoice(), PEPPER.packing(),
				PEPPER.transport(), PEPPER.origin(), weak, TODAY, TODAY));

		assertThat(codes(findings)).contains("INS_AMOUNT", "INS_DATE").doesNotContain("INS_RISK");
		assertThat(find(findings, "INS_AMOUNT").expected()).isEqualTo("At least USD 93,500.00");
	}

	@Test
	void transportDocumentWithoutOnBoardNotationOrWithDefectsIsRefused() {
		TransportDocReading t = PEPPER.transport();
		TransportDocReading received = new TransportDocReading(t.documentType(), t.documentNumber(), t.issueDate(), "",
				"NO", t.carrier(), t.signedBy(), t.shipper(), t.consignee(), t.notifyParty(), t.vessel(), t.voyage(),
				t.placeOfReceipt(), t.portOfLoading(), "ROTTERDAM, NETHERLANDS", t.placeOfDelivery(), t.goodsDescription(),
				t.packages(), "20450", t.measurementCbm(), t.marks(), t.containerNumbers(), "PREPAID", "3",
				"2 BAGS TORN AND RESTITCHED", t.lcNumber(), t.invoiceNumber(), 0.9);

		List<FindingDraft> findings = DocChecker.check(new CheckInput(PEPPER.lc(), PEPPER.invoice(), PEPPER.packing(),
				received, null, null, TODAY, TODAY));

		assertThat(codes(findings)).contains("BL_NOT_ON_BOARD", "BL_PORT_DISCHARGE", "BL_NOT_CLEAN")
			.doesNotContain("BL_FREIGHT", "BL_GROSS_VS_PL");
	}

	// ---------------------------------------------------------------- helpers people rely on

	@Test
	void portsMatchAcrossTheirNames() {
		assertThat(Ports.compare("NHAVA SHEVA, INDIA", "JNPT")).isEqualTo(Ports.Match.SAME);
		assertThat(Ports.compare("COCHIN, INDIA", "Kochi")).isEqualTo(Ports.Match.SAME);
		assertThat(Ports.compare("ANY INDIAN PORT", "MUNDRA PORT")).isEqualTo(Ports.Match.SAME);
		assertThat(Ports.compare("ANY SEAPORT IN INDIA", "JEBEL ALI")).isEqualTo(Ports.Match.DIFFERENT);
		assertThat(Ports.compare("HAMBURG, GERMANY", "ROTTERDAM")).isEqualTo(Ports.Match.DIFFERENT);
		assertThat(Ports.compare("PORT KLANG, MALAYSIA", "PORT KLANG")).isEqualTo(Ports.Match.SAME);
	}

	@Test
	void unitsAndDatesAreReadAsWritten() {
		assertThat(QtyUnit.parse("M/T")).isEqualTo(QtyUnit.MT);
		assertThat(QtyUnit.parse("Kgs.")).isEqualTo(QtyUnit.KG);
		assertThat(QtyUnit.parse("Nos")).isEqualTo(QtyUnit.PCS);
		assertThat(QtyUnit.parse("CTNS")).isEqualTo(QtyUnit.CARTONS);
		assertThat(QtyUnit.convert(new BigDecimal("20"), QtyUnit.MT, QtyUnit.KG)).isEqualByComparingTo("20000");
		assertThat(TradeText.date("261015")).isEqualTo(LocalDate.of(2026, 10, 15));
		assertThat(TradeText.date("15.10.2026")).isEqualTo(LocalDate.of(2026, 10, 15));
		assertThat(TradeText.date("2026/10/15")).isEqualTo(LocalDate.of(2026, 10, 15));
		assertThat(TradeText.date("10/25/2026")).isEqualTo(LocalDate.of(2026, 10, 25));
		assertThat(TradeText.money("USD", new BigDecimal("85000"))).isEqualTo("USD 85,000.00");
	}

	@Test
	void reviewFormRoundTripsEveryField() {
		InvoiceReading invoice = PEPPER.invoice();
		Map<String, String> params = new java.util.HashMap<>();
		DocFields.fields(invoice).forEach((f) -> params.put("f." + f.name(), f.value()));
		params.put("f.goodsDescription", "INDIAN BLACK PEPPER ASTA GRADE 550 G/L, MACHINE CLEANED");
		params.put("l.0.description", "BLACK PEPPER");
		params.put("l.0.quantity", "20");
		params.put("l.1.description", "");
		params.put("l.2.description", "FREIGHT");
		params.put("l.2.amount", "0");

		InvoiceReading updated = DocFields.apply(InvoiceReading.class, invoice, params);

		assertThat(updated.invoiceNumber()).isEqualTo(invoice.invoiceNumber());
		assertThat(updated.goodsDescription()).startsWith("INDIAN BLACK PEPPER");
		assertThat(updated.lines()).extracting(InvoiceReading.InvoiceLine::description).containsExactly("BLACK PEPPER", "FREIGHT");
		assertThat(updated.confidence()).isEqualTo(invoice.confidence());
		assertThat(DocFields.table(invoice, 2).rows()).hasSize(3);
	}

	private static CheckInput input(SampleSet.Docs docs, LocalDate presentation) {
		return new CheckInput(docs.lc(), docs.invoice(), docs.packing(), docs.transport(), docs.origin(), docs.insurance(),
				presentation, TODAY);
	}

	private static InvoiceReading invoice(InvoiceReading i, String qty, String total, String price) {
		return new InvoiceReading(i.invoiceNumber(), i.invoiceDate(), i.seller(), i.buyer(), i.consignee(), i.lcNumber(),
				i.orderReference(), i.currency(), i.incoterm(), i.incotermPlace(), i.portOfLoading(), i.portOfDischarge(),
				i.finalDestination(), i.countryOfOrigin(), i.vesselOrFlight(),
				"INDIAN BLACK PEPPER ASTA GRADE 550 G/L, MACHINE CLEANED",
				List.of(new InvoiceReading.InvoiceLine("INDIAN BLACK PEPPER ASTA GRADE 550 G/L, MACHINE CLEANED", "09041120",
						qty, "MT", price, total)),
				qty, "MT", total, i.packages(), i.netWeightKg(), i.grossWeightKg(), i.marks(), i.signed(), i.confidence());
	}

	private static List<String> codes(List<FindingDraft> findings) {
		return findings.stream().map(FindingDraft::code).toList();
	}

	private static FindingDraft find(List<FindingDraft> findings, String code) {
		return findings.stream().filter((f) -> f.code().equals(code)).findFirst().orElseThrow(() -> new AssertionError(
				code + " not in " + codes(findings)));
	}

}
