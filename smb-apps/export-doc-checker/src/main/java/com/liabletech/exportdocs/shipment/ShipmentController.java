package com.liabletech.exportdocs.shipment;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.exportdocs.check.CheckService;
import com.liabletech.exportdocs.check.Finding;
import com.liabletech.exportdocs.check.Findings;
import com.liabletech.exportdocs.check.Fixer;
import com.liabletech.exportdocs.check.LcTerms;
import com.liabletech.exportdocs.document.DocData;
import com.liabletech.exportdocs.document.DocType;
import com.liabletech.exportdocs.document.ShipmentDoc;
import com.liabletech.exportdocs.document.ShipmentDocs;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.report.FixMessages;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;

@Controller
@RequestMapping("/shipments")
public class ShipmentController {

	private final Shipments shipments;

	private final ShipmentService service;

	private final ShipmentDocs docs;

	private final DocData data;

	private final Findings findings;

	private final CheckService checks;

	private final FileStore files;

	private final Outbox outbox;

	private final DocumentAi ai;

	private final Clock clock;

	public ShipmentController(Shipments shipments, ShipmentService service, ShipmentDocs docs, DocData data,
			Findings findings, CheckService checks, FileStore files, Outbox outbox, DocumentAi ai, Clock clock) {
		this.shipments = shipments;
		this.service = service;
		this.docs = docs;
		this.data = data;
		this.findings = findings;
		this.checks = checks;
		this.files = files;
		this.outbox = outbox;
		this.ai = ai;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "active") String show,
			Model model) {
		model.addAttribute("show", show);
		model.addAttribute("shipments", shipments.list(user.accountId(), !"all".equals(show), 300));
		model.addAttribute("today", LocalDate.now(clock));
		return "export/shipments";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String title) {
		long id = service.create(user.accountId(), title);
		return "redirect:/shipments/" + id;
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		Shipment shipment = shipments.get(user.accountId(), id);
		List<ShipmentDoc> all = docs.forShipment(id);
		Map<String, ShipmentDoc> byType = new LinkedHashMap<>();
		for (ShipmentDoc doc : all) {
			if (doc.type() != DocType.OTHER) {
				byType.put(doc.docType(), doc);
			}
		}
		LcTerms terms = byType.containsKey("LC") && data.read(byType.get("LC")) instanceof LcReading lc ? LcTerms.of(lc)
				: null;
		List<Finding> list = findings.forShipment(id);
		model.addAttribute("shipment", shipment);
		model.addAttribute("form", ShipmentForm.of(shipment));
		model.addAttribute("docs", byType);
		model.addAttribute("others", all.stream().filter((d) -> d.type() == DocType.OTHER).toList());
		model.addAttribute("slots", DocType.read());
		model.addAttribute("types", DocType.values());
		model.addAttribute("terms", terms);
		model.addAttribute("missing", CheckService.missingDocuments(terms, byType.keySet().stream()
			.map(DocType::parse)
			.collect(java.util.stream.Collectors.toMap((t) -> t, (t) -> Boolean.TRUE))));
		model.addAttribute("open", list.stream().filter(Finding::open).toList());
		model.addAttribute("accepted", list.stream().filter((f) -> Finding.ACCEPTED.equals(f.status())).toList());
		model.addAttribute("fixed", list.stream().filter((f) -> Finding.FIXED.equals(f.status())).toList());
		model.addAttribute("counts", findings.openCounts(id));
		model.addAttribute("forwarderFixes", FixMessages.forFixer(list, Fixer.FORWARDER).size());
		model.addAttribute("buyerFixes", FixMessages.forFixer(list, Fixer.BUYER).size());
		model.addAttribute("messages", outbox.about(user.accountId(), "shipment", id));
		model.addAttribute("today", LocalDate.now(clock));
		model.addAttribute("aiEnabled", ai.available());
		return "export/shipment";
	}

	@PostMapping("/{id}")
	public String save(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@ModelAttribute("form") ShipmentForm form, RedirectAttributes redirect) {
		shipments.get(user.accountId(), id);
		shipments.updateDetails(user.accountId(), id, form, java.time.OffsetDateTime.now(clock));
		checks.check(user.accountId(), id);
		redirect.addFlashAttribute("notice", "Saved.");
		return "redirect:/shipments/" + id;
	}

	@PostMapping("/{id}/documents")
	public String upload(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) String type, @RequestParam MultipartFile file, RedirectAttributes redirect)
			throws IOException {
		if (file.isEmpty()) {
			redirect.addFlashAttribute("error", "Choose a PDF or photo of the document.");
			return "redirect:/shipments/" + id;
		}
		shipments.get(user.accountId(), id);
		StoredFile stored;
		try {
			stored = files.save(user.accountId(), file.getBytes(), file.getContentType(), file.getOriginalFilename());
		}
		catch (FileStore.UnsupportedFileException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
			return "redirect:/shipments/" + id;
		}
		ShipmentService.Added added = service.addDocument(user.accountId(), id, DocType.parse(type), stored, null, "UPLOAD");
		if (added.doc().needsTyping()) {
			redirect.addFlashAttribute("notice", added.reading().type().label() + " added. " + added.reading().note());
			return "redirect:/shipments/" + id + "/documents/" + added.doc().id();
		}
		redirect.addFlashAttribute("notice", added.reading().type().label() + " read and checked: "
				+ added.result().verdict() + ".");
		return "redirect:/shipments/" + id;
	}

	@PostMapping("/{id}/check")
	public String check(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		shipments.get(user.accountId(), id);
		CheckService.Result result = checks.check(user.accountId(), id);
		redirect.addFlashAttribute("notice", "Checked again: " + result.verdict() + ".");
		return "redirect:/shipments/" + id;
	}

	@PostMapping("/{id}/status")
	public String status(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String status) {
		service.setStatus(user.accountId(), id, status);
		return "redirect:/shipments/" + id;
	}

	@PostMapping("/{id}/findings/{findingId}")
	public String decide(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @PathVariable long findingId,
			@RequestParam boolean accept, @RequestParam(required = false) String note) {
		service.decide(user.accountId(), findingId, accept, note);
		return "redirect:/shipments/" + id + "#findings";
	}

	@PostMapping("/{id}/send/forwarder")
	public String sendToForwarder(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			RedirectAttributes redirect) {
		try {
			List<OutboundMessage> sent = service.sendToForwarder(user.accountId(), id);
			redirect.addFlashAttribute("notice", "Corrections sent to the forwarder by "
					+ String.join(" and ", sent.stream().map((m) -> m.isWhatsApp() ? "WhatsApp" : "email").toList()) + ".");
		}
		catch (IllegalStateException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/shipments/" + id;
	}

	@PostMapping("/{id}/send/buyer")
	public String sendToBuyer(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			RedirectAttributes redirect) {
		try {
			service.sendToBuyer(user.accountId(), id);
			redirect.addFlashAttribute("notice", "Amendment request emailed to the buyer.");
		}
		catch (IllegalStateException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/shipments/" + id;
	}

	@GetMapping("/{id}/report.pdf")
	public ResponseEntity<byte[]> report(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		Shipment shipment = shipments.get(user.accountId(), id);
		byte[] pdf = service.report(user.accountId(), id);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename("check-" + shipment.reference().replace('/', '-') + ".pdf").build().toString())
			.body(pdf);
	}

}
