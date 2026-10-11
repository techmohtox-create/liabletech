package com.liabletech.bff.landing;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/landing")
public class LandingController {

	private final LandingContentService contentService;

	public LandingController(LandingContentService contentService) {
		this.contentService = contentService;
	}

	@GetMapping
	public ResponseEntity<LandingContent> landing() {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
			.body(contentService.getContent());
	}

}
