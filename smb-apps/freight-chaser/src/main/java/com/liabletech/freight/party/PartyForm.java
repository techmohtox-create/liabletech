package com.liabletech.freight.party;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PartyForm {

	@NotBlank
	@Size(max = 150)
	private String name;

	@Size(max = 100)
	private String contactPerson;

	@Size(max = 20)
	private String phone;

	@Email
	@Size(max = 200)
	private String email;

	@Pattern(regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][0-9A-Za-z]{3}$", message = "is not a valid GSTIN")
	private String gstin;

	@Size(max = 500)
	private String address;

	@Min(0)
	@Max(365)
	private int creditDays = 30;

	public static PartyForm of(Party party) {
		PartyForm form = new PartyForm();
		form.name = party.name();
		form.contactPerson = party.contactPerson();
		form.phone = party.phone();
		form.email = party.email();
		form.gstin = party.gstin();
		form.address = party.address();
		form.creditDays = party.creditDays();
		return form;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContactPerson() {
		return contactPerson;
	}

	public void setContactPerson(String contactPerson) {
		this.contactPerson = contactPerson;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getGstin() {
		return gstin;
	}

	public void setGstin(String gstin) {
		this.gstin = gstin;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public int getCreditDays() {
		return creditDays;
	}

	public void setCreditDays(int creditDays) {
		this.creditDays = creditDays;
	}

}
