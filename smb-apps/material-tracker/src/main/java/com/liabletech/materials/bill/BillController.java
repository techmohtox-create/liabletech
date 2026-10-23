package com.liabletech.materials.bill;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.catalog.Unit;
import com.liabletech.materials.leakage.Flag;
import com.liabletech.materials.leakage.Flags;
import com.liabletech.materials.leakage.ReconcileService;
import com.liabletech.materials.site.Sites;
import com.liabletech.materials.supplier.Suppliers;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.IndianMoney;

@Controller
@RequestMapping("/bills")
public class BillController {

	private final Bills bills;

	private final BillService service;

	private final ReconcileService reconcile;

	private final Sites sites;

	private final Suppliers suppliers;

	private final Materials materials;

	private final Flags flags;

	private final FileStore files;

	private final Outbox outbox;

	public BillController(Bills bills, BillService service, ReconcileService reconcile, Sites sites, Suppliers suppliers,
			Materials materials, Flags flags, FileStore files, Outbox outbox) {
		this.bills = bills;
		this.service = service;
		this.reconcile = reconcile;
		this.sites = sites;
		this.suppliers = suppliers;
		this.materials = materials;
		this.flags = flags;
		this.files = files;
		this.outbox = outbox;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long site, Model model) {
		String filter = status == null || status.isBlank() ? null : status;
		model.addAttribute("status", filter);
		model.addAttribute("site", site);
		model.addAttribute("sites", sites.all(user.accountId()));
		model.addAttribute("bills", bills.list(user.accountId(), filter, site, 300));
		return "materials/bills";
	}

	@PostMapping("/upload")
	public String upload(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long siteId,
			@RequestParam MultipartFile file, RedirectAttributes redirect) throws IOException {
		if (file.isEmpty()) {
			redirect.addFlashAttribute("error", "Choose a photo or PDF of the bill.");
			return "redirect:/bills";
		}
		StoredFile stored = files.save(user.accountId(), file.getBytes(), file.getContentType(), file.getOriginalFilename());
		long id = service.upload(user.accountId(), siteId, stored);
		redirect.addFlashAttribute("notice", "Bill added. Check the lines, then press Check bill.");
		return "redirect:/bills/" + id;
	}

	@GetMapping("/{id}")
	public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		Bill bill = bills.get(user.accountId(), id);
		BillForm form = BillForm.of(bill, bills.lines(id));
		for (int i = 0; i < 2; i++) {
			form.getLines().add(new BillForm.Line());
		}
		model.addAttribute("bill", bill);
		model.addAttribute("form", form);
		model.addAttribute("file", bill.fileId() == null ? null : files.get(bill.fileId()));
		model.addAttribute("sites", sites.all(user.accountId()));
		model.addAttribute("suppliers", suppliers.all(user.accountId()));
		model.addAttribute("materials", materials.active(user.accountId()));
		model.addAttribute("units", Unit.values());
		List<Flag> billFlags = flags.forBill(user.accountId(), id);
		model.addAttribute("flags", billFlags);
		model.addAttribute("atRisk", sum(billFlags.stream().filter(Flag::atRisk)));
		model.addAttribute("recovered", sum(billFlags.stream().filter((f) -> Flag.RECOVERED.equals(f.status()))));
		model.addAttribute("openCount", billFlags.stream().filter((f) -> Flag.OPEN.equals(f.status())).count());
		model.addAttribute("raisedCount", billFlags.stream().filter((f) -> Flag.RAISED.equals(f.status())).count());
		model.addAttribute("linked", reconcile.linked(user.accountId(), id));
		model.addAttribute("materialNames", materials.all(user.accountId())
			.stream()
			.collect(Collectors.toMap(Material::id, Material::name)));
		model.addAttribute("messages", outbox.about(user.accountId(), "bill", id));
		model.addAttribute("supplier", suppliers.find(user.accountId(), bill.supplierId()).orElse(null));
		return "materials/bill";
	}

	@PostMapping("/{id}")
	public String save(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@ModelAttribute("form") BillForm form, @RequestParam(defaultValue = "false") boolean check,
			RedirectAttributes redirect) {
		service.save(user.accountId(), id, form);
		if (check) {
			return check(user, id, redirect);
		}
		redirect.addFlashAttribute("notice", "Bill saved.");
		return "redirect:/bills/" + id;
	}

	@PostMapping("/{id}/check")
	public String check(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		try {
			ReconcileService.Result result = reconcile.check(user.accountId(), id);
			redirect.addFlashAttribute("notice", result.findings() == 0
					? "Checked against " + result.deliveriesLinked() + " deliveries: everything matches."
					: "Checked against " + result.deliveriesLinked() + " deliveries: " + result.findings()
							+ (result.findings() == 1 ? " finding" : " findings") + ", " + IndianMoney.rupees(result.atRisk())
							+ " at risk.");
		}
		catch (IllegalStateException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/bills/" + id;
	}

	@PostMapping("/{id}/raise")
	public String raise(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		try {
			List<OutboundMessage> sent = service.raiseWithSupplier(user.accountId(), id);
			redirect.addFlashAttribute("notice", "Discrepancy note sent by "
					+ String.join(" and ", sent.stream().map((m) -> m.isWhatsApp() ? "WhatsApp" : "email").toList()) + ".");
		}
		catch (IllegalStateException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/bills/" + id;
	}

	@PostMapping("/{id}/recovered")
	public String recovered(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@RequestParam(required = false) String note, RedirectAttributes redirect) {
		service.markRecovered(user.accountId(), id, note);
		redirect.addFlashAttribute("notice", "Marked recovered.");
		return "redirect:/bills/" + id;
	}

	@PostMapping("/{id}/status")
	public String status(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String status,
			RedirectAttributes redirect) {
		if (!List.of(Bill.APPROVED, Bill.PAID, Bill.REJECTED).contains(status)) {
			redirect.addFlashAttribute("error", "Unknown status.");
		}
		else {
			service.setStatus(user.accountId(), id, status);
		}
		return "redirect:/bills/" + id;
	}

	private static BigDecimal sum(Stream<Flag> flags) {
		return flags.map(Flag::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
