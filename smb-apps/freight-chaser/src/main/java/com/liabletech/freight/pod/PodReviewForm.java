package com.liabletech.freight.pod;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.liabletech.freight.trip.Trip;

/** The owner's check of a POD: corrected details, and the trip it belongs to (existing or new). */
public class PodReviewForm {

	private String lrNumber;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate lrDate;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate deliveryDate;

	private String consignor;

	private String consignee;

	private String origin;

	private String destination;

	private String vehicleNumber;

	private String packages;

	private boolean stampPresent;

	private boolean signaturePresent;

	private String remarks;

	/** An existing trip to attach to; null to create one from these details. */
	private Long tripId;

	private Long partyId;

	private BigDecimal freightAmount;

	public static PodReviewForm of(Pod pod) {
		PodReviewForm form = new PodReviewForm();
		form.lrNumber = pod.lrNumber();
		form.lrDate = pod.lrDate();
		form.deliveryDate = pod.deliveryDate();
		form.consignor = pod.consignor();
		form.consignee = pod.consignee();
		form.origin = pod.origin();
		form.destination = pod.destination();
		form.vehicleNumber = pod.vehicleNumber();
		form.packages = pod.packages();
		form.stampPresent = Boolean.TRUE.equals(pod.stampPresent());
		form.signaturePresent = Boolean.TRUE.equals(pod.signaturePresent());
		form.remarks = pod.remarks();
		form.tripId = pod.tripId();
		form.freightAmount = pod.freightAmount();
		return form;
	}

	/** Fills fields the photo did not give with what is already known about the trip. */
	void fillBlanksFrom(Trip trip, String vehicleRegistration) {
		lrNumber = lrNumber != null ? lrNumber : trip.lrNumber();
		lrDate = lrDate != null ? lrDate : trip.lrDate();
		origin = origin != null ? origin : trip.origin();
		destination = destination != null ? destination : trip.destination();
		consignee = consignee != null ? consignee : trip.consignee();
		vehicleNumber = vehicleNumber != null ? vehicleNumber : vehicleRegistration;
		freightAmount = freightAmount != null ? freightAmount : trip.freightAmount();
	}

	Pods.Details details() {
		return new Pods.Details(clean(lrNumber), lrDate, deliveryDate, clean(consignor), clean(consignee), clean(origin),
				clean(destination), clean(vehicleNumber), clean(packages), freightAmount, stampPresent, signaturePresent,
				clean(remarks));
	}

	private static String clean(String value) {
		return value == null || value.isBlank() ? null : value.trim();
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

	public LocalDate getDeliveryDate() {
		return deliveryDate;
	}

	public void setDeliveryDate(LocalDate deliveryDate) {
		this.deliveryDate = deliveryDate;
	}

	public String getConsignor() {
		return consignor;
	}

	public void setConsignor(String consignor) {
		this.consignor = consignor;
	}

	public String getConsignee() {
		return consignee;
	}

	public void setConsignee(String consignee) {
		this.consignee = consignee;
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

	public String getVehicleNumber() {
		return vehicleNumber;
	}

	public void setVehicleNumber(String vehicleNumber) {
		this.vehicleNumber = vehicleNumber;
	}

	public String getPackages() {
		return packages;
	}

	public void setPackages(String packages) {
		this.packages = packages;
	}

	public boolean isStampPresent() {
		return stampPresent;
	}

	public void setStampPresent(boolean stampPresent) {
		this.stampPresent = stampPresent;
	}

	public boolean isSignaturePresent() {
		return signaturePresent;
	}

	public void setSignaturePresent(boolean signaturePresent) {
		this.signaturePresent = signaturePresent;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	public Long getTripId() {
		return tripId;
	}

	public void setTripId(Long tripId) {
		this.tripId = tripId;
	}

	public Long getPartyId() {
		return partyId;
	}

	public void setPartyId(Long partyId) {
		this.partyId = partyId;
	}

	public BigDecimal getFreightAmount() {
		return freightAmount;
	}

	public void setFreightAmount(BigDecimal freightAmount) {
		this.freightAmount = freightAmount;
	}

}
