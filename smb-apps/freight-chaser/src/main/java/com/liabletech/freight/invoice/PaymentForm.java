package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

public class PaymentForm {

	@NotNull
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate receivedOn;

	@NotNull
	@DecimalMin("0")
	private BigDecimal amount;

	@DecimalMin("0")
	private BigDecimal tds;

	@Pattern(regexp = "NEFT|RTGS|IMPS|UPI|CHEQUE|CASH")
	private String mode = "NEFT";

	@Size(max = 100)
	private String reference;

	private boolean thankYou = true;

	public LocalDate getReceivedOn() {
		return receivedOn;
	}

	public void setReceivedOn(LocalDate receivedOn) {
		this.receivedOn = receivedOn;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getTds() {
		return tds;
	}

	public void setTds(BigDecimal tds) {
		this.tds = tds;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public boolean isThankYou() {
		return thankYou;
	}

	public void setThankYou(boolean thankYou) {
		this.thankYou = thankYou;
	}

}
