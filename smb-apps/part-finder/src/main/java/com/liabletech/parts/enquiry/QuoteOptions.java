package com.liabletech.parts.enquiry;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The numbered options a reply offered, so "2" from the mechanic books the second one. */
@Repository
public class QuoteOptions {

	private final JdbcClient jdbc;

	public QuoteOptions(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Replaces what the enquiry offered: a new quote supersedes the last one. */
	public void replace(long enquiryId, List<NewOption> options, OffsetDateTime at) {
		jdbc.sql("DELETE FROM quote_option WHERE enquiry_id = ?").param(enquiryId).update();
		int number = 1;
		for (NewOption option : options) {
			jdbc.sql("""
					INSERT INTO quote_option (enquiry_id, option_no, part_id, description, price, created_at)
					VALUES (?, ?, ?, ?, ?, ?)
					""").params(enquiryId, number++, option.partId(), option.description(), option.price(), at).update();
		}
	}

	public List<QuoteOption> forEnquiry(long enquiryId) {
		return jdbc.sql("SELECT * FROM quote_option WHERE enquiry_id = ? ORDER BY option_no").param(enquiryId)
			.query(QuoteOption.class)
			.list();
	}

	public Optional<QuoteOption> option(long enquiryId, int number) {
		return jdbc.sql("SELECT * FROM quote_option WHERE enquiry_id = ? AND option_no = ?")
			.params(enquiryId, number)
			.query(QuoteOption.class)
			.optional();
	}

	public record NewOption(Long partId, String description, BigDecimal price) {
	}

	public record QuoteOption(Long id, Long enquiryId, int optionNo, Long partId, String description, BigDecimal price,
			OffsetDateTime createdAt) {
	}

}
