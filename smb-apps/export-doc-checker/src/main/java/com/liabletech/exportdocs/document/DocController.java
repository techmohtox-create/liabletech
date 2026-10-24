package com.liabletech.exportdocs.document;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.exportdocs.check.Findings;
import com.liabletech.exportdocs.shipment.Shipment;
import com.liabletech.exportdocs.shipment.ShipmentService;
import com.liabletech.exportdocs.shipment.Shipments;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.web.NotFoundException;

/** One document: the scan next to what was read from it, to correct before the check is trusted. */
@Controller
@RequestMapping("/shipments/{shipmentId}/documents/{docId}")
public class DocController {

	private final Shipments shipments;

	private final ShipmentDocs docs;

	private final DocData data;

	private final ShipmentService service;

	private final Findings findings;

	private final FileStore files;

	private final DocumentAi ai;

	public DocController(Shipments shipments, ShipmentDocs docs, DocData data, ShipmentService service, Findings findings,
			FileStore files, DocumentAi ai) {
		this.shipments = shipments;
		this.docs = docs;
		this.data = data;
		this.service = service;
		this.findings = findings;
		this.files = files;
		this.ai = ai;
	}

	@GetMapping
	public String view(@AuthenticationPrincipal CurrentUser user, @PathVariable long shipmentId, @PathVariable long docId,
			Model model) {
		Shipment shipment = shipments.get(user.accountId(), shipmentId);
		ShipmentDoc doc = document(user, shipmentId, docId);
		Record reading = data.read(doc);
		model.addAttribute("shipment", shipment);
		model.addAttribute("doc", doc);
		model.addAttribute("docType", doc.type());
		model.addAttribute("file", doc.fileId() == null ? null : files.getForAccount(user.accountId(), doc.fileId()));
		model.addAttribute("fields", reading == null ? List.of() : DocFields.fields(reading));
		model.addAttribute("table", reading == null ? null : DocFields.table(reading, 2));
		model.addAttribute("types", DocType.values());
		model.addAttribute("aiEnabled", ai.available());
		model.addAttribute("findings", findings.forShipment(shipmentId)
			.stream()
			.filter((f) -> f.open() && f.docTypes().contains(doc.type()))
			.toList());
		return "export/document";
	}

	@PostMapping
	public String save(@AuthenticationPrincipal CurrentUser user, @PathVariable long shipmentId, @PathVariable long docId,
			@RequestParam Map<String, String> params, RedirectAttributes redirect) {
		document(user, shipmentId, docId);
		service.saveFields(user.accountId(), docId, params);
		redirect.addFlashAttribute("notice", "Saved, and the shipment checked again.");
		return "redirect:/shipments/" + shipmentId + "/documents/" + docId;
	}

	@PostMapping("/reread")
	public String reread(@AuthenticationPrincipal CurrentUser user, @PathVariable long shipmentId, @PathVariable long docId,
			@RequestParam(required = false) String type, RedirectAttributes redirect) {
		document(user, shipmentId, docId);
		service.reread(user.accountId(), docId, DocType.parse(type));
		redirect.addFlashAttribute("notice", "Read again.");
		return "redirect:/shipments/" + shipmentId;
	}

	@PostMapping("/delete")
	public String delete(@AuthenticationPrincipal CurrentUser user, @PathVariable long shipmentId, @PathVariable long docId,
			RedirectAttributes redirect) {
		document(user, shipmentId, docId);
		service.deleteDocument(user.accountId(), docId);
		redirect.addFlashAttribute("notice", "Document removed.");
		return "redirect:/shipments/" + shipmentId;
	}

	private ShipmentDoc document(CurrentUser user, long shipmentId, long docId) {
		ShipmentDoc doc = docs.get(user.accountId(), docId);
		if (!doc.shipmentId().equals(shipmentId)) {
			throw new NotFoundException("Document " + docId);
		}
		return doc;
	}

}
