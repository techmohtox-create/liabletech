package com.liabletech.platform.whatsapp;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Phones;
import com.liabletech.platform.web.NotFoundException;

/**
 * A stand-in for a phone on WhatsApp, for trying the apps locally: pick a driver or mechanic, send
 * a photo or a text, and watch the app's replies arrive. Messages take exactly the same path as
 * real ones from the webhook. Only available in simulator mode.
 */
@Controller
@RequestMapping("/dev/whatsapp")
public class SimulatorController {

	/** The "number" simulated messages arrive on when a business has no number of its own. */
	public static final String PLATFORM_NUMBER = "simulator";

	private final WhatsAppProperties properties;

	private final ObjectProvider<SimulatorDirectory> directory;

	private final InboundMessages messages;

	private final InboundProcessor processor;

	private final Outbox outbox;

	private final FileStore files;

	private final Accounts accounts;

	private final Clock clock;

	public SimulatorController(WhatsAppProperties properties, ObjectProvider<SimulatorDirectory> directory,
			InboundMessages messages, InboundProcessor processor, Outbox outbox, FileStore files, Accounts accounts,
			Clock clock) {
		this.properties = properties;
		this.directory = directory;
		this.messages = messages;
		this.processor = processor;
		this.outbox = outbox;
		this.files = files;
		this.accounts = accounts;
		this.clock = clock;
	}

	@GetMapping
	public String page(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String phone,
			Model model) {
		requireSimulator();
		SimulatorDirectory dir = directory.getIfAvailable();
		List<SimulatorDirectory.Contact> contacts = dir == null ? List.of() : dir.contacts(user.accountId());
		String selected = Phones.normalize(phone);
		if (selected == null && !contacts.isEmpty()) {
			selected = contacts.getFirst().phone();
		}
		model.addAttribute("contacts", contacts);
		model.addAttribute("samples", dir == null ? List.of() : dir.samples(user.accountId()));
		model.addAttribute("phone", selected);
		String chosen = selected;
		model.addAttribute("contactName", contacts.stream()
			.filter((c) -> c.phone().equals(chosen))
			.map(SimulatorDirectory.Contact::name)
			.findFirst()
			.orElse(null));
		model.addAttribute("thread", selected == null ? List.of() : thread(user.accountId(), selected));
		return "platform/simulator";
	}

	/** Just the conversation, for the page to refresh while replies come in. */
	@GetMapping("/thread")
	public String thread(@AuthenticationPrincipal CurrentUser user, @RequestParam String phone, Model model) {
		requireSimulator();
		String normalized = Phones.normalize(phone);
		model.addAttribute("thread", normalized == null ? List.of() : thread(user.accountId(), normalized));
		return "platform/simulator :: thread";
	}

	@PostMapping
	public String send(@AuthenticationPrincipal CurrentUser user, @RequestParam String phone,
			@RequestParam(required = false) String text, @RequestParam(required = false) MultipartFile file,
			@RequestParam(required = false) String sample, @RequestParam(required = false) String name,
			RedirectAttributes redirect) throws IOException {
		requireSimulator();
		String from = Phones.normalize(phone);
		if (from == null) {
			redirect.addFlashAttribute("error", "Enter a valid mobile number to send from.");
			return "redirect:/dev/whatsapp";
		}
		String type = "text";
		String body = StringUtils.hasText(text) ? text.trim() : null;
		StoredFile stored = null;
		if (StringUtils.hasText(sample)) {
			SimulatorDirectory.Sample chosen = directory.getObject()
				.samples(user.accountId())
				.stream()
				.filter((s) -> s.id().equals(sample))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Sample " + sample));
			SimulatorDirectory.SampleFile generated = chosen.file().get();
			stored = files.save(user.accountId(), generated.bytes(), generated.contentType(), generated.filename());
			body = body != null ? body : chosen.caption();
		}
		else if (file != null && !file.isEmpty()) {
			try {
				stored = files.save(user.accountId(), file.getBytes(), file.getContentType(),
						file.getOriginalFilename());
			}
			catch (FileStore.UnsupportedFileException ex) {
				redirect.addFlashAttribute("error", ex.getMessage());
				return "redirect:/dev/whatsapp?phone=" + Phones.waId(from);
			}
		}
		if (stored != null) {
			type = stored.isAudio() ? "audio" : stored.isImage() ? "image" : "document";
		}
		else if (body == null) {
			return "redirect:/dev/whatsapp?phone=" + Phones.waId(from);
		}
		Account account = accounts.get(user.accountId());
		String toNumber = account.whatsappPhoneNumberId() != null ? account.whatsappPhoneNumberId() : PLATFORM_NUMBER;
		long id = messages
			.insertIfNew(new InboundMessages.NewInbound(null, "sim-" + UUID.randomUUID(), from, toNumber,
					StringUtils.hasText(name) ? name : null, type, body, null,
					stored == null ? null : stored.contentType(), stored == null ? null : stored.originalName(),
					stored == null ? null : stored.id(), null, OffsetDateTime.now(clock)))
			.orElseThrow();
		processor.dispatch(id, from);
		return "redirect:/dev/whatsapp?phone=" + Phones.waId(from);
	}

	/** Messages both ways with one phone. Unrouted messages (no business yet) show too: it is a local tool. */
	private List<Bubble> thread(long accountId, String phone) {
		List<Bubble> bubbles = new ArrayList<>();
		for (InboundMessage in : messages.recentFromPhone(accountId, phone, 60)) {
			bubbles.add(new Bubble(false, in.receivedAt(), in.textBody(), in.fileId(), in.messageType(),
					in.status(), in.error()));
		}
		for (OutboundMessage out : outbox.whatsAppToPhone(accountId, phone, 60)) {
			Long fileId = out.attachmentIds().isEmpty() ? null : out.attachmentIds().getFirst();
			bubbles.add(new Bubble(true, out.createdAt(), out.body(), fileId, fileId == null ? "text" : "document",
					out.status(), out.error()));
		}
		bubbles.sort(Comparator.comparing(Bubble::at));
		return bubbles;
	}

	private void requireSimulator() {
		if (!properties.simulator()) {
			throw new NotFoundException("Simulator");
		}
	}

	public record Bubble(boolean outgoing, OffsetDateTime at, String text, Long fileId, String type, String status,
			String error) {
	}

}
