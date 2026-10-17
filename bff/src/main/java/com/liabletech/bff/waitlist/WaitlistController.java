package com.liabletech.bff.waitlist;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.bff.contact.ContactResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

	private final WaitlistService waitlistService;

	public WaitlistController(WaitlistService waitlistService) {
		this.waitlistService = waitlistService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ContactResponse join(@Valid @RequestBody WaitlistRequest request) {
		String reference = waitlistService.join(request);
		return new ContactResponse(reference, "You're on the list. We'll email you before early access opens.");
	}

}
