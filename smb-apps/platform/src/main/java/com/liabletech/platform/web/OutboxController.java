package com.liabletech.platform.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;

/** Everything the app has sent on the business's behalf, newest first. */
@Controller
public class OutboxController {

	private final Outbox outbox;

	public OutboxController(Outbox outbox) {
		this.outbox = outbox;
	}

	@GetMapping("/outbox")
	public String outbox(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String channel,
			Model model) {
		String filter = "WHATSAPP".equals(channel) || "EMAIL".equals(channel) ? channel : null;
		model.addAttribute("channel", filter);
		model.addAttribute("messages", outbox.forAccount(user.accountId(), filter, 200));
		return "platform/outbox";
	}

}
