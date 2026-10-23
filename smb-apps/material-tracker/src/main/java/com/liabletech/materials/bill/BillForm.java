package com.liabletech.materials.bill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

/** The office's corrected version of a supplier bill. */
public class BillForm {

	private Long siteId;

	private Long supplierId;

	private String billNumber;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate billDate;

	/** Challan numbers the bill quotes, comma separated. */
	private String challanRefs;

	private BigDecimal taxableAmount;

	private BigDecimal taxAmount;

	private BigDecimal totalAmount;

	private List<Line> lines = new ArrayList<>();

	public static BillForm of(Bill bill, List<BillLine> lines) {
		BillForm form = new BillForm();
		form.siteId = bill.siteId();
		form.supplierId = bill.supplierId();
		form.billNumber = bill.billNumber();
		form.billDate = bill.billDate();
		form.challanRefs = bill.challanRefs();
		form.taxableAmount = bill.taxableAmount();
		form.taxAmount = bill.taxAmount();
		form.totalAmount = bill.totalAmount();
		for (BillLine line : lines) {
			Line l = new Line();
			l.setMaterialId(line.materialId());
			l.setDescription(line.description());
			l.setQty(line.qty());
			l.setUnit(line.unit());
			l.setRate(line.rate());
			l.setAmount(line.amount());
			l.setChallanRef(line.challanRef());
			form.lines.add(l);
		}
		return form;
	}

	public static class Line {

		private Long materialId;

		private String description;

		private BigDecimal qty;

		private String unit;

		private BigDecimal rate;

		private BigDecimal amount;

		private String challanRef;

		public boolean isBlank() {
			return (description == null || description.isBlank()) && materialId == null && qty == null && amount == null;
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

		public BigDecimal getQty() {
			return qty;
		}

		public void setQty(BigDecimal qty) {
			this.qty = qty;
		}

		public String getUnit() {
			return unit;
		}

		public void setUnit(String unit) {
			this.unit = unit;
		}

		public BigDecimal getRate() {
			return rate;
		}

		public void setRate(BigDecimal rate) {
			this.rate = rate;
		}

		public BigDecimal getAmount() {
			return amount;
		}

		public void setAmount(BigDecimal amount) {
			this.amount = amount;
		}

		public String getChallanRef() {
			return challanRef;
		}

		public void setChallanRef(String challanRef) {
			this.challanRef = challanRef;
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

	public String getBillNumber() {
		return billNumber;
	}

	public void setBillNumber(String billNumber) {
		this.billNumber = billNumber;
	}

	public LocalDate getBillDate() {
		return billDate;
	}

	public void setBillDate(LocalDate billDate) {
		this.billDate = billDate;
	}

	public String getChallanRefs() {
		return challanRefs;
	}

	public void setChallanRefs(String challanRefs) {
		this.challanRefs = challanRefs;
	}

	public BigDecimal getTaxableAmount() {
		return taxableAmount;
	}

	public void setTaxableAmount(BigDecimal taxableAmount) {
		this.taxableAmount = taxableAmount;
	}

	public BigDecimal getTaxAmount() {
		return taxAmount;
	}

	public void setTaxAmount(BigDecimal taxAmount) {
		this.taxAmount = taxAmount;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public List<Line> getLines() {
		return lines;
	}

	public void setLines(List<Line> lines) {
		this.lines = lines;
	}

}
