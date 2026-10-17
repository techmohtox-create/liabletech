package com.liabletech.bff.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
		@NotBlank(message = "Please tell us your name") @Size(max = 100) String name,
		@NotBlank(message = "Please enter your email") @Email(message = "Please enter a valid email") @Size(max = 200) String email,
		@Size(max = 150) String company,
		@Size(max = 100) String service,
		@NotBlank(message = "Please describe what you need") @Size(min = 10, max = 2000, message = "Message must be between 10 and 2000 characters") String message,
		// Optional answers from the project scoper on the contact page.
		@Size(max = 60) String stage,
		@Size(max = 60) String timeline,
		@Size(max = 60) String budget,
		// Page and campaign the visitor came from.
		@Size(max = 200) String source,
		// Honeypot: hidden in the form, so only bots fill it in.
		String website) {
}
