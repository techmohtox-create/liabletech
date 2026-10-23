package com.liabletech.freight.chase;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.springframework.util.StringUtils;

import com.liabletech.freight.invoice.Invoice;
import com.liabletech.freight.party.Party;
import com.liabletech.freight.trip.Trip;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.text.IndianMoney;

/**
 * The words of each message to a party. The tone firms up as an invoice gets older, but stays
 * polite: these are customers the owner wants to keep carrying loads for.
 */
public final class ChaseMessages {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private ChaseMessages() {
	}

	/** The message that goes with the invoice the first time it is sent. */
	public static Text invoice(Account account, Party party, Invoice invoice, List<Trip> trips) {
		String body = """
				Dear %s,

				Please find attached our invoice %s dated %s for %s.
				Amount payable: %s, due by %s.
				The signed proof of delivery is attached with the invoice.
				%s
				Thank you,
				%s%s""".formatted(greeting(party), invoice.number(), DATE.format(invoice.invoiceDate()), lrList(trips),
				rupees(invoice.balance()), DATE.format(invoice.dueDate()), paymentLines(account, invoice), account.name(),
				phoneLine(account));
		String subject = "Invoice %s (%s) from %s".formatted(invoice.number(), rupees(invoice.balance()), account.name());
		return new Text(subject, tidy(body));
	}

	/** A reminder; the wording depends on how late the payment is. */
	public static Text reminder(Account account, Party party, Invoice invoice, List<Trip> trips, LocalDate today) {
		long late = ChronoUnit.DAYS.between(invoice.dueDate(), today);
		String opening;
		String ask;
		String subjectLead;
		if (late < 0) {
			opening = "A friendly reminder that our invoice %s for %s is due on %s.".formatted(invoice.number(),
					lrList(trips), DATE.format(invoice.dueDate()));
			ask = "Please plan the payment of %s.".formatted(rupees(invoice.balance()));
			subjectLead = "Due soon";
		}
		else if (late == 0) {
			opening = "Our invoice %s for %s is due today.".formatted(invoice.number(), lrList(trips));
			ask = "Please release the payment of %s today.".formatted(rupees(invoice.balance()));
			subjectLead = "Due today";
		}
		else if (late < 15) {
			opening = "Our invoice %s for %s was due on %s and is %d %s overdue.".formatted(invoice.number(),
					lrList(trips), DATE.format(invoice.dueDate()), late, late == 1 ? "day" : "days");
			ask = "Please release the payment of %s at the earliest.".formatted(rupees(invoice.balance()));
			subjectLead = "Payment reminder";
		}
		else if (late < 45) {
			opening = "This is a second reminder: invoice %s for %s has been outstanding for %d days past its due date of %s."
				.formatted(invoice.number(), lrList(trips), late, DATE.format(invoice.dueDate()));
			ask = "Kindly confirm the date by which %s will be paid.".formatted(rupees(invoice.balance()));
			subjectLead = "Second reminder";
		}
		else {
			opening = "Invoice %s for %s is now %d days overdue (due %s). The signed POD was shared with the invoice."
				.formatted(invoice.number(), lrList(trips), late, DATE.format(invoice.dueDate()));
			ask = "We request you to clear %s immediately so that we can continue to serve you without interruption."
				.formatted(rupees(invoice.balance()));
			subjectLead = "Overdue " + late + " days";
		}
		String body = """
				Dear %s,

				%s
				%s
				The invoice with the signed proof of delivery is attached again for your reference.
				%s
				Regards,
				%s%s""".formatted(greeting(party), opening, ask, paymentLines(account, invoice), account.name(),
				phoneLine(account));
		String subject = "%s: invoice %s, %s – %s".formatted(subjectLead, invoice.number(), rupees(invoice.balance()),
				account.name());
		return new Text(subject, tidy(body));
	}

	public static String thankYou(Account account, Party party, Invoice invoice, BigDecimal amount) {
		return "Dear %s, we have received %s against invoice %s. Thank you for the prompt payment!\n%s".formatted(
				greeting(party), rupees(amount), invoice.number(), account.name());
	}

	/** A UPI deep link that opens any UPI app with the amount filled in. */
	public static String upiLink(Account account, Invoice invoice) {
		if (!StringUtils.hasText(account.upiId())) {
			return null;
		}
		return "upi://pay?pa=" + encode(account.upiId()) + "&pn=" + encode(account.name()) + "&am="
				+ invoice.balance().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "&cu=INR&tn="
				+ encode("Invoice " + invoice.number());
	}

	private static String paymentLines(Account account, Invoice invoice) {
		StringBuilder lines = new StringBuilder();
		if (account.hasBankDetails()) {
			lines.append("\nBank: ")
				.append(StringUtils.hasText(account.bankAccountName()) ? account.bankAccountName() + ", " : "")
				.append("A/c ")
				.append(account.bankAccountNumber())
				.append(", IFSC ")
				.append(account.bankIfsc())
				.append(StringUtils.hasText(account.bankName()) ? " (" + account.bankName() + ")" : "");
		}
		String upi = upiLink(account, invoice);
		if (upi != null) {
			lines.append("\nUPI: ").append(account.upiId()).append("\nPay by UPI: ").append(upi);
		}
		return lines.isEmpty() ? "" : lines.append("\n").toString();
	}

	private static String lrList(List<Trip> trips) {
		List<String> numbers = trips.stream().map(Trip::lrNumber).filter(StringUtils::hasText).toList();
		if (numbers.isEmpty()) {
			return trips.size() == 1 ? "1 trip" : trips.size() + " trips";
		}
		if (numbers.size() <= 4) {
			return "LR " + String.join(", ", numbers);
		}
		return "LR " + String.join(", ", numbers.subList(0, 3)) + " and " + (numbers.size() - 3) + " more";
	}

	private static String greeting(Party party) {
		return StringUtils.hasText(party.contactPerson()) ? party.contactPerson() : party.name();
	}

	private static String phoneLine(Account account) {
		return StringUtils.hasText(account.phone()) ? "\n" + account.phone() : "";
	}

	private static String rupees(BigDecimal amount) {
		return IndianMoney.rupees(amount);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/** Collapses the blank lines left by optional parts. */
	private static String tidy(String text) {
		return text.replaceAll("\n{3,}", "\n\n").strip();
	}

	public record Text(String subject, String body) {
	}

}
