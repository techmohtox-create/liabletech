package com.liabletech.payoutcheck.ingest;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.liabletech.payoutcheck.common.BadRequestException;
import com.liabletech.payoutcheck.common.NotFoundException;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.ingest.StatementImportService.StatementDetails;
import com.liabletech.payoutcheck.security.CurrentUser;
import com.liabletech.payoutcheck.security.Roles;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

	private final OrderImportService orderImports;

	private final StatementImportService statementImports;

	private final ImportBatchRepository batches;

	public ImportController(OrderImportService orderImports, StatementImportService statementImports,
			ImportBatchRepository batches) {
		this.orderImports = orderImports;
		this.statementImports = statementImports;
		this.batches = batches;
	}

	@PostMapping(path = "/orders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(Roles.EDITOR)
	public ImportResult importOrders(@AuthenticationPrincipal CurrentUser user, @RequestParam long outletId,
			@RequestParam(required = false) String platform, @RequestPart("file") MultipartFile file) throws IOException {
		Platform chosen = platform == null || platform.isBlank() ? null : Platform.parse(platform);
		return this.orderImports.importOrders(user, outletId, chosen, name(file), bytes(file));
	}

	@PostMapping(path = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(Roles.EDITOR)
	public ImportResult importStatement(@AuthenticationPrincipal CurrentUser user, @RequestParam long outletId,
			@RequestParam String platform,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payoutDate,
			@RequestParam(required = false) String utr, @RequestParam(required = false) BigDecimal creditedAmount,
			@RequestPart("file") MultipartFile file) throws IOException {
		StatementDetails details = new StatementDetails(Platform.parse(platform), periodStart, periodEnd, payoutDate, utr,
				creditedAmount);
		return this.statementImports.importStatement(user, outletId, details, name(file), bytes(file));
	}

	@GetMapping
	public List<ImportBatch> recent(@AuthenticationPrincipal CurrentUser user) {
		return this.batches.recent(user.orgId(), 50);
	}

	@GetMapping("/templates/{kind}")
	public ResponseEntity<byte[]> template(@PathVariable String kind) {
		String csv = switch (kind) {
			case "orders" -> Templates.orders();
			case "statement" -> Templates.statement();
			default -> throw new NotFoundException("Template");
		};
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payoutcheck-" + kind + "-template.csv\"")
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.body(csv.getBytes(StandardCharsets.UTF_8));
	}

	private static String name(MultipartFile file) {
		String name = file.getOriginalFilename();
		return name == null || name.isBlank() ? "upload.csv" : name.replaceAll("[\\\\/]", "_");
	}

	private static byte[] bytes(MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new BadRequestException("The file is empty");
		}
		return file.getBytes();
	}

}
