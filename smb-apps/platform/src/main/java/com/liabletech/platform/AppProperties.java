package com.liabletech.platform;

import java.nio.file.Path;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings every app shares. Defaults live in {@code platform-defaults.properties}; each app sets
 * its brand in its own {@code application.properties}.
 */
@ConfigurationProperties("app")
public record AppProperties(Path dataDir, ZoneId timezone, String publicBaseUrl, Brand brand, Demo demo) {

	public record Brand(String name, @DefaultValue("") String tagline, @DefaultValue("#2563eb") String accent,
			@DefaultValue("") String plan) {
	}

	/** The demo login created on first start, shown on the sign-in page. */
	public record Demo(@DefaultValue("true") boolean enabled, @DefaultValue("") String email,
			@DefaultValue("") String password) {
	}

	/** The public URL with no trailing slash, for links that leave the app (booking pages, PDFs). */
	public String baseUrl() {
		return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
	}

}
