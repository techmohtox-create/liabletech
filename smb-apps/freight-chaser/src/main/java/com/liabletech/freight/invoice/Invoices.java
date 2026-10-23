package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Invoices {

	private final JdbcClient jdbc;

	public Invoices(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Invoice get(long accountId, long id) {
		return jdbc.sql("SELECT * FROM invoice WHERE account_id = ? AND id = ?")
			.params(accountId, id)
			.query(Invoice.class)
			.optional()
			.orElseThrow(() -> new NotFoundException("Invoice " + id));
	}

	long create(long accountId, String number, LocalDate invoiceDate, LocalDate dueDate, long partyId, GstMode mode,
			BigDecimal rate, GstMode.Totals totals, String notes) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO invoice (account_id, number, invoice_date, due_date, party_id, subtotal, gst_mode, gst_rate,
				    cgst, sgst, igst, round_off, advance_received, total, status, notes)
				VALUES (:accountId, :number, :invoiceDate, :dueDate, :partyId, :subtotal, :mode, :rate, :cgst, :sgst,
				    :igst, :roundOff, :advance, :total, 'DRAFT', :notes)
				""")
			.param("accountId", accountId)
			.param("number", number)
			.param("invoiceDate", invoiceDate)
			.param("dueDate", dueDate)
			.param("partyId", partyId)
			.param("subtotal", totals.subtotal())
			.param("mode", mode.name())
			.param("rate", mode == GstMode.FORWARD ? rate : BigDecimal.ZERO)
			.param("cgst", totals.cgst())
			.param("sgst", totals.sgst())
			.param("igst", totals.igst())
			.param("roundOff", totals.roundOff())
			.param("advance", totals.advance())
			.param("total", totals.total())
			.param("notes", notes)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	void addLine(long invoiceId, Long tripId, String description, BigDecimal amount, int position) {
		jdbc.sql("INSERT INTO invoice_line (invoice_id, trip_id, description, amount, position) VALUES (?, ?, ?, ?, ?)")
			.params(invoiceId, tripId, description, amount, position)
			.update();
	}

	public List<InvoiceLine> lines(long invoiceId) {
		return jdbc.sql("SELECT * FROM invoice_line WHERE invoice_id = ? ORDER BY position")
			.param(invoiceId)
			.query(InvoiceLine.class)
			.list();
	}

	void setPdf(long id, long fileId) {
		jdbc.sql("UPDATE invoice SET pdf_file_id = ? WHERE id = ?").params(fileId, id).update();
	}

	void markSent(long id, OffsetDateTime at, LocalDate nextFollowUp) {
		jdbc.sql("""
				UPDATE invoice SET status = CASE WHEN status = 'DRAFT' THEN 'SENT' ELSE status END,
				    sent_at = COALESCE(sent_at, ?), next_follow_up_on = ?
				WHERE id = ?
				""").params(at, nextFollowUp, id).update();
	}

	void recordPaymentTotals(long id, BigDecimal received, BigDecimal tds, String status, LocalDate paidOn) {
		jdbc.sql("UPDATE invoice SET received = ?, tds = ?, status = ?, paid_on = ? WHERE id = ?")
			.params(received, tds, status, paidOn, id)
			.update();
	}

	void setStatus(long accountId, long id, String status) {
		jdbc.sql("UPDATE invoice SET status = ? WHERE account_id = ? AND id = ?").params(status, accountId, id).update();
	}

	void setChasePaused(long accountId, long id, boolean paused) {
		jdbc.sql("UPDATE invoice SET chase_paused = ? WHERE account_id = ? AND id = ?")
			.params(paused, accountId, id)
			.update();
	}

	void setPromise(long accountId, long id, LocalDate promisedOn, LocalDate nextFollowUp) {
		jdbc.sql("UPDATE invoice SET promised_on = ?, next_follow_up_on = ? WHERE account_id = ? AND id = ?")
			.params(promisedOn, nextFollowUp, accountId, id)
			.update();
	}

	public void afterFollowUp(long id, int count, LocalDate next) {
		jdbc.sql("UPDATE invoice SET follow_up_count = ?, next_follow_up_on = ? WHERE id = ?")
			.params(count, next, id)
			.update();
	}

	void insertPayment(long accountId, long invoiceId, PaymentForm form) {
		jdbc.sql("""
				INSERT INTO payment (account_id, invoice_id, received_on, amount, tds, mode, reference)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""")
			.params(accountId, invoiceId, form.getReceivedOn(), form.getAmount(),
					form.getTds() == null ? BigDecimal.ZERO : form.getTds(), form.getMode(),
					form.getReference() == null || form.getReference().isBlank() ? null : form.getReference().trim())
			.update();
	}

	public List<Payment> payments(long invoiceId) {
		return jdbc.sql("SELECT * FROM payment WHERE invoice_id = ? ORDER BY received_on, id")
			.param(invoiceId)
			.query(Payment.class)
			.list();
	}

	public List<InvoiceRow> list(long accountId, String filter, LocalDate today, Long partyId) {
		return jdbc.sql("""
				SELECT i.*, p.name AS party_name FROM invoice i JOIN party p ON p.id = i.party_id
				WHERE i.account_id = :accountId
				    AND (CAST(:partyId AS BIGINT) IS NULL OR i.party_id = :partyId)
				    AND (:filter = 'all'
				        OR (:filter = 'open' AND i.status IN ('SENT', 'PART_PAID', 'DISPUTED'))
				        OR (:filter = 'overdue' AND i.status IN ('SENT', 'PART_PAID', 'DISPUTED') AND i.due_date < :today)
				        OR (:filter = 'draft' AND i.status = 'DRAFT')
				        OR (:filter = 'paid' AND i.status = 'PAID'))
				ORDER BY i.invoice_date DESC, i.id DESC
				""")
			.param("accountId", accountId)
			.param("partyId", partyId)
			.param("filter", filter)
			.param("today", today)
			.query(InvoiceRow.class)
			.list();
	}

	/** Open invoices whose next automatic follow-up is due. */
	public List<Invoice> dueForFollowUp(long accountId, LocalDate today) {
		return jdbc.sql("""
				SELECT * FROM invoice WHERE account_id = ? AND status IN ('SENT', 'PART_PAID') AND chase_paused = FALSE
				    AND next_follow_up_on IS NOT NULL AND next_follow_up_on <= ?
				ORDER BY due_date
				""").params(accountId, today).query(Invoice.class).list();
	}

	public List<Long> accountsWithOpenInvoices() {
		return jdbc.sql("SELECT DISTINCT account_id FROM invoice WHERE status IN ('SENT', 'PART_PAID')")
			.query(Long.class)
			.list();
	}

	public List<Invoice> openForParty(long accountId, long partyId) {
		return jdbc.sql("""
				SELECT * FROM invoice WHERE account_id = ? AND party_id = ? AND status IN ('SENT', 'PART_PAID', 'DISPUTED')
				ORDER BY due_date
				""").params(accountId, partyId).query(Invoice.class).list();
	}

	public List<Invoice> open(long accountId) {
		return jdbc.sql("SELECT * FROM invoice WHERE account_id = ? AND status IN ('SENT', 'PART_PAID', 'DISPUTED')")
			.param(accountId)
			.query(Invoice.class)
			.list();
	}

	public BigDecimal collectedSince(long accountId, LocalDate since) {
		return jdbc.sql("SELECT COALESCE(SUM(amount + tds), 0) FROM payment WHERE account_id = ? AND received_on >= ?")
			.params(accountId, since)
			.query(BigDecimal.class)
			.single();
	}

	public record InvoiceRow(Long id, String number, LocalDate invoiceDate, LocalDate dueDate, Long partyId,
			String partyName, BigDecimal total, BigDecimal received, BigDecimal tds, String status, boolean chasePaused,
			LocalDate promisedOn, LocalDate nextFollowUpOn, int followUpCount) {

		public BigDecimal balance() {
			return total.subtract(received).subtract(tds);
		}

		public boolean open() {
			return Invoice.OPEN.contains(status);
		}

	}

}
