package com.liabletech.platform.account;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.liabletech.platform.text.Phones;

/** The business details that appear on invoices and messages: name, GSTIN, bank and UPI. */
public class ProfileForm {

	@NotBlank
	@Size(max = 150)
	private String name;

	@Size(max = 100)
	private String ownerName;

	@Size(max = 20)
	private String phone;

	@Email
	@Size(max = 200)
	private String email;

	@Size(max = 500)
	private String address;

	@Pattern(regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][0-9A-Za-z]{3}$", message = "is not a valid GSTIN")
	private String gstin;

	@Pattern(regexp = "^$|^[A-Za-z]{5}[0-9]{4}[A-Za-z]$", message = "is not a valid PAN")
	private String pan;

	@Pattern(regexp = "^$|^[\\w.\\-]{2,}@[A-Za-z]{2,}$", message = "is not a valid UPI ID")
	private String upiId;

	@Size(max = 150)
	private String bankAccountName;

	@Pattern(regexp = "^$|^[0-9]{6,20}$", message = "should be 6 to 20 digits")
	private String bankAccountNumber;

	@Pattern(regexp = "^$|^[A-Za-z]{4}0[A-Za-z0-9]{6}$", message = "is not a valid IFSC")
	private String bankIfsc;

	@Size(max = 100)
	private String bankName;

	public static ProfileForm of(Account account) {
		ProfileForm form = new ProfileForm();
		form.name = account.name();
		form.ownerName = account.ownerName();
		form.phone = account.phone();
		form.email = account.email();
		form.address = account.address();
		form.gstin = account.gstin();
		form.pan = account.pan();
		form.upiId = account.upiId();
		form.bankAccountName = account.bankAccountName();
		form.bankAccountNumber = account.bankAccountNumber();
		form.bankIfsc = account.bankIfsc();
		form.bankName = account.bankName();
		return form;
	}

	String normalizedPhone() {
		String normalized = Phones.normalize(phone);
		return normalized != null ? normalized : clean(phone);
	}

	static String clean(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	static String upper(String value) {
		String cleaned = clean(value);
		return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public void setOwnerName(String ownerName) {
		this.ownerName = ownerName;
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

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getGstin() {
		return gstin;
	}

	public void setGstin(String gstin) {
		this.gstin = gstin;
	}

	public String getPan() {
		return pan;
	}

	public void setPan(String pan) {
		this.pan = pan;
	}

	public String getUpiId() {
		return upiId;
	}

	public void setUpiId(String upiId) {
		this.upiId = upiId;
	}

	public String getBankAccountName() {
		return bankAccountName;
	}

	public void setBankAccountName(String bankAccountName) {
		this.bankAccountName = bankAccountName;
	}

	public String getBankAccountNumber() {
		return bankAccountNumber;
	}

	public void setBankAccountNumber(String bankAccountNumber) {
		this.bankAccountNumber = bankAccountNumber;
	}

	public String getBankIfsc() {
		return bankIfsc;
	}

	public void setBankIfsc(String bankIfsc) {
		this.bankIfsc = bankIfsc;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

}
