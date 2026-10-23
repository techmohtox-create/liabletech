package com.liabletech.platform.numbering;

import java.time.LocalDate;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gapless, per-account document numbers that restart every Indian financial year (April to March),
 * as GST rules expect for invoice series: {@code INV/2026-27/0007}.
 */
@Service
public class DocumentNumbers {

	private final JdbcClient jdbc;

	public DocumentNumbers(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Runs inside the caller's transaction, so a rolled-back invoice does not burn its number. */
	@Transactional(propagation = Propagation.MANDATORY)
	public String next(long accountId, String prefix, LocalDate date) {
		String period = financialYear(date);
		int value = claim(accountId, prefix, period);
		return "%s/%s/%04d".formatted(prefix, period, value);
	}

	/** "2026-27" for any date from 1 April 2026 to 31 March 2027. */
	public static String financialYear(LocalDate date) {
		int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
		return start + "-" + String.format("%02d", (start + 1) % 100);
	}

	private int claim(long accountId, String series, String period) {
		for (int attempt = 0; attempt < 3; attempt++) {
			Integer current = jdbc.sql("""
					SELECT next_value FROM doc_sequence WHERE account_id = ? AND series = ? AND period = ? FOR UPDATE
					""").params(accountId, series, period).query(Integer.class).optional().orElse(null);
			if (current != null) {
				jdbc.sql("UPDATE doc_sequence SET next_value = ? WHERE account_id = ? AND series = ? AND period = ?")
					.params(current + 1, accountId, series, period)
					.update();
				return current;
			}
			try {
				jdbc.sql("INSERT INTO doc_sequence (account_id, series, period, next_value) VALUES (?, ?, ?, 2)")
					.params(accountId, series, period)
					.update();
				return 1;
			}
			catch (DuplicateKeyException ex) {
				// another transaction created the series first; read it again
			}
		}
		throw new IllegalStateException("Could not allocate a number in series " + series);
	}

}
