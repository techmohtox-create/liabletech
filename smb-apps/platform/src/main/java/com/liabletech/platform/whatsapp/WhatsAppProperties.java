package com.liabletech.platform.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * WhatsApp Business Cloud API settings. In {@code simulator} mode nothing leaves the machine:
 * replies are recorded in the outbox and shown on the simulator page.
 *
 * @param phoneNumberId the platform's own number, used for accounts without a number of their own
 * @param useTemplates send approved message templates for business-initiated messages (invoices,
 * reminders). WhatsApp only delivers free-form text inside the 24-hour window after the customer
 * last wrote, so production needs this on once the templates are approved.
 */
@ConfigurationProperties("app.whatsapp")
public record WhatsAppProperties(Mode mode, String graphVersion, String verifyToken, String appSecret,
		String accessToken, String phoneNumberId, boolean useTemplates, String templateLanguage) {

	public enum Mode {

		SIMULATOR, CLOUD

	}

	public boolean simulator() {
		return mode != Mode.CLOUD;
	}

	public boolean cloudReady() {
		return mode == Mode.CLOUD && StringUtils.hasText(accessToken);
	}

}
