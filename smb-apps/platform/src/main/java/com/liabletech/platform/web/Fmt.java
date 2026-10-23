package com.liabletech.platform.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.liabletech.platform.text.IndianMoney;
import com.liabletech.platform.text.Phones;

/**
 * Formatting for templates, used as {@code ${@fmt.rupees(amount)}}. Dates and times show in Indian
 * Standard Time regardless of where the server runs.
 */
@Component("fmt")
public class Fmt {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

	private final Clock clock;

	public Fmt(Clock clock) {
		this.clock = clock;
	}

	public String rupees(BigDecimal amount) {
		return amount == null ? "–" : IndianMoney.rupees(amount);
	}

	/** Whole rupees, for dashboards: "₹1,23,457". */
	public String rupees0(BigDecimal amount) {
		return amount == null ? "–" : "₹" + IndianMoney.group(amount, 0);
	}

	/** A quantity with Indian grouping and no trailing zeros: "1,250", "12.5". */
	public String num(BigDecimal value) {
		if (value == null) {
			return "–";
		}
		BigDecimal stripped = value.stripTrailingZeros();
		int scale = Math.max(0, Math.min(stripped.scale(), 3));
		return IndianMoney.group(value.setScale(scale, RoundingMode.HALF_UP), scale);
	}

	public String percent(Number value) {
		return value == null ? "–" : Math.round(value.doubleValue()) + "%";
	}

	public String date(LocalDate date) {
		return date == null ? "–" : DATE.format(date);
	}

	public String shortDate(LocalDate date) {
		return date == null ? "–" : SHORT_DATE.format(date);
	}

	public String dateTime(OffsetDateTime time) {
		if (time == null) {
			return "–";
		}
		OffsetDateTime local = time.atZoneSameInstant(clock.getZone()).toOffsetDateTime();
		LocalDate day = local.toLocalDate();
		LocalDate today = LocalDate.now(clock);
		String prefix = day.equals(today) ? "Today" : day.equals(today.minusDays(1)) ? "Yesterday" : SHORT_DATE.format(day);
		return prefix + ", " + TIME.format(local).toLowerCase(Locale.ENGLISH);
	}

	/** "just now", "4 min ago", "3 h ago", or the date for anything older than a day. */
	public String ago(OffsetDateTime time) {
		if (time == null) {
			return "–";
		}
		long seconds = Duration.between(time.toInstant(), clock.instant()).getSeconds();
		if (seconds < 60) {
			return "just now";
		}
		if (seconds < 3600) {
			return (seconds / 60) + " min ago";
		}
		if (seconds < 86400) {
			return (seconds / 3600) + " h ago";
		}
		return dateTime(time);
	}

	/** Days from today to the date: negative when the date has passed. */
	public long daysFromToday(LocalDate date) {
		return date == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(clock), date);
	}

	/** "in 5 days", "today", "3 days ago". */
	public String relativeDays(LocalDate date) {
		if (date == null) {
			return "–";
		}
		long days = daysFromToday(date);
		if (days == 0) {
			return "today";
		}
		if (days == 1) {
			return "tomorrow";
		}
		if (days == -1) {
			return "yesterday";
		}
		return days > 0 ? "in " + days + " days" : (-days) + " days ago";
	}

	/** A short duration such as "45 s", "3 min" or "2 h 10 min". */
	public String duration(Long seconds) {
		if (seconds == null) {
			return "–";
		}
		if (seconds < 60) {
			return seconds + " s";
		}
		if (seconds < 3600) {
			return (seconds / 60) + " min";
		}
		long minutes = (seconds % 3600) / 60;
		return (seconds / 3600) + " h" + (minutes > 0 ? " " + minutes + " min" : "");
	}

	public String phone(String phone) {
		return Phones.pretty(phone);
	}

	/** Lower-case words from an enum-style constant: "NEEDS_REVIEW" becomes "needs review". */
	public String label(Object constant) {
		return constant == null ? "" : constant.toString().replace('_', ' ').toLowerCase(Locale.ENGLISH);
	}

	/** "1 trip", "3 trips". */
	public String plural(long count, String singular, String plural) {
		return count + " " + (count == 1 ? singular : plural);
	}

}
