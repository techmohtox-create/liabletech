package com.liabletech.parts.settings;

import java.math.BigDecimal;

/**
 * How the counter answers.
 *
 * @param acknowledge say "got it, checking" with the guess as soon as an enquiry arrives
 * @param autoQuote (Pro) answer a sure match with its price straight away
 * @param autoQuoteConfidence how sure the reading must be for an automatic quote
 * @param nudgeMinutes (Pro) tell the owner on WhatsApp when an enquiry has waited this long; 0 is off
 * @param readyMinutes how soon a booked part is ready for pickup, for the booking message
 */
public record ShopSettings(Long accountId, boolean acknowledge, boolean autoQuote, BigDecimal autoQuoteConfidence,
		int nudgeMinutes, int readyMinutes) {

	public static ShopSettings defaults(long accountId) {
		return new ShopSettings(accountId, true, true, new BigDecimal("0.80"), 3, 15);
	}

}
