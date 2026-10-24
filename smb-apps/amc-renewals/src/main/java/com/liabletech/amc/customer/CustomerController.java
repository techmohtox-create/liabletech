package com.liabletech.amc.customer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.plan.Plans;
import com.liabletech.amc.reminder.ReminderService;
import com.liabletech.amc.request.RequestService;
import com.liabletech.amc.request.ServiceRequest;
import com.liabletech.amc.request.ServiceRequests;
import com.liabletech.amc.team.Technicians;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Phones;

@Controller
@RequestMapping("/customers")
public class CustomerController {

	private final Customers customers;

	private final Appliances appliances;

	private final Contracts contracts;

	private final Plans plans;

	private final ServiceRequests requests;

	private final RequestService requestService;

	private final ReminderService reminders;

	private final Technicians technicians;

	private final Outbox outbox;

	private final Clock clock;

	public CustomerController(Customers customers, Appliances appliances, Contracts contracts, Plans plans,
			ServiceRequests requests, RequestService requestService, ReminderService reminders, Technicians technicians,
			Outbox outbox, Clock clock) {
		this.customers = customers;
		this.appliances = appliances;
		this.contracts = contracts;
		this.plans = plans;
		this.requests = requests;
		this.requestService = requestService;
		this.reminders = reminders;
		this.technicians = technicians;
		this.outbox = outbox;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String q, Model model) {
		model.addAttribute("q", q);
		model.addAttribute("customers", customers.list(user.accountId(), q, 500));
		model.addAttribute("today", LocalDate.now(clock));
		return "amc/customers";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam String phone,
			@RequestParam(required = false) String name, @RequestParam(required = false) String area,
			@RequestParam(required = false) String address, RedirectAttributes redirect) {
		String normalized = Phones.normalize(phone);
		if (normalized == null) {
			redirect.addFlashAttribute("error", "Enter a valid mobile number.");
			return "redirect:/customers";
		}
		Customer customer = customers.findOrCreate(user.accountId(), normalized, name, OffsetDateTime.now(clock));
		customers.update(user.accountId(), customer.id(), name != null && !name.isBlank() ? name : customer.name(), address,
				area, customer.notes());
		return "redirect:/customers/" + customer.id();
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		long account = user.accountId();
		Customer customer = customers.get(account, id);
		List<Appliance> owned = appliances.forCustomer(id);
		List<Contract> all = contracts.forCustomer(id);
		Map<Long, Contract> current = new LinkedHashMap<>();
		for (Contract contract : all) {
			if (contract.open()) {
				current.putIfAbsent(contract.applianceId(), contract);
			}
		}
		model.addAttribute("customer", customer);
		model.addAttribute("appliances", owned);
		model.addAttribute("current", current);
		model.addAttribute("contracts", all);
		model.addAttribute("plans", plans.all(account).stream().filter((p) -> p.active()).toList());
		model.addAttribute("requests", requests.forCustomer(account, id));
		model.addAttribute("messages", outbox.whatsAppToPhone(account, customer.phone(), 30).reversed());
		model.addAttribute("technicians", technicians.all(account));
		model.addAttribute("kinds", ApplianceKind.values());
		model.addAttribute("links", all.stream().filter(Contract::open)
			.collect(java.util.stream.Collectors.toMap(Contract::id, reminders::link)));
		model.addAttribute("today", LocalDate.now(clock));
		return "amc/customer";
	}

	@PostMapping("/{id}")
	public String update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) String name, @RequestParam(required = false) String address,
			@RequestParam(required = false) String area, @RequestParam(required = false) String notes,
			RedirectAttributes redirect) {
		customers.get(user.accountId(), id);
		customers.update(user.accountId(), id, name, address, area, notes);
		redirect.addFlashAttribute("notice", "Saved.");
		return "redirect:/customers/" + id;
	}

	@PostMapping("/{id}/appliances")
	public String addAppliance(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String kind,
			@RequestParam(required = false) String brand, @RequestParam(required = false) String model,
			@RequestParam(required = false) String serialNumber, @RequestParam(required = false) String capacity,
			@RequestParam(required = false) String location, RedirectAttributes redirect) {
		customers.get(user.accountId(), id);
		ApplianceKind parsed = ApplianceKind.parse(kind);
		appliances.create(user.accountId(), id, parsed == null ? ApplianceKind.OTHER : parsed, brand, model, serialNumber,
				capacity, null, null, location, null, OffsetDateTime.now(clock));
		redirect.addFlashAttribute("notice", "Appliance added.");
		return "redirect:/customers/" + id;
	}

	@PostMapping("/{id}/opt-out")
	public String optOut(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(defaultValue = "false") boolean optedOut) {
		customers.get(user.accountId(), id);
		customers.setOptedOut(user.accountId(), id, optedOut);
		return "redirect:/customers/" + id;
	}

	@PostMapping("/{id}/requests")
	public String request(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) Long applianceId, @RequestParam(defaultValue = "SERVICE") String kind,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate preferredOn,
			@RequestParam(required = false) String note, RedirectAttributes redirect) {
		long account = user.accountId();
		Long contractId = applianceId == null ? null
				: contracts.forCustomer(id).stream()
					.filter((c) -> c.open() && c.applianceId().equals(applianceId))
					.map(Contract::id)
					.findFirst()
					.orElse(null);
		requestService.fromOffice(account, id, contractId, applianceId,
				ServiceRequest.REPAIR.equals(kind) ? ServiceRequest.REPAIR : ServiceRequest.SERVICE, preferredOn, note);
		redirect.addFlashAttribute("notice", "Visit request added. Schedule it under Requests.");
		return "redirect:/customers/" + id;
	}

}
