package com.liabletech.freight.trip;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

public class TripForm {

	@Size(max = 50)
	private String lrNumber;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate lrDate;

	private Long partyId;

	private Long vehicleId;

	private Long driverId;

	@Size(max = 100)
	private String origin;

	@Size(max = 100)
	private String destination;

	@Size(max = 150)
	private String consignee;

	@Size(max = 200)
	private String goods;

	@DecimalMin("0")
	private BigDecimal freightAmount;

	@DecimalMin("0")
	private BigDecimal advanceAmount;

	@DecimalMin("0")
	private BigDecimal detentionAmount;

	@DecimalMin("0")
	private BigDecimal otherCharges;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate deliveredOn;

	@Size(max = 1000)
	private String notes;

	public static TripForm of(Trip trip) {
		TripForm form = new TripForm();
		form.lrNumber = trip.lrNumber();
		form.lrDate = trip.lrDate();
		form.partyId = trip.partyId();
		form.vehicleId = trip.vehicleId();
		form.driverId = trip.driverId();
		form.origin = trip.origin();
		form.destination = trip.destination();
		form.consignee = trip.consignee();
		form.goods = trip.goods();
		form.freightAmount = trip.freightAmount();
		form.advanceAmount = trip.advanceAmount();
		form.detentionAmount = trip.detentionAmount();
		form.otherCharges = trip.otherCharges();
		form.deliveredOn = trip.deliveredOn();
		form.notes = trip.notes();
		return form;
	}

	public String getLrNumber() {
		return lrNumber;
	}

	public void setLrNumber(String lrNumber) {
		this.lrNumber = lrNumber;
	}

	public LocalDate getLrDate() {
		return lrDate;
	}

	public void setLrDate(LocalDate lrDate) {
		this.lrDate = lrDate;
	}

	public Long getPartyId() {
		return partyId;
	}

	public void setPartyId(Long partyId) {
		this.partyId = partyId;
	}

	public Long getVehicleId() {
		return vehicleId;
	}

	public void setVehicleId(Long vehicleId) {
		this.vehicleId = vehicleId;
	}

	public Long getDriverId() {
		return driverId;
	}

	public void setDriverId(Long driverId) {
		this.driverId = driverId;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public String getConsignee() {
		return consignee;
	}

	public void setConsignee(String consignee) {
		this.consignee = consignee;
	}

	public String getGoods() {
		return goods;
	}

	public void setGoods(String goods) {
		this.goods = goods;
	}

	public BigDecimal getFreightAmount() {
		return freightAmount;
	}

	public void setFreightAmount(BigDecimal freightAmount) {
		this.freightAmount = freightAmount;
	}

	public BigDecimal getAdvanceAmount() {
		return advanceAmount;
	}

	public void setAdvanceAmount(BigDecimal advanceAmount) {
		this.advanceAmount = advanceAmount;
	}

	public BigDecimal getDetentionAmount() {
		return detentionAmount;
	}

	public void setDetentionAmount(BigDecimal detentionAmount) {
		this.detentionAmount = detentionAmount;
	}

	public BigDecimal getOtherCharges() {
		return otherCharges;
	}

	public void setOtherCharges(BigDecimal otherCharges) {
		this.otherCharges = otherCharges;
	}

	public LocalDate getDeliveredOn() {
		return deliveredOn;
	}

	public void setDeliveredOn(LocalDate deliveredOn) {
		this.deliveredOn = deliveredOn;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

}
