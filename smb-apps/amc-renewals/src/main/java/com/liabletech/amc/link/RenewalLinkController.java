package com.liabletech.amc.link;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.plan.AmcPlan;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.reminder.ReminderService;
import com.liabletech.amc.request.RequestService;
import com.liabletech.amc.request.ServiceRequest;
import com.liabletech.amc.request.ServiceRequests;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.web.NotFoundException;

/**
 * The link in every reminder. The customer sees their appliance and contract, picks a plan to renew
 * (and pays by UPI if the business has a UPI ID) or picks a day for a service visit. No login: the
 * link's random token is the key, and it only ever shows this one contract.
 */
@Controller
@RequestMapping("/p/renew/{token}")
public class RenewalLinkController {

	static final List<String> SLOTS = List.of("Morning (9 am–12 pm)", "Afternoon (12–4 pm)", "Evening (4–7 pm)");

	/** How far ahead a customer may pick a visit day. */
	static final int BOOKING_DAYS = 45;

	private final Contracts contracts;

	private final Customers customers;

	private final Appliances appliances;

	private final Plans plans;

	private final ServiceRequests requests;

	private final RequestService requestService;

	private final ReminderService reminders;

	private final Accounts accounts;

	private final Clock clock;

	public RenewalLinkController(Contracts contracts, Customers customers, Appliances appliances, Plans plans,
			ServiceRequests requests, RequestService requestService, ReminderService reminders, Accounts accounts,
			Clock clock) {
		this.contracts = contracts;
		this.customers = customers;
		this.appliances = appliances;
		this.plans = plans;
		this.requests = requests;
		this.requestService = requestService;
		this.reminders = reminders;
		this.accounts = accounts;
		this.clock = clock;
	}

	@GetMapping
	public String page(@PathVariable String token, @RequestParam(required = false) String done, Model model) {
		Contract contract = contract(token);
		if (Contract.RENEWED.equals(contract.status()) && contract.renewedById() != null) {
			Optional<Contract> renewed = contracts.find(contract.accountId(), contract.renewedById());
			if (renewed.isPresent()) {
				return "redirect:/p/renew/" + renewed.get().renewalToken() + (done != null ? "?done=" + done : "");
			}
		}
		long accountId = contract.accountId();
		Account account = accounts.get(accountId);
		Appliance appliance = appliances.get(accountId, contract.applianceId());
		LocalDate today = LocalDate.now(clock);
		List<AmcPlan> options = plans.forFamily(accountId, appliance.applianceKind().family());
		Optional<ServiceRequest> renewal = requests.openFor(contract.id(), ServiceRequest.RENEWAL);
		Optional<ServiceRequest> visit = requests.openFor(contract.id(), ServiceRequest.SERVICE);
		BigDecimal price = renewal.flatMap((r) -> plans.find(accountId, r.planId()))
			.map(AmcPlan::price)
			.orElse(reminders.renewalPrice(accountId, contract));
		model.addAttribute("account", account);
		model.addAttribute("contract", contract);
		model.addAttribute("customer", customers.get(accountId, contract.customerId()));
		model.addAttribute("appliance", appliance);
		model.addAttribute("plans", options);
		model.addAttribute("currentPlanId", options.stream().anyMatch((p) -> p.id().equals(contract.planId()))
				? contract.planId() : options.isEmpty() ? null : options.getFirst().id());
		model.addAttribute("renewalPrice", price);
		model.addAttribute("renewal", renewal.orElse(null));
		model.addAttribute("renewalPlan", renewal.flatMap((r) -> plans.find(accountId, r.planId())).orElse(null));
		model.addAttribute("visit", visit.orElse(null));
		model.addAttribute("visitsDone", requests.visitsDone(contract.id()));
		model.addAttribute("renewable", contract.open() && contract.daysLeft(today) <= 60);
		model.addAttribute("upi", upiLink(account, price, appliance));
		model.addAttribute("slots", SLOTS);
		model.addAttribute("earliest", today.plusDays(1));
		model.addAttribute("latest", today.plusDays(BOOKING_DAYS));
		model.addAttribute("today", today);
		model.addAttribute("done", done);
		return "amc/public-renew";
	}

	@PostMapping("/renew")
	public String renew(@PathVariable String token, @RequestParam(required = false) Long planId,
			@RequestParam(required = false) String note) {
		Contract contract = contract(token);
		if (!contract.open()) {
			return "redirect:/p/renew/" + token;
		}
		Appliance appliance = appliances.get(contract.accountId(), contract.applianceId());
		Long chosen = plans.forFamily(contract.accountId(), appliance.applianceKind().family())
			.stream()
			.map(AmcPlan::id)
			.filter((id) -> id.equals(planId))
			.findFirst()
			.orElse(null);
		requestService.forContract(contract, ServiceRequest.RENEWAL, "LINK", chosen, null, null, cut(note));
		return "redirect:/p/renew/" + token + "?done=renewal";
	}

	@PostMapping("/service")
	public String service(@PathVariable String token,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate preferredOn,
			@RequestParam(required = false) String slot, @RequestParam(required = false) String note) {
		Contract contract = contract(token);
		if (!contract.open()) {
			return "redirect:/p/renew/" + token;
		}
		LocalDate today = LocalDate.now(clock);
		LocalDate day = preferredOn == null || preferredOn.isBefore(today.plusDays(1))
				|| preferredOn.isAfter(today.plusDays(BOOKING_DAYS)) ? null : preferredOn;
		String chosenSlot = SLOTS.contains(slot) ? slot.substring(0, slot.indexOf(' ')) : null;
		requestService.forContract(contract, ServiceRequest.SERVICE, "LINK", null, day, chosenSlot, cut(note));
		return "redirect:/p/renew/" + token + "?done=service";
	}

	@PostMapping("/stop")
	public String stop(@PathVariable String token) {
		Contract contract = contract(token);
		customers.setOptedOut(contract.accountId(), contract.customerId(), true);
		return "redirect:/p/renew/" + token + "?done=stopped";
	}

	@PostMapping("/start")
	public String start(@PathVariable String token) {
		Contract contract = contract(token);
		customers.setOptedOut(contract.accountId(), contract.customerId(), false);
		return "redirect:/p/renew/" + token + "?done=started";
	}

	private Contract contract(String token) {
		return contracts.byToken(token).orElseThrow(() -> new NotFoundException("Renewal link"));
	}

	/**
	 * A UPI payment link (opens GPay, PhonePe, Paytm or the bank's app on the phone) for the renewal
	 * amount, or null if the business has not added its UPI ID.
	 */
	public static String upiLink(Account account, BigDecimal amount, Appliance appliance) {
		if (!StringUtils.hasText(account.upiId()) || amount == null) {
			return null;
		}
		// A UPI ID is letters, digits, dots, hyphens and one @ (Settings checks it), so it goes in as it is.
		return "upi://pay?pa=" + account.upiId().trim() + "&pn=" + encode(account.name()) + "&am="
				+ amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "&cu=INR&tn="
				+ encode("AMC renewal " + appliance.applianceKind().label());
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String cut(String note) {
		if (note == null || note.isBlank()) {
			return null;
		}
		String trimmed = note.trim();
		return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
	}

}
