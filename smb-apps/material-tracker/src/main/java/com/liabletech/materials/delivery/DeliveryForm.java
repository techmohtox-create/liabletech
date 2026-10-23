package com.liabletech.materials.delivery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

/** The office's corrected version of a delivery: header and every line with the counted quantity. */
public class DeliveryForm {

	private Long siteId;

	private Long supplierId;

	private String challanNumber;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate challanDate;

	private String vehicleNumber;

	private String notes;

	private List<Line> lines = new ArrayList<>();

	public static DeliveryForm of(Delivery delivery, List<DeliveryLine> lines) {
		DeliveryForm form = new DeliveryForm();
		form.siteId = delivery.siteId();
		form.supplierId = delivery.supplierId();
		form.challanNumber = delivery.challanNumber();
		form.challanDate = delivery.challanDate();
		form.vehicleNumber = delivery.vehicleNumber();
		form.notes = delivery.notes();
		for (DeliveryLine line : lines) {
			Line l = new Line();
			l.setMaterialId(line.materialId());
			l.setDescription(line.description());
			l.setSlipQty(line.slipQty());
			l.setUnit(line.unit());
			l.setReceivedQty(line.receivedQty());
			form.lines.add(l);
		}
		return form;
	}

	public static class Line {

		private Long materialId;

		private String description;

		private BigDecimal slipQty;

		private String unit;

		private BigDecimal receivedQty;

		public boolean isBlank() {
			return (description == null || description.isBlank()) && materialId == null && slipQty == null;
		}

		public Long getMaterialId() {
			return materialId;
		}

		public void setMaterialId(Long materialId) {
			this.materialId = materialId;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public BigDecimal getSlipQty() {
			return slipQty;
		}

		public void setSlipQty(BigDecimal slipQty) {
			this.slipQty = slipQty;
		}

		public String getUnit() {
			return unit;
		}

		public void setUnit(String unit) {
			this.unit = unit;
		}

		public BigDecimal getReceivedQty() {
			return receivedQty;
		}

		public void setReceivedQty(BigDecimal receivedQty) {
			this.receivedQty = receivedQty;
		}

	}

	public Long getSiteId() {
		return siteId;
	}

	public void setSiteId(Long siteId) {
		this.siteId = siteId;
	}

	public Long getSupplierId() {
		return supplierId;
	}

	public void setSupplierId(Long supplierId) {
		this.supplierId = supplierId;
	}

	public String getChallanNumber() {
		return challanNumber;
	}

	public void setChallanNumber(String challanNumber) {
		this.challanNumber = challanNumber;
	}

	public LocalDate getChallanDate() {
		return challanDate;
	}

	public void setChallanDate(LocalDate challanDate) {
		this.challanDate = challanDate;
	}

	public String getVehicleNumber() {
		return vehicleNumber;
	}

	public void setVehicleNumber(String vehicleNumber) {
		this.vehicleNumber = vehicleNumber;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public List<Line> getLines() {
		return lines;
	}

	public void setLines(List<Line> lines) {
		this.lines = lines;
	}

}
