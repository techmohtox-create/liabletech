package com.liabletech.platform.web;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import com.liabletech.platform.AppProperties;

/** An icon in the app's accent colour with its initials, so each app is easy to spot in browser tabs. */
@RestController
public class FaviconController {

	private final String svg;

	public FaviconController(AppProperties properties) {
		String initials = Arrays.stream(properties.brand().name().split("\\s+"))
			.filter((word) -> !word.isEmpty())
			.limit(2)
			.map((word) -> word.substring(0, 1).toUpperCase())
			.collect(Collectors.joining());
		String accent = properties.brand().accent().matches("#[0-9a-fA-F]{3,8}") ? properties.brand().accent() : "#2563eb";
		this.svg = """
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">\
				<rect width="64" height="64" rx="14" fill="%s"/>\
				<text x="32" y="42" font-family="Arial, Helvetica, sans-serif" font-size="26" font-weight="700" \
				text-anchor="middle" fill="#fff">%s</text></svg>""".formatted(accent, HtmlUtils.htmlEscape(initials));
	}

	@GetMapping("/favicon.svg")
	public ResponseEntity<String> favicon() {
		return ResponseEntity.ok()
			.contentType(MediaType.valueOf("image/svg+xml"))
			.cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)))
			.body(svg);
	}

}
