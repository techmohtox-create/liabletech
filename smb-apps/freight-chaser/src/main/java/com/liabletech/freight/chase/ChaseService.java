package com.liabletech.freight.chase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.liabletech.freight.invoice.Invoice;
import com.liabletech.freight.invoice.InvoiceService;
import com.liabletech.freight.invoice.Invoices;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.party.Party;
import com.liabletech.freight.pod.Pods;
import com.liabletech.freight.trip.Trips;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.text.IndianMoney;

/**
 * Sends the payment reminders that are due, every morning, until each invoice is paid, paused or
 * disputed. Every reminder carries the invoice PDF with the signed POD again.
 */
@Service
public class ChaseService {

	private static final Logger log = LoggerFactory.getLogger(ChaseService.class);

	private final Invoices invoices;

	private final InvoiceService invoiceService;

	private final Parties parties;

	private final Trips trips;

	private final Pods pods;

	private final Accounts accounts;

	private final ChaseSettingsRepository settings;

	private final FollowUps followUps;

	private final Messenger messenger;

	private final Clock clock;

	public ChaseService(Invoices invoices, InvoiceService invoiceService, Parties parties, Trips trips, Pods pods,
			Accounts accounts, ChaseSettingsRepository settings, FollowUps followUps, Messenger messenger, Clock clock) {
		this.invoices = invoices;
		this.invoiceService = invoiceService;
		this.parties = parties;
		this.trips = trips;
		this.pods = pods;
		this.accounts = accounts;
		this.settings = settings;
		this.followUps = followUps;
		this.messenger = messenger;
		this.clock = clock;
	}

	/** 10 am IST every day: send what is due for every fleet. */
	@Scheduled(cron = "${app.chase.cron:0 0 10 * * *}", zone = "Asia/Kolkata")
	public void runDaily() {
		for (Long accountId : invoices.accountsWithOpenInvoices()) {
			try {
				int sent = runFor(accountId);
				if (sent > 0) {
					log.info("Sent {} payment reminders for account {}", sent, accountId);
				}
			}
			catch (RuntimeException ex) {
				log.warn("Payment reminders failed for account {}", accountId, ex);
			}
		}
	}

	/** Sends every reminder due today for one fleet; returns how many invoices were reminded. */
	public int runFor(long accountId) {
		ChaseSettings chase = settings.get(accountId);
		if (!chase.enabled()) {
			return 0;
		}
		LocalDate today = LocalDate.now(clock);
		int count = 0;
		for (Invoice invoice : invoices.dueForFollowUp(accountId, today)) {
			remind(accountId, invoice, chase, today);
			count++;
		}
		return count;
	}

	/** Sends one reminder now, whether or not one is due (the "Remind now" button). */
	public List<OutboundMessage> remindNow(long accountId, long invoiceId) {
		Invoice invoice = invoices.get(accountId, invoiceId);
		return remind(accountId, invoice, settings.get(accountId), LocalDate.now(clock));
	}

	private List<OutboundMessage> remind(long accountId, Invoice invoice, ChaseSettings chase, LocalDate today) {
		Account account = accounts.get(accountId);
		Party party = parties.get(accountId, invoice.partyId());
		StoredFile pdf = invoiceService.renderPdf(accountId, invoice.id());
		ChaseMessages.Text text = ChaseMessages.reminder(account, party, invoice, trips.ofInvoice(accountId, invoice.id()),
				today);
		long late = invoice.daysOverdue(today);
		List<OutboundMessage> sent = invoiceService.deliver(account, party, invoice, pdf, text, chase,
				"freight_payment_reminder", party.name(), invoice.number(), IndianMoney.rupees(invoice.balance()),
				String.valueOf(late), account.name());
		int stage = invoice.followUpCount() + 1;
		LocalDate next = ChasePlan.next(invoice.dueDate(), chase.offsets(), chase.repeatEveryDays(), today,
				invoice.promisedOn() != null && invoice.promisedOn().isAfter(today) ? invoice.promisedOn() : null);
		invoices.afterFollowUp(invoice.id(), stage, next);
		String note = (late > 0 ? late + " days overdue. " : "") + (sent.isEmpty()
				? "No WhatsApp number or email for this party; nothing sent."
				: "Reminder sent by " + String.join(" and ",
						sent.stream().map((m) -> (m.isWhatsApp() ? "WhatsApp" : "email") + (m.failed() ? " (failed)" : "")).toList())
						+ ".");
		followUps.record(accountId, invoice.id(), OffsetDateTime.now(clock), FollowUp.REMINDER, stage, note,
				sent.isEmpty() ? null : sent.getFirst().id());
		return sent;
	}

	/** 7:30 pm IST: a short summary of the day to the owner's WhatsApp. */
	@Scheduled(cron = "${app.chase.digest-cron:0 30 19 * * *}", zone = "Asia/Kolkata")
	public void sendDigests() {
		for (Long accountId : invoices.accountsWithOpenInvoices()) {
			if (settings.get(accountId).ownerDigest()) {
				try {
					digest(accountId);
				}
				catch (RuntimeException ex) {
					log.warn("Owner digest failed for account {}", accountId, ex);
				}
			}
		}
	}

	public OutboundMessage digest(long accountId) {
		Account account = accounts.get(accountId);
		if (!StringUtils.hasText(account.phone())) {
			return null;
		}
		LocalDate today = LocalDate.now(clock);
		OffsetDateTime dayStart = today.atStartOfDay(clock.getZone()).toOffsetDateTime();
		BigDecimal outstanding = BigDecimal.ZERO;
		BigDecimal overdue = BigDecimal.ZERO;
		int overdueCount = 0;
		for (Invoice invoice : invoices.open(accountId)) {
			outstanding = outstanding.add(invoice.balance());
			if (invoice.dueDate().isBefore(today)) {
				overdue = overdue.add(invoice.balance());
				overdueCount++;
			}
		}
		String text = """
				%s – today
				Collected: %s
				Reminders sent: %d
				PODs waiting for your check: %d
				Ready to bill: %d trips (%s)
				Outstanding: %s, of which %s overdue on %d invoices""".formatted(account.name(),
				IndianMoney.rupees(invoices.collectedSince(accountId, today)),
				followUps.remindersSince(accountId, dayStart), pods.countNeedingReview(accountId),
				trips.unbilled(accountId).trips(), IndianMoney.rupees(trips.unbilled(accountId).amount()),
				IndianMoney.rupees(outstanding), IndianMoney.rupees(overdue), overdueCount);
		return messenger.send(Outgoing.whatsApp(accountId, account.phone(), text).about("digest", null));
	}

}
