package com.liabletech.payoutcheck.outlet;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record TermsRequest(@NotNull @DecimalMin("0") @DecimalMax("60") BigDecimal commissionPct,
		@NotNull @DecimalMin("0") @DecimalMax("10") BigDecimal pgFeePct,
		@NotNull @DecimalMin("0") @DecimalMax("28") BigDecimal gstOnFeesPct,
		@NotNull @DecimalMin("0") @DecimalMax("5") BigDecimal tcsPct, @NotNull @DecimalMin("0") @DecimalMax("5") BigDecimal tdsPct,
		boolean commissionOnPackaging, @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal tolerance) {
}
