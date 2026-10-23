package com.liabletech.payoutcheck.outlet;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.liabletech.payoutcheck.common.Platform;

/**
 * What an outlet agreed with a platform. Percentages are of the order's net value (item total plus
 * packaging, less the restaurant-funded discount); {@code commissionOnPackaging} false leaves
 * packaging out of the commission base.
 */
public record PlatformTerms(long id, long outletId, Platform platform, BigDecimal commissionPct, BigDecimal pgFeePct,
		BigDecimal gstOnFeesPct, BigDecimal tcsPct, BigDecimal tdsPct, boolean commissionOnPackaging, BigDecimal tolerance,
		OffsetDateTime updatedAt) {

	/**
	 * Starting values for a new outlet. They are placeholders, not anyone's contract: owners replace
	 * them with the rates in their own platform agreement. GST on platform fees is 18%. Income-tax
	 * TDS under section 194-O is 0.1% from October 2024. GST TCS is 0 because restaurant services
	 * sold through an app fall under section 9(5), where the platform pays the GST itself.
	 */
	public static PlatformTerms defaults(long outletId, Platform platform) {
		BigDecimal pg = platform == Platform.SWIGGY ? new BigDecimal("2.000") : new BigDecimal("1.840");
		return new PlatformTerms(0, outletId, platform, new BigDecimal("18.000"), pg, new BigDecimal("18.000"),
				new BigDecimal("0.000"), new BigDecimal("0.100"), true, new BigDecimal("1.00"), null);
	}

}
