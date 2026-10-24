package com.liabletech.parts.report;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.liabletech.parts.catalog.PartType;
import com.liabletech.parts.catalog.VehicleCatalog;

/**
 * How the counter is doing: how fast enquiries are answered, how many turn into bookings, and the
 * parts mechanics asked for that the shop did not have.
 */
@Repository
public class Reports {

	/** An answer within this many seconds is "fast": before the mechanic has asked the next shop. */
	public static final long FAST_SECONDS = 120;

	private final JdbcClient jdbc;

	private final Clock clock;

	public Reports(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public Stats stats(long accountId, OffsetDateTime from, OffsetDateTime to) {
		List<Asked> asked = asked(accountId, from, to);
		List<Long> seconds = asked.stream().map(Asked::responseSeconds).filter(Objects::nonNull).sorted().toList();
		long fast = seconds.stream().filter((s) -> s <= FAST_SECONDS).count();
		long auto = asked.stream().filter((a) -> "AUTO".equals(a.repliedBy())).count();
		long booked = asked.stream().filter((a) -> "BOOKED".equals(a.status())).count();
		long missed = asked.stream().filter(Asked::missed).count();
		BigDecimal value = jdbc.sql("""
				SELECT COALESCE(SUM(price * quantity), 0) FROM booking
				WHERE account_id = ? AND status <> 'CANCELLED' AND created_at >= ? AND created_at < ?
				""").params(accountId, from, to).query(BigDecimal.class).single();
		long waiting = asked.stream().filter((a) -> "NEW".equals(a.status()) && a.responseSeconds() == null).count();
		return new Stats(asked.size(), seconds.size(), median(seconds), seconds.isEmpty() ? null : 100.0 * fast / seconds.size(),
				auto, booked, value, missed, waiting);
	}

	/** Enquiries and bookings per day, oldest first, for the last {@code days} days including today. */
	public List<Day> perDay(long accountId, int days) {
		LocalDate today = LocalDate.now(clock);
		LocalDate first = today.minusDays(days - 1L);
		OffsetDateTime from = first.atStartOfDay(clock.getZone()).toOffsetDateTime();
		Map<LocalDate, long[]> counts = new LinkedHashMap<>();
		for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
			counts.put(day, new long[2]);
		}
		for (Asked a : asked(accountId, from, from.plusDays(days))) {
			long[] count = counts.get(a.receivedAt().atZoneSameInstant(clock.getZone()).toLocalDate());
			if (count != null) {
				count[0]++;
				if ("BOOKED".equals(a.status())) {
					count[1]++;
				}
			}
		}
		List<Day> result = new ArrayList<>();
		counts.forEach((day, count) -> result.add(new Day(day, count[0], count[1])));
		return result;
	}

	/**
	 * What mechanics asked for and the shop could not supply: told "not in stock", or closed
	 * without a quote. Grouped by part and vehicle, most asked first.
	 */
	public List<Missed> missed(long accountId, OffsetDateTime from, int limit) {
		Map<String, Missed> grouped = new LinkedHashMap<>();
		for (Asked a : asked(accountId, from, OffsetDateTime.now(clock).plusMinutes(1))) {
			if (!a.missed()) {
				continue;
			}
			PartType type = PartType.parse(a.partType());
			String part = type == null || type == PartType.OTHER ? (a.partName() != null ? a.partName() : "Unidentified part")
					: type.label();
			String vehicle = a.vehicleModel() == null ? "" : VehicleCatalog.canonical(a.vehicleModel());
			String key = part.toLowerCase() + "|" + vehicle.toLowerCase();
			Missed before = grouped.get(key);
			grouped.put(key, before == null ? new Missed(part, vehicle, 1, a.receivedAt(), a.words())
					: new Missed(part, vehicle, before.times() + 1,
							a.receivedAt().isAfter(before.lastAsked()) ? a.receivedAt() : before.lastAsked(), before.example()));
		}
		return grouped.values()
			.stream()
			.sorted(Comparator.comparingLong(Missed::times).reversed().thenComparing(Missed::lastAsked, Comparator.reverseOrder()))
			.limit(limit)
			.toList();
	}

	/** What sold through the counter, by part. */
	public List<Sold> topBooked(long accountId, OffsetDateTime from, int limit) {
		return jdbc.sql("""
				SELECT description, COUNT(*) AS bookings, SUM(quantity) AS quantity, SUM(price * quantity) AS booked_value
				FROM booking WHERE account_id = ? AND status <> 'CANCELLED' AND created_at >= ?
				GROUP BY description ORDER BY COUNT(*) DESC, SUM(price * quantity) DESC LIMIT ?
				""").params(accountId, from, limit).query(Sold.class).list();
	}

	private List<Asked> asked(long accountId, OffsetDateTime from, OffsetDateTime to) {
		return jdbc.sql("""
				SELECT e.id, e.status, e.received_at, e.first_reply_at, e.replied_by, e.part_type, e.part_name,
				    e.vehicle_model, COALESCE(e.transcript, e.text) AS words,
				    (SELECT COUNT(*) FROM quote_option q WHERE q.enquiry_id = e.id) AS options
				FROM enquiry e WHERE e.account_id = ? AND e.received_at >= ? AND e.received_at < ?
				ORDER BY e.received_at
				""").params(accountId, from, to).query(Asked.class).list();
	}

	static Long median(List<Long> sorted) {
		if (sorted.isEmpty()) {
			return null;
		}
		int middle = sorted.size() / 2;
		return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
	}

	record Asked(Long id, String status, OffsetDateTime receivedAt, OffsetDateTime firstReplyAt, String repliedBy,
			String partType, String partName, String vehicleModel, String words, long options) {

		Long responseSeconds() {
			return firstReplyAt == null ? null : Math.max(0, java.time.Duration.between(receivedAt, firstReplyAt).getSeconds());
		}

		boolean missed() {
			return "NOT_AVAILABLE".equals(status) || "CLOSED".equals(status) && options == 0;
		}

	}

	/**
	 * @param answered enquiries that got a real answer
	 * @param medianSeconds the typical time to that answer
	 * @param fastPercent share of answers within {@link #FAST_SECONDS}
	 * @param waiting enquiries still waiting for their first answer
	 */
	public record Stats(long enquiries, long answered, Long medianSeconds, Double fastPercent, long autoQuoted,
			long booked, BigDecimal bookedValue, long missed, long waiting) {

		/** Share of enquiries that became a booking, or null with none. */
		public Integer conversion() {
			return enquiries == 0 ? null : (int) Math.round(100.0 * booked / enquiries);
		}

	}

	public record Day(LocalDate day, long enquiries, long bookings) {
	}

	public record Missed(String part, String vehicle, long times, OffsetDateTime lastAsked, String example) {
	}

	public record Sold(String description, long bookings, long quantity, BigDecimal bookedValue) {
	}

}
