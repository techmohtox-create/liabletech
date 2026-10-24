package com.liabletech.exportdocs.check;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.exportdocs.document.DocData;
import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.document.ShipmentDocs;
import com.liabletech.exportdocs.reading.InsuranceReading;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.reading.OriginReading;
import com.liabletech.exportdocs.reading.PackingListReading;
import com.liabletech.exportdocs.reading.TransportDocReading;
import com.liabletech.exportdocs.shipment.Shipment;
import com.liabletech.exportdocs.shipment.Shipments;

/** Runs the check on a shipment's documents and records the outcome. */
@Service
public class CheckService {

	private final Shipments shipments;

	private final ShipmentDocs docs;

	private final DocData data;

	private final Findings findings;

	private final Clock clock;

	public CheckService(Shipments shipments, ShipmentDocs docs, DocData data, Findings findings, Clock clock) {
		this.shipments = shipments;
		this.docs = docs;
		this.data = data;
		this.findings = findings;
		this.clock = clock;
	}

	@Transactional
	public Result check(long accountId, long shipmentId) {
		Shipment shipment = shipments.get(accountId, shipmentId);
		Map<DocType, Record> readings = data.byType(docs.forShipment(shipmentId));
		LocalDate today = LocalDate.now(clock);
		CheckInput input = new CheckInput((LcReading) readings.get(DocType.LC), (InvoiceReading) readings.get(DocType.INVOICE),
				(PackingListReading) readings.get(DocType.PACKING_LIST),
				(TransportDocReading) readings.get(DocType.TRANSPORT), (OriginReading) readings.get(DocType.ORIGIN),
				(InsuranceReading) readings.get(DocType.INSURANCE), presentationDate(shipment, today), today);
		OffsetDateTime now = OffsetDateTime.now(clock);
		List<FindingDraft> drafts = DocChecker.check(input);
		findings.sync(accountId, shipmentId, drafts, now);
		Findings.Counts counts = findings.openCounts(shipmentId);

		LcTerms terms = input.lc() == null ? null : LcTerms.of(input.lc());
		List<String> missing = missingDocuments(terms, readings);
		updateTerms(shipment, input, terms);
		String status = shipment.status();
		if (shipment.active()) {
			if (input.lc() == null || input.invoice() == null) {
				status = Shipment.COLLECTING;
			}
			else if (counts.critical() + counts.warning() > 0) {
				status = Shipment.CHECKED;
			}
			else {
				status = missing.isEmpty() ? Shipment.READY : Shipment.COLLECTING;
			}
		}
		shipments.updateCheck(shipmentId, status, (int) counts.critical(), (int) counts.warning(), now);
		return new Result(counts, status, missing);
	}

	/**
	 * The day the documents were presented, once they have been; before that the planned day, or today
	 * once that day has passed or when none is set.
	 */
	static LocalDate presentationDate(Shipment shipment, LocalDate today) {
		if (shipment.submittedOn() != null) {
			return shipment.submittedOn();
		}
		LocalDate planned = shipment.plannedPresentation();
		return planned == null || planned.isBefore(today) ? today : planned;
	}

	/** Documents 46A asks for that the app reads and that have not been uploaded yet. */
	public static List<String> missingDocuments(LcTerms terms, Map<DocType, ?> present) {
		if (terms == null) {
			return List.of();
		}
		return terms.requiredDocs()
			.stream()
			.filter((doc) -> doc.readAs() != null && !present.containsKey(doc.readAs()))
			.map((doc) -> doc.kind().label())
			.distinct()
			.toList();
	}

	private void updateTerms(Shipment shipment, CheckInput input, LcTerms terms) {
		LocalDate shipped = input.transport() == null ? null : DocChecker.shipmentDate(input.transport());
		LocalDate deadline = null;
		if (terms != null && shipped != null) {
			deadline = shipped.plusDays(terms.presentationDays());
			if (terms.expiry() != null && terms.expiry().isBefore(deadline)) {
				deadline = terms.expiry();
			}
		}
		if (terms == null) {
			shipments.updateTerms(shipment.id(), shipment.lcNumber(), shipment.issuingBank(), buyerFromInvoice(input, shipment),
					shipment.currency(), shipment.lcAmount(), shipment.lcExpiry(), shipment.latestShipment(), shipped, null);
			return;
		}
		shipments.updateTerms(shipment.id(), terms.number(), TradeText.firstLine(input.lc().issuingBank()),
				TradeText.firstLine(input.lc().applicant()), terms.currency(), terms.amount(), terms.expiry(),
				terms.latestShipment(), shipped, deadline);
	}

	private static String buyerFromInvoice(CheckInput input, Shipment shipment) {
		if (input.invoice() != null && TradeText.firstLine(input.invoice().buyer()) != null) {
			return TradeText.firstLine(input.invoice().buyer());
		}
		return shipment.buyerName();
	}

	public record Result(Findings.Counts counts, String status, List<String> missing) {

		public boolean clean() {
			return counts.critical() == 0 && counts.warning() == 0;
		}

		public String verdict() {
			if (counts.critical() > 0) {
				return counts.critical() + (counts.critical() == 1 ? " discrepancy" : " discrepancies") + " to fix";
			}
			if (counts.warning() > 0) {
				return counts.warning() + (counts.warning() == 1 ? " point" : " points") + " to check";
			}
			return Objects.equals(status, Shipment.READY) ? "ready for the bank" : "no discrepancies so far";
		}

	}

}
