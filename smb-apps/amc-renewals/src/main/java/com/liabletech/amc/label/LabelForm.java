package com.liabletech.amc.label;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.Customer;

/** The office's corrected label, the customer it belongs to and the AMC sold with it. */
public class LabelForm {

	private String kind;

	private String brand;

	private String model;

	private String serialNumber;

	private String capacity;

	private String refrigerant;

	private String manufactured;

	private String location;

	private String customerPhone;

	private String customerName;

	private String address;

	private String area;

	private Long planId;

	private BigDecimal price;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startsOn;

	private boolean paid;

	private String paymentMode = "CASH";

	public static LabelForm of(LabelScan scan, CaptionParser.Caption caption, Long planId) {
		LabelForm form = new LabelForm();
		form.kind = scan.kind();
		form.brand = scan.brand();
		form.model = scan.model();
		form.serialNumber = scan.serialNumber();
		form.capacity = scan.capacity();
		form.refrigerant = scan.refrigerant();
		form.manufactured = scan.manufactured();
		form.location = caption.location();
		form.customerPhone = scan.customerPhone();
		form.customerName = scan.customerName();
		form.planId = planId;
		form.price = scan.amount();
		form.paid = caption.paid();
		return form;
	}

	/** The form for a label already saved: what the appliance and customer hold now. */
	public static LabelForm of(Appliance appliance, Customer customer) {
		LabelForm form = new LabelForm();
		form.kind = appliance.kind();
		form.brand = appliance.brand();
		form.model = appliance.model();
		form.serialNumber = appliance.serialNumber();
		form.capacity = appliance.capacity();
		form.refrigerant = appliance.refrigerant();
		form.manufactured = appliance.manufactured();
		form.location = appliance.location();
		form.customerPhone = customer.phone();
		form.customerName = customer.name();
		form.address = customer.address();
		form.area = customer.area();
		return form;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getSerialNumber() {
		return serialNumber;
	}

	public void setSerialNumber(String serialNumber) {
		this.serialNumber = serialNumber;
	}

	public String getCapacity() {
		return capacity;
	}

	public void setCapacity(String capacity) {
		this.capacity = capacity;
	}

	public String getRefrigerant() {
		return refrigerant;
	}

	public void setRefrigerant(String refrigerant) {
		this.refrigerant = refrigerant;
	}

	public String getManufactured() {
		return manufactured;
	}

	public void setManufactured(String manufactured) {
		this.manufactured = manufactured;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getCustomerPhone() {
		return customerPhone;
	}

	public void setCustomerPhone(String customerPhone) {
		this.customerPhone = customerPhone;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

	public Long getPlanId() {
		return planId;
	}

	public void setPlanId(Long planId) {
		this.planId = planId;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public LocalDate getStartsOn() {
		return startsOn;
	}

	public void setStartsOn(LocalDate startsOn) {
		this.startsOn = startsOn;
	}

	public boolean isPaid() {
		return paid;
	}

	public void setPaid(boolean paid) {
		this.paid = paid;
	}

	public String getPaymentMode() {
		return paymentMode;
	}

	public void setPaymentMode(String paymentMode) {
		this.paymentMode = paymentMode;
	}

}
