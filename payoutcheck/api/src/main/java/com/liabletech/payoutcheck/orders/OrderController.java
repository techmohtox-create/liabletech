package com.liabletech.payoutcheck.orders;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.payoutcheck.common.PageResult;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.security.CurrentUser;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderRepository orders;

	public OrderController(OrderRepository orders) {
		this.orders = orders;
	}

	@GetMapping
	public PageResult<OrderRecord> search(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long outletId,
			@RequestParam(required = false) String platform, @RequestParam(required = false) String status,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return this.orders.search(user.orgId(), outletId, platform == null || platform.isBlank() ? null : Platform.parse(platform),
				status, q, from, to, PageResult.clampPage(page), PageResult.clampSize(size));
	}

}
