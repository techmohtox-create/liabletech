package com.liabletech.parts.enquiry;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.liabletech.parts.PartsNavigation;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.security.CurrentUser;

/** The counter: enquiries waiting for an answer, newest arrivals refreshed every few seconds. */
@Controller
public class InboxController {

	static final int CANDIDATES_SHOWN = 4;

	private final Enquiries enquiries;

	private final EnquiryService service;

	private final Accounts accounts;

	private final Clock clock;

	public InboxController(Enquiries enquiries, EnquiryService service, Accounts accounts, Clock clock) {
		this.enquiries = enquiries;
		this.service = service;
		this.accounts = accounts;
		this.clock = clock;
	}

	@GetMapping("/inbox")
	public String inbox(@AuthenticationPrincipal CurrentUser user, Model model) {
		long account = user.accountId();
		waiting(account, model);
		model.addAttribute("quoted", enquiries.list(account, List.of(Enquiry.QUOTED), null, 20));
		model.addAttribute("answered", enquiries.list(account, List.of(Enquiry.BOOKED, Enquiry.NOT_AVAILABLE, Enquiry.CLOSED),
				null, 15));
		model.addAttribute("pro", PartsNavigation.isPro(accounts.get(account)));
		return "parts/inbox";
	}

	@GetMapping("/inbox/live")
	public String live(@AuthenticationPrincipal CurrentUser user, Model model) {
		waiting(user.accountId(), model);
		return "parts/inbox :: cards";
	}

	private void waiting(long account, Model model) {
		List<Card> cards = enquiries.list(account, List.of(Enquiry.NEW), null, 40)
			.stream()
			.map((row) -> new Card(row, service.candidates(enquiries.get(account, row.id()))
				.stream()
				.limit(CANDIDATES_SHOWN)
				.toList()))
			.toList();
		model.addAttribute("cards", cards);
		model.addAttribute("now", OffsetDateTime.now(clock));
	}

	/** An enquiry at the counter with the parts that could answer it. */
	public record Card(Enquiries.EnquiryRow enquiry, List<PartMatcher.Candidate> candidates) {
	}

}
