package com.liabletech.bff.contact;

import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ContactService {

	private static final Logger log = LoggerFactory.getLogger(ContactService.class);

	public String submit(ContactRequest request) {
		String reference = "LT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		if (StringUtils.hasText(request.website())) {
			log.debug("Dropped contact enquiry {} (honeypot filled)", reference);
			return reference;
		}
		// TODO: deliver to the sales inbox / CRM. Until then enquiries only exist in this log.
		log.info("Contact enquiry {} from {} <{}> company={} service={} message={}", reference,
				singleLine(request.name()), singleLine(request.email()), singleLine(request.company()),
				singleLine(request.service()), singleLine(request.message()));
		return reference;
	}

	private static String singleLine(String value) {
		return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
	}

}
