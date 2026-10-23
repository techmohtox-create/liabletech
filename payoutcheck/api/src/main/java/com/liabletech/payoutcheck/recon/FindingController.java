package com.liabletech.payoutcheck.recon;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.payoutcheck.common.PageResult;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.recon.FindingService.DisputeLetter;
import com.liabletech.payoutcheck.recon.FindingService.StatusChange;
import com.liabletech.payoutcheck.security.CurrentUser;
import com.liabletech.payoutcheck.security.Roles;

@RestController
@RequestMapping("/api/findings")
public class FindingController {

	private final FindingService findings;

	public FindingController(FindingService findings) {
		this.findings = findings;
	}

	@GetMapping
	public PageResult<Finding> search(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long outletId,
			@RequestParam(required = false) Long statementId, @RequestParam(required = false) String platform,
			@RequestParam(required = false) FindingStatus status, @RequestParam(required = false) FindingType type,
			@RequestParam(required = false) Severity severity, @RequestParam(required = false) String q,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return this.findings.search(user.orgId(), filter(outletId, statementId, platform, status, type, severity, q),
				PageResult.clampPage(page), PageResult.clampSize(size));
	}

	@GetMapping("/types")
	public List<Map<String, String>> types() {
		return Arrays.stream(FindingType.values())
			.map((t) -> Map.of("code", t.name(), "label", t.label(), "description", t.disputeText()))
			.toList();
	}

	@GetMapping(path = "/export", produces = "text/csv")
	public ResponseEntity<byte[]> export(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long outletId,
			@RequestParam(required = false) Long statementId, @RequestParam(required = false) String platform,
			@RequestParam(required = false) FindingStatus status, @RequestParam(required = false) FindingType type,
			@RequestParam(required = false) Severity severity, @RequestParam(required = false) String q) {
		String csv = this.findings.exportCsv(user.orgId(), filter(outletId, statementId, platform, status, type, severity, q));
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payoutcheck-findings-" + LocalDate.now() + ".csv\"")
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.body(csv.getBytes(StandardCharsets.UTF_8));
	}

	public record UpdateRequest(@NotNull FindingStatus status, @Size(max = 80) String ticketRef, BigDecimal recoveredAmount,
			@Size(max = 1000) String notes) {
	}

	@PutMapping("/{id}")
	@PreAuthorize(Roles.EDITOR)
	public Finding update(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @RequestBody UpdateRequest request) {
		return this.findings.update(user, id,
				new StatusChange(request.status(), request.ticketRef(), request.recoveredAmount(), request.notes()));
	}

	public record BulkRequest(@NotEmpty @Size(max = 1000) List<Long> ids, @NotNull FindingStatus status,
			@Size(max = 80) String ticketRef, @Size(max = 1000) String notes) {
	}

	@PostMapping("/bulk")
	@PreAuthorize(Roles.EDITOR)
	public Map<String, Integer> bulk(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody BulkRequest request) {
		int updated = this.findings.bulkUpdate(user, request.ids(),
				new StatusChange(request.status(), request.ticketRef(), null, request.notes()));
		return Map.of("updated", updated);
	}

	public record LetterRequest(List<Long> findingIds) {
	}

	@PostMapping("/letter/{statementId}")
	public DisputeLetter letter(@AuthenticationPrincipal CurrentUser user, @PathVariable long statementId,
			@RequestBody(required = false) LetterRequest request) {
		return this.findings.letter(user, statementId, request == null ? null : request.findingIds());
	}

	private static FindingRepository.Filter filter(Long outletId, Long statementId, String platform, FindingStatus status,
			FindingType type, Severity severity, String q) {
		return new FindingRepository.Filter(outletId, statementId,
				platform == null || platform.isBlank() ? null : Platform.parse(platform), status, type, severity, q);
	}

}
