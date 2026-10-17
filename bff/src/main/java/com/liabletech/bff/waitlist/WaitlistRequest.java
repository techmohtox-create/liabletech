package com.liabletech.bff.waitlist;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WaitlistRequest(
		@Size(max = 100) String name,
		@NotBlank(message = "Please enter your email") @Email(message = "Please enter a valid email") @Size(max = 200) String email,
		@Size(max = 150) String company,
		@NotBlank @Pattern(regexp = "medflow-ai|erp|bazaarflow", message = "Unknown product") String product,
		// Honeypot: hidden in the form, so only bots fill it in.
		String website) {
}
