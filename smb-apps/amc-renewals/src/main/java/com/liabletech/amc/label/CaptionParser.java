package com.liabletech.amc.label;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.liabletech.platform.text.Phones;

/**
 * Reads what technicians type under a label photo: "Sharma ji 9812345678 AMC 1499 bedroom",
 * "9876543210 no amc", "Mrs Kulkarni, Kothrud, comprehensive amc 3999 paid cash".
 */
public final class CaptionParser {

	/** "₹1499", "Rs. 1,499", "1499/-", "1499 rs". */
	private static final Pattern MARKED_AMOUNT = Pattern
		.compile("(?i)(?:₹|\\brs\\.?|\\binr)\\s*(\\d[\\d,]{2,6})|\\b(\\d[\\d,]{2,6})\\s*(?:/-|rs\\b|rupees\\b)");

	/** "AMC 1499", "amc comprehensive 3999": the price named after the word AMC. */
	private static final Pattern AMC_AMOUNT = Pattern.compile("(?i)\\bamc\\b[^\\d]{0,20}?(\\d[\\d,]{2,6})");

	/** A bare price, used only next to a plan's name: "gold 3999". */
	private static final Pattern PLAIN_AMOUNT = Pattern.compile("\\b(\\d[\\d,]{2,6})\\b");

	private static final Pattern NO_AMC = Pattern.compile("(?i)\\b(no|without|not|nahi|nai)\\s+amc\\b|\\bamc\\s+(no|nahi|nai|later)\\b");

	private static final Pattern AMC = Pattern.compile("(?i)\\bamc\\b|annual maintenance|\\bcontract\\b");

	private static final Pattern PAID = Pattern.compile("(?i)\\b(paid|cash|upi|gpay|phonepe|paytm|received)\\b");

	private static final Pattern LOCATION = Pattern.compile("(?i)\\b(bedroom|master bedroom|hall|living room|kitchen|office|shop|drawing room|kids room|guest room|bathroom)\\b");

	private static final Pattern NOISE = Pattern.compile("(?i)\\b(amc|sold|done|new|customer|cust|name|mob|mobile|ph|phone|no|paid|cash|upi|gpay|phonepe|paytm|received|comprehensive|comp|basic|gold|silver|care|plan|rs|inr|ji|sir|madam|ro|ac|split|window|purifier|geyser|service|servicing|visit)\\b");

	private CaptionParser() {
	}

	public static Caption parse(String caption) {
		if (caption == null || caption.isBlank()) {
			return new Caption(null, null, false, null, false, null, null);
		}
		String text = caption.trim();
		String phone = Phones.findIn(text);
		String withoutPhone = phone == null ? text : removePhone(text);
		BigDecimal amount = amount(MARKED_AMOUNT.matcher(withoutPhone));
		if (amount == null) {
			amount = amount(AMC_AMOUNT.matcher(withoutPhone));
		}
		String lower = withoutPhone.toLowerCase(Locale.ROOT);
		String planHint = lower.contains("comprehensive") || lower.matches(".*\\bcomp\\b.*") ? "comprehensive"
				: lower.contains("gold") ? "gold" : lower.contains("basic") ? "basic"
						: lower.matches(".*\\bcare\\b.*") ? "care" : null;
		if (amount == null && planHint != null) {
			amount = amount(PLAIN_AMOUNT.matcher(withoutPhone));
		}
		// "RO care 2499" names a plan and its price without the word AMC.
		boolean amc = (AMC.matcher(withoutPhone).find() || (planHint != null && amount != null))
				&& !NO_AMC.matcher(withoutPhone).find();
		Matcher location = LOCATION.matcher(withoutPhone);
		String room = location.find() ? capitalize(location.group(1).toLowerCase(Locale.ROOT)) : null;
		String name = withoutPhone;
		name = LOCATION.matcher(name).replaceAll(" ");
		name = NOISE.matcher(name).replaceAll(" ");
		name = name.replaceAll("[^\\p{L}.'\\s]", " ").trim();
		name = String.join(" ", java.util.Arrays.stream(name.split("\\s+")).filter((w) -> w.matches(".*\\p{L}.*")).toList());
		if (name.length() < 2 || name.split(" ").length > 5) {
			name = null;
		}
		return new Caption(phone, name == null ? null : titleCase(name), amc, amount, PAID.matcher(withoutPhone).find(),
				planHint, room);
	}

	/** The first plausible AMC price the matcher finds: between ₹199 and ₹50,000. */
	private static BigDecimal amount(Matcher matcher) {
		while (matcher.find()) {
			String digits = null;
			for (int group = 1; group <= matcher.groupCount() && digits == null; group++) {
				digits = matcher.group(group);
			}
			if (digits == null) {
				continue;
			}
			BigDecimal value = new BigDecimal(digits.replace(",", ""));
			if (value.compareTo(BigDecimal.valueOf(199)) >= 0 && value.compareTo(BigDecimal.valueOf(50000)) <= 0) {
				return value;
			}
		}
		return null;
	}

	private static String removePhone(String text) {
		return text.replaceAll("(\\+?91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}", " ");
	}

	private static String capitalize(String text) {
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String titleCase(String text) {
		StringBuilder out = new StringBuilder();
		for (String word : text.split(" ")) {
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(word.length() <= 1 ? word.toUpperCase(Locale.ROOT)
					: Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT));
		}
		return out.toString();
	}

	/**
	 * @param amcSold the caption says an AMC was sold (and not "no AMC")
	 * @param planHint "comprehensive", "gold", "basic" or "care" when the caption names a plan
	 */
	public record Caption(String phone, String name, boolean amcSold, BigDecimal amount, boolean paid, String planHint,
			String location) {
	}

}
