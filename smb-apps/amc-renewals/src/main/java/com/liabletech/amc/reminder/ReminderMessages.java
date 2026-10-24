package com.liabletech.amc.reminder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.Customer;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.text.IndianMoney;
import com.liabletech.platform.text.Phones;

/** The words of each reminder. Short, in the business's name, with the link and a way to stop them. */
public final class ReminderMessages {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private ReminderMessages() {
	}

	/** "2 Nov 2026", as dates appear in every message. */
	public static String date(LocalDate date) {
		return DATE.format(date);
	}

	/** A renewal reminder, worded for how far the date is: before, on or after the end of the contract. */
	public static String renewal(Account account, Customer customer, Appliance appliance, Contract contract,
			BigDecimal price, LocalDate today, String link) {
		long days = ChronoUnit.DAYS.between(today, contract.endsOn());
		StringBuilder text = new StringBuilder(greeting(customer));
		String what = opening(customer, "your") + " " + contract.planName() + " for the " + appliance.describe();
		if (days > 0) {
			text.append(what).append(" ends on ").append(DATE.format(contract.endsOn())).append(" (")
				.append(days).append(days == 1 ? " day" : " days").append(" left). Renew for ")
				.append(IndianMoney.rupees(price)).append(" to keep your ").append(contract.visitsIncluded())
				.append(" scheduled services for another year.");
		}
		else if (days == 0) {
			text.append(what).append(" ends today. Renew for ").append(IndianMoney.rupees(price))
				.append(" so your next service stays covered.");
		}
		else if (days >= -14) {
			text.append(what).append(" ended on ").append(DATE.format(contract.endsOn()))
				.append(". Renew for ").append(IndianMoney.rupees(price)).append(" to restart your scheduled services.");
		}
		else {
			text.append(what).append(" ended on ").append(DATE.format(contract.endsOn()))
				.append(". Regular servicing keeps it running well; renew for ").append(IndianMoney.rupees(price))
				.append(" whenever you are ready.");
		}
		text.append("\n\nRenew or book a visit: ").append(link);
		text.append("\nReply YES to renew, or STOP to stop these messages.");
		return text.append(signature(account)).toString();
	}

	/** A reminder that a service visit included in the contract is due. */
	public static String serviceDue(Account account, Customer customer, Appliance appliance, Contract contract, int visit,
			String link) {
		return greeting(customer) + opening(customer, "your") + " " + appliance.describe() + " is due for its service (" + visit + " of "
				+ contract.visitsIncluded() + " under your " + contract.planName() + ").\n\nPick a day: " + link
				+ "\nOr reply SERVICE and we will call you." + signature(account);
	}

	public static String renewalConfirmed(Account account, Customer customer, Appliance appliance, Contract renewed) {
		return greeting(customer) + opening(customer, "thank you") + "! Your " + renewed.planName() + " for the " + appliance.describe()
				+ " is renewed until " + DATE.format(renewed.endsOn()) + "." + signature(account);
	}

	public static String visitBooked(Account account, Customer customer, LocalDate on, String slot, String technician) {
		return greeting(customer) + opening(customer, "your") + " service visit is booked for " + DATE.format(on)
				+ (slot == null || slot.isBlank() ? "" : ", " + slot.toLowerCase(Locale.ROOT))
				+ (technician == null ? "" : ". Technician: " + technician) + "." + signature(account);
	}

	private static String greeting(Customer customer) {
		String name = customer.greetingName();
		return name.isEmpty() ? "Namaste! " : "Namaste " + name + " ji, ";
	}

	private static String signature(Account account) {
		return "\n– " + account.name() + (account.phone() == null ? "" : ", " + Phones.pretty(account.phone()));
	}

	/** The first word after the greeting: capitalised after "Namaste!", not after "Namaste Sunita ji,". */
	private static String opening(Customer customer, String word) {
		return customer.greetingName().isEmpty() ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word;
	}

}
