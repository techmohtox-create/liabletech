package com.liabletech.amc.whatsapp;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.liabletech.amc.contract.Contract;
import com.liabletech.amc.contract.Contracts;
import com.liabletech.amc.customer.Appliance;
import com.liabletech.amc.customer.Appliances;
import com.liabletech.amc.customer.Customer;
import com.liabletech.amc.customer.Customers;
import com.liabletech.amc.label.LabelScan;
import com.liabletech.amc.label.LabelService;
import com.liabletech.amc.reminder.ReminderService;
import com.liabletech.amc.request.RequestService;
import com.liabletech.amc.request.ServiceRequest;
import com.liabletech.amc.request.ServiceRequests;
import com.liabletech.amc.team.Technician;
import com.liabletech.amc.team.Technicians;
import com.liabletech.platform.account.Account;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.Outgoing;
import com.liabletech.platform.text.IndianMoney;
import com.liabletech.platform.text.Phones;
import com.liabletech.platform.whatsapp.InboundContext;
import com.liabletech.platform.whatsapp.InboundHandler;
import com.liabletech.platform.whatsapp.InboundMessage;
import com.liabletech.platform.whatsapp.InboundRouter;
import com.liabletech.platform.whatsapp.SimulatorDirectory;

/**
 * Two kinds of people write in. Technicians send label photos from customers' homes, with the
 * customer's number in the caption. Customers answer reminders: YES to renew, SERVICE for a visit,
 * STOP to stop. Anything else is passed to the owner.
 */
@Component
public class AmcWhatsApp implements InboundRouter, InboundHandler, SimulatorDirectory {

	static final String TECHNICIAN_HELP = """
			Send a clear photo of the appliance's rating label (AC outdoor or indoor unit, purifier, geyser). \
			In the caption write the customer's mobile number, and AMC with the amount if you sold one, e.g. \
			"9812345678 Sharma AMC 1499 bedroom".
			Type TODAY for your visits.""";

	private static final Set<String> YES = Set.of("yes", "y", "renew", "ok", "okay", "haan", "ha", "han", "1", "renewal",
			"confirm", "👍");

	private static final Set<String> SERVICE = Set.of("service", "book", "visit", "2", "servicing", "booking");

	private static final Set<String> STOP = Set.of("stop", "unsubscribe", "stop messages", "no more", "band karo");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private final Technicians technicians;

	private final Customers customers;

	private final Appliances appliances;

	private final Contracts contracts;

	private final LabelService labels;

	private final RequestService requestService;

	private final ServiceRequests requests;

	private final ReminderService reminders;

	private final Messenger messenger;

	private final Clock clock;

	public AmcWhatsApp(Technicians technicians, Customers customers, Appliances appliances, Contracts contracts,
			LabelService labels, RequestService requestService, ServiceRequests requests,
			ReminderService reminders, Messenger messenger, Clock clock) {
		this.technicians = technicians;
		this.customers = customers;
		this.appliances = appliances;
		this.contracts = contracts;
		this.labels = labels;
		this.requestService = requestService;
		this.requests = requests;
		this.reminders = reminders;
		this.messenger = messenger;
		this.clock = clock;
	}

	/** On the shared number: a technician's business, or the business that last messaged this customer. */
	@Override
	public Optional<Long> accountFor(String fromPhone) {
		Optional<Technician> technician = technicians.byPhone(fromPhone).filter(Technician::active);
		if (technician.isPresent()) {
			return Optional.of(technician.get().accountId());
		}
		return customers.accountsFor(fromPhone).stream().findFirst();
	}

	@Override
	public void handle(InboundContext context) {
		Optional<Technician> technician = technicians.byPhone(context.from())
			.filter((t) -> t.active() && t.accountId().equals(context.accountId()));
		if (technician.isPresent()) {
			fromTechnician(context, technician.get());
		}
		else {
			fromCustomer(context);
		}
	}

	private void fromTechnician(InboundContext context, Technician technician) {
		InboundMessage message = context.message();
		if (message.isPhotoOrPdf()) {
			Optional<StoredFile> file = context.file();
			if (file.isEmpty()) {
				context.reply("We could not open that photo. Please send it again.");
				return;
			}
			LabelService.Received received = labels.receive(context.accountId(), technician, file.get(), message.text());
			context.reply(labelReply(context.accountId(), received), "label", received.scan().id());
			return;
		}
		if (message.text().toLowerCase(Locale.ROOT).startsWith("today")) {
			context.reply(visitsFor(context.accountId(), technician));
			return;
		}
		context.reply(TECHNICIAN_HELP);
	}

	/** What the technician hears back about a label photo: saved, or what the office still needs. */
	public String labelReply(long accountId, LabelService.Received received) {
		var scan = received.scan();
		String what = String.join(" ", nonBlank(scan.brand(), scan.model())).trim();
		if (received.linkedNow()) {
			Appliance appliance = appliances.get(accountId, received.linked().applianceId());
			Customer customer = customers.get(accountId, received.linked().customerId());
			StringBuilder text = new StringBuilder("✅ Saved: ").append(appliance.describe());
			if (appliance.serialNumber() != null) {
				text.append(", SN ").append(appliance.serialNumber());
			}
			text.append(" for ").append(customer.displayName()).append(" (").append(Phones.pretty(customer.phone())).append(").");
			if (received.linked().contractId() != null) {
				Contract contract = contracts.get(accountId, received.linked().contractId());
				text.append("\nAMC: ").append(contract.planName()).append(", ").append(IndianMoney.rupees(contract.price()))
					.append(", until ").append(DATE.format(contract.endsOn())).append(' ').append(contract.endsOn().getYear())
					.append(contract.paid() ? ", paid." : ", payment pending.");
				text.append(" Renewal reminders will go to the customer automatically.");
			}
			return text.toString();
		}
		StringBuilder text = new StringBuilder("📷 Label received");
		if (!what.isEmpty()) {
			text.append(": ").append(what);
		}
		text.append(". ");
		if (LabelScan.MANUAL.equals(scan.readingStatus())) {
			text.append("It could not be read automatically; the office will type it in from the photo.");
		}
		else if (scan.customerPhone() == null) {
			text.append("Which customer is it? Next time put their mobile number in the caption, e.g. \"9812345678 AMC 1499\". The office will complete it.");
		}
		else {
			text.append("The office will check it and pick the AMC plan.");
		}
		return text.toString();
	}

	private void fromCustomer(InboundContext context) {
		InboundMessage message = context.message();
		long accountId = context.accountId();
		Account account = context.account();
		Optional<Customer> known = customers.byPhone(accountId, context.from());
		String text = message.text().toLowerCase(Locale.ROOT).replaceAll("[!.?]+$", "").trim();
		if (known.isEmpty()) {
			Customer customer = customers.findOrCreate(accountId, context.from(), message.contactName(),
					java.time.OffsetDateTime.now(clock));
			requestService.enquiry(accountId, customer.id(),
					message.isPhotoOrPdf() ? "Sent a photo" + (StringUtils.hasText(message.text()) ? ": " + message.text() : "")
							: message.text());
			context.reply(enquiryReply(account));
			return;
		}
		Customer customer = known.get();
		if (STOP.contains(text)) {
			customers.setOptedOut(accountId, customer.id(), true);
			context.reply("Done. You will not get reminders from " + account.name() + " any more. Reply START to get them again.");
			return;
		}
		if (text.equals("start")) {
			customers.setOptedOut(accountId, customer.id(), false);
			context.reply("Welcome back! You will get your AMC reminders again.");
			return;
		}
		Optional<Contract> contract = contracts.renewableFor(customer.id());
		if (YES.contains(text) && contract.isPresent()) {
			ServiceRequest request = requestService.forContract(contract.get(), ServiceRequest.RENEWAL, "WHATSAPP", null, null,
					null, null);
			context.reply(renewalNoted(accountId, contract.get()), "request", request.id());
			return;
		}
		if (SERVICE.contains(text) && contract.isPresent()) {
			ServiceRequest request = requestService.forContract(contract.get(), ServiceRequest.SERVICE, "WHATSAPP", null, null,
					null, null);
			context.reply("Thank you! We will call you to fix a time. Or pick a day yourself: " + reminders.link(contract.get()),
					"request", request.id());
			return;
		}
		forwardToOwner(context, account, customer);
		context.reply("Thank you, we have passed your message on and will get back to you soon."
				+ (contract.isPresent() ? " Reply YES to renew your AMC, or SERVICE to book a visit." : ""));
	}

	/** The answer to a YES: the renewal is noted, with the price and the link to pay or book. */
	public String renewalNoted(long accountId, Contract contract) {
		Appliance appliance = appliances.get(accountId, contract.applianceId());
		return "🙏 Thank you! We have noted your renewal of " + contract.planName() + " for the " + appliance.describe()
				+ " (" + IndianMoney.rupees(reminders.renewalPrice(accountId, contract))
				+ "). We will confirm shortly.\nYou can also pay or pick a service day here: " + reminders.link(contract);
	}

	/** The answer to someone who is not a customer yet. */
	public static String enquiryReply(Account account) {
		return "Namaste! Thank you for writing to " + account.name()
				+ ". Tell us the appliance and your area, and we will call you back to fix a visit.";
	}

	private void forwardToOwner(InboundContext context, Account account, Customer customer) {
		String owner = Phones.normalize(account.phone());
		if (owner == null || owner.equals(customer.phone())) {
			return;
		}
		String body = context.message().isPhotoOrPdf() ? "(sent a photo)" : context.message().text();
		messenger.send(Outgoing.whatsApp(account.id(), owner, "💬 " + customer.displayName() + " ("
				+ Phones.pretty(customer.phone()) + "): " + body)
			.about("customer", customer.id())
			.template("amc_customer_message", customer.displayName(), body));
	}

	private String visitsFor(long accountId, Technician technician) {
		LocalDate today = LocalDate.now(clock);
		List<ServiceRequests.RequestRow> visits = requests.list(accountId, List.of(ServiceRequest.SCHEDULED), 200)
			.stream()
			.filter((r) -> technician.name().equals(r.technicianName()) && r.scheduledOn() != null
					&& !r.scheduledOn().isAfter(today.plusDays(1)))
			.toList();
		if (visits.isEmpty()) {
			return "No visits booked for you today or tomorrow.";
		}
		StringBuilder text = new StringBuilder("Your visits:");
		for (ServiceRequests.RequestRow visit : visits) {
			text.append("\n• ").append(visit.scheduledOn().equals(today) ? "Today" : "Tomorrow").append(": ")
				.append(visit.customerName() == null ? Phones.pretty(visit.phone()) : visit.customerName())
				.append(visit.area() == null ? "" : ", " + visit.area()).append(" – ")
				.append(visit.appliance() == null ? "visit" : visit.appliance()).append(" · ").append(Phones.pretty(visit.phone()));
		}
		return text.toString();
	}

	@Override
	public String replyToUnknownSender(InboundMessage message) {
		return "This number is not registered with any service business here. Please call your AC or RO service provider directly.";
	}

	@Override
	public List<Contact> contacts(long accountId) {
		List<Contact> contacts = new ArrayList<>();
		for (Technician technician : technicians.all(accountId)) {
			if (technician.active()) {
				contacts.add(new Contact(technician.name(), technician.phone(), "Technician"));
			}
		}
		LocalDate today = LocalDate.now(clock);
		for (Contract contract : contracts.endingBetween(accountId, today.minusDays(30), today.plusDays(30))) {
			Customer customer = customers.get(accountId, contract.customerId());
			if (contacts.stream().noneMatch((c) -> c.phone().equals(customer.phone()))) {
				contacts.add(new Contact(customer.displayName(), customer.phone(),
						"Customer · AMC " + contract.dueText(today)));
			}
			if (contacts.size() >= 8) {
				break;
			}
		}
		return contacts;
	}

	@Override
	public List<Sample> samples(long accountId) {
		LocalDate today = LocalDate.now(clock);
		String suffix = String.valueOf(Math.floorMod(accountId * 31 + today.getDayOfYear(), 900) + 100);
		return List.of(
				new Sample("split-ac", "AC outdoor unit label", "9822012" + suffix + " Kulkarni AMC 1499 bedroom",
						() -> new SampleFile(LabelSamples.splitAc("Voltas", "183V CZTT", "4B1206" + suffix + "21", "1.5 TR",
								"R32", "02/2023"), "image/jpeg", "label.jpg")),
				new Sample("ro", "RO purifier label", "9890112" + suffix + " no amc",
						() -> new SampleFile(LabelSamples.ro("Kent", "Grand Plus", "KGP" + suffix + "7781", "11/2022"),
								"image/jpeg", "ro-label.jpg")));
	}

	private static List<String> nonBlank(String... values) {
		List<String> out = new ArrayList<>();
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				out.add(value);
			}
		}
		return out;
	}

}
