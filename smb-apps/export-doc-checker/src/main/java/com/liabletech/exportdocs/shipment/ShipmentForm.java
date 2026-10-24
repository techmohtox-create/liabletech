package com.liabletech.exportdocs.shipment;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.liabletech.platform.text.Phones;

/** What the exporter adds to a shipment besides its documents: contacts and the presentation date. */
public class ShipmentForm {

	private String title;

	private String buyerEmail;

	private String forwarderName;

	private String forwarderPhone;

	private String forwarderEmail;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate plannedPresentation;

	private String notes;

	public static ShipmentForm of(Shipment shipment) {
		ShipmentForm form = new ShipmentForm();
		form.title = shipment.title();
		form.buyerEmail = shipment.buyerEmail();
		form.forwarderName = shipment.forwarderName();
		form.forwarderPhone = shipment.forwarderPhone();
		form.forwarderEmail = shipment.forwarderEmail();
		form.plannedPresentation = shipment.plannedPresentation();
		form.notes = shipment.notes();
		return form;
	}

	public String normalizedForwarderPhone() {
		if (forwarderPhone == null || forwarderPhone.isBlank()) {
			return null;
		}
		String normalized = Phones.normalize(forwarderPhone);
		return normalized != null ? normalized : forwarderPhone.trim();
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBuyerEmail() {
		return buyerEmail;
	}

	public void setBuyerEmail(String buyerEmail) {
		this.buyerEmail = buyerEmail;
	}

	public String getForwarderName() {
		return forwarderName;
	}

	public void setForwarderName(String forwarderName) {
		this.forwarderName = forwarderName;
	}

	public String getForwarderPhone() {
		return forwarderPhone;
	}

	public void setForwarderPhone(String forwarderPhone) {
		this.forwarderPhone = forwarderPhone;
	}

	public String getForwarderEmail() {
		return forwarderEmail;
	}

	public void setForwarderEmail(String forwarderEmail) {
		this.forwarderEmail = forwarderEmail;
	}

	public LocalDate getPlannedPresentation() {
		return plannedPresentation;
	}

	public void setPlannedPresentation(LocalDate plannedPresentation) {
		this.plannedPresentation = plannedPresentation;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

}
