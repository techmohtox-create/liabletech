package com.liabletech.parts.catalog;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.text.Lenient;

/** The stock list: what the counter can quote, what it fits, and how many are on the shelf. */
@Controller
@RequestMapping("/parts")
public class PartController {

	private final Parts parts;

	private final StockCsv csv;

	private final Clock clock;

	public PartController(Parts parts, StockCsv csv, Clock clock) {
		this.parts = parts;
		this.csv = csv;
		this.clock = clock;
	}

	@GetMapping
	public String list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String q,
			@RequestParam(required = false) String category, @RequestParam(defaultValue = "all") String show, Model model) {
		long account = user.accountId();
		List<Part> found = parts.list(account, q, category, show, 1000);
		model.addAttribute("parts", found);
		model.addAttribute("fitments", parts.fitments(found.stream().map(Part::id).toList()));
		model.addAttribute("q", q);
		model.addAttribute("category", category);
		model.addAttribute("show", show);
		model.addAttribute("types", Arrays.asList(PartType.values()));
		model.addAttribute("total", parts.countActive(account));
		return "parts/parts";
	}

	@GetMapping("/new")
	public String newPart(Model model) {
		model.addAttribute("part", null);
		model.addAttribute("fits", "");
		model.addAttribute("types", Arrays.asList(PartType.values()));
		model.addAttribute("models", VehicleCatalog.all());
		return "parts/part";
	}

	@GetMapping("/{id}")
	public String part(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		Part part = parts.get(user.accountId(), id);
		model.addAttribute("part", part);
		model.addAttribute("fits", String.join("; ", parts.fitments(id).stream().map(Fitment::describe).toList()));
		model.addAttribute("types", Arrays.asList(PartType.values()));
		model.addAttribute("models", VehicleCatalog.all());
		return "parts/part";
	}

	@PostMapping
	public String create(@AuthenticationPrincipal CurrentUser user, @RequestParam String partNumber,
			@RequestParam String name, @RequestParam String category, @RequestParam(required = false) String brand,
			@RequestParam(required = false) String altNumbers, @RequestParam(required = false) String price,
			@RequestParam(required = false) String mrp, @RequestParam(defaultValue = "0") int stock,
			@RequestParam(required = false) String rack, @RequestParam(required = false) String fits,
			RedirectAttributes redirect) {
		Parts.PartData data = data(partNumber, name, category, brand, altNumbers, price, mrp, stock, rack);
		if (data == null) {
			redirect.addFlashAttribute("error", "A part needs a number, a name and a price.");
			return "redirect:/parts/new";
		}
		try {
			long id = parts.create(user.accountId(), data, OffsetDateTime.now(clock));
			parts.replaceFitments(id, StockCsv.fitments(fits));
			redirect.addFlashAttribute("notice", "Part added.");
			return "redirect:/parts/" + id;
		}
		catch (DuplicateKeyException ex) {
			redirect.addFlashAttribute("error", "Part number " + partNumber.trim() + " is already in the stock list.");
			return "redirect:/parts/new";
		}
	}

	@PostMapping("/{id}")
	public String update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam String partNumber,
			@RequestParam String name, @RequestParam String category, @RequestParam(required = false) String brand,
			@RequestParam(required = false) String altNumbers, @RequestParam(required = false) String price,
			@RequestParam(required = false) String mrp, @RequestParam(defaultValue = "0") int stock,
			@RequestParam(required = false) String rack, @RequestParam(required = false) String fits,
			RedirectAttributes redirect) {
		parts.get(user.accountId(), id);
		Parts.PartData data = data(partNumber, name, category, brand, altNumbers, price, mrp, stock, rack);
		if (data == null) {
			redirect.addFlashAttribute("error", "A part needs a number, a name and a price.");
			return "redirect:/parts/" + id;
		}
		try {
			parts.update(user.accountId(), id, data, OffsetDateTime.now(clock));
			parts.replaceFitments(id, StockCsv.fitments(fits));
			redirect.addFlashAttribute("notice", "Saved.");
		}
		catch (DuplicateKeyException ex) {
			redirect.addFlashAttribute("error", "Another part already has number " + partNumber.trim() + ".");
		}
		return "redirect:/parts/" + id;
	}

	/** A quick stock count from the list: the number on the shelf now. */
	@PostMapping("/{id}/stock")
	public String stock(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @RequestParam int stock,
			@RequestParam(defaultValue = "/parts") String back) {
		parts.get(user.accountId(), id);
		parts.setStock(user.accountId(), id, stock, OffsetDateTime.now(clock));
		return "redirect:" + (back.startsWith("/") && !back.startsWith("//") ? back : "/parts");
	}

	@PostMapping("/{id}/remove")
	public String remove(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		parts.get(user.accountId(), id);
		parts.deactivate(user.accountId(), id, OffsetDateTime.now(clock));
		redirect.addFlashAttribute("notice", "Removed from the stock list.");
		return "redirect:/parts";
	}

	@PostMapping("/import")
	public String importCsv(@AuthenticationPrincipal CurrentUser user, @RequestParam("file") MultipartFile file,
			RedirectAttributes redirect) throws IOException {
		if (file.isEmpty()) {
			redirect.addFlashAttribute("error", "Choose a CSV file to import.");
			return "redirect:/parts";
		}
		StockCsv.Result result = csv.importCsv(user.accountId(), file.getBytes());
		redirect.addFlashAttribute(result.added() + result.updated() > 0 ? "notice" : "error",
				result.added() + " added, " + result.updated() + " updated"
						+ (result.problems().isEmpty() ? "." : "; " + result.problems().size() + " lines skipped."));
		redirect.addFlashAttribute("problems", result.problems().stream().limit(20).toList());
		return "redirect:/parts";
	}

	@GetMapping("/export.csv")
	public ResponseEntity<byte[]> export(@AuthenticationPrincipal CurrentUser user) {
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stock.csv\"")
			.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
			.body(csv.export(user.accountId()));
	}

	private static Parts.PartData data(String partNumber, String name, String category, String brand, String altNumbers,
			String price, String mrp, int stock, String rack) {
		BigDecimal amount = Lenient.number(price);
		if (!StringUtils.hasText(partNumber) || !StringUtils.hasText(name) || amount == null || amount.signum() <= 0) {
			return null;
		}
		PartType type = PartType.parse(category);
		return new Parts.PartData(partNumber, name, type == null ? PartType.OTHER : type, brand, altNumbers, amount,
				Lenient.number(mrp), Math.max(0, stock), rack);
	}

}
