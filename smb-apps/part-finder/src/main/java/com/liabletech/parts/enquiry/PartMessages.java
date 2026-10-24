package com.liabletech.parts.enquiry;

import java.math.BigDecimal;
import java.util.List;

import com.liabletech.parts.booking.Booking;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.text.IndianMoney;

/** The words of every reply to a mechanic: short, with prices, and the number to reply with. */
public final class PartMessages {

	public static final String HELP = "Send a photo of the old part or its box, or write the part and the vehicle, "
			+ "e.g. \"Swift 2019 front brake pad\". We reply with the price and stock.";

	private PartMessages() {
	}

	/** The instant answer: what was understood, and the one question that would settle it, if any. */
	public static String acknowledge(Account account, Identification found) {
		String what = found.knowsPart() || found.knowsVehicle() ? found.describe() : null;
		String start = "👍 Got it" + (what == null || what.isBlank() ? "" : ": " + what) + ". ";
		return start + (found.question() != null ? found.question() : "Checking stock, we will reply in a minute.")
				+ signature(account);
	}

	public static String quote(Account account, Identification found, List<QuoteOptions.NewOption> options, int quantity,
			int readyMinutes) {
		StringBuilder text = new StringBuilder("✅ ");
		String what = found.describe();
		text.append(what.isBlank() ? "Available" : what).append(":");
		int number = 1;
		for (QuoteOptions.NewOption option : options) {
			text.append('\n');
			if (options.size() > 1) {
				text.append(number).append(") ");
			}
			text.append(option.description()).append(" – ").append(IndianMoney.rupees(option.price()));
			if (quantity > 1) {
				text.append(" each (").append(quantity).append(" = ")
					.append(IndianMoney.rupees(option.price().multiply(BigDecimal.valueOf(quantity)))).append(')');
			}
			number++;
		}
		text.append("\n").append(options.size() > 1 ? "Reply 1" + (options.size() == 2 ? " or 2" : " to " + options.size())
				+ " to book." : "Reply 1 to book.");
		text.append(" Ready for pickup in ").append(readyMinutes).append(" min.");
		return text.append(signature(account)).toString();
	}

	public static String notAvailable(Account account, Identification found, String note) {
		String what = found.describe();
		return "❌ Sorry, " + (what.isBlank() ? "that part" : what) + " is not in stock right now."
				+ (note == null || note.isBlank() ? "" : " " + note.trim()) + signature(account);
	}

	public static String booked(Account account, Booking booking, int readyMinutes) {
		return "✅ Booked: " + booking.description() + (booking.quantity() > 1 ? " × " + booking.quantity() : "") + ", "
				+ IndianMoney.rupees(booking.total()) + ". Ready for pickup in " + readyMinutes
				+ " min. Reply CANCEL if you no longer need it." + signature(account);
	}

	public static String soldOut(Account account) {
		return "Sorry, that one has just gone out of stock. We will call you with another option." + signature(account);
	}

	public static String cancelled(Account account, Booking booking) {
		return "Cancelled: " + booking.description() + "." + signature(account);
	}

	public static String whichOption(int count) {
		return "Please reply with the option number, 1" + (count > 1 ? " to " + count : "") + ".";
	}

	private static String signature(Account account) {
		return "\n– " + account.name();
	}

}
