package com.liabletech.bff.contact;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

	private final ContactService contactService;

	public ContactController(ContactService contactService) {
		this.contactService = contactService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ContactResponse submit(@Valid @RequestBody ContactRequest request) {
		String reference = contactService.submit(request);
		return new ContactResponse(reference, "Thanks, we have your message and will reply within one business day.");
	}

}
