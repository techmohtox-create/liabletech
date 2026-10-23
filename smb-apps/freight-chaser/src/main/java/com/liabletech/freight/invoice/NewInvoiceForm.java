package com.liabletech.freight.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

/** Billing one party for one or more delivered trips. */
public class NewInvoiceForm {

	@NotEmpty
	private List<Long> tripIds = new ArrayList<>();

	@NotNull
	private Long partyId;

	@NotNull
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate invoiceDate;

	@NotNull
	private GstMode gstMode = GstMode.NONE;

	@DecimalMin("0")
	@DecimalMax("28")
	private BigDecimal gstRate = BigDecimal.valueOf(5);

	@Size(max = 1000)
	private String notes;

	/** Send to the party right after creating it. */
	private boolean sendNow = true;

	public List<Long> getTripIds() {
		return tripIds;
	}

	public void setTripIds(List<Long> tripIds) {
		this.tripIds = tripIds;
	}

	public Long getPartyId() {
		return partyId;
	}

	public void setPartyId(Long partyId) {
		this.partyId = partyId;
	}

	public LocalDate getInvoiceDate() {
		return invoiceDate;
	}

	public void setInvoiceDate(LocalDate invoiceDate) {
		this.invoiceDate = invoiceDate;
	}

	public GstMode getGstMode() {
		return gstMode;
	}

	public void setGstMode(GstMode gstMode) {
		this.gstMode = gstMode;
	}

	public BigDecimal getGstRate() {
		return gstRate;
	}

	public void setGstRate(BigDecimal gstRate) {
		this.gstRate = gstRate;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public boolean isSendNow() {
		return sendNow;
	}

	public void setSendNow(boolean sendNow) {
		this.sendNow = sendNow;
	}

}
