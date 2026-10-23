package com.liabletech.platform.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SignupForm {

	@NotBlank
	@Size(max = 150)
	private String businessName;

	@NotBlank
	@Size(max = 100)
	private String ownerName;

	@NotBlank
	@Size(max = 20)
	private String phone;

	@NotBlank
	@Email
	@Size(max = 200)
	private String email;

	@NotBlank
	@Size(min = 8, max = 100, message = "must be at least 8 characters")
	private String password;

	public String getBusinessName() {
		return businessName;
	}

	public void setBusinessName(String businessName) {
		this.businessName = businessName;
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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}
