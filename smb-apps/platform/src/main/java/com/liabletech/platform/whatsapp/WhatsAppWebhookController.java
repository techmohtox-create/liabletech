package com.liabletech.platform.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

import com.liabletech.platform.messaging.Outbox;

/**
 * The WhatsApp Cloud API webhook. Register {@code https://<host>/webhooks/whatsapp} in the Meta app
 * with the verify token, and set the app secret so every call's signature is checked.
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

	private final WhatsAppProperties properties;

	private final InboundMessages messages;

	private final InboundProcessor processor;

	private final Outbox outbox;

	private final ObjectMapper json;

	private final Clock clock;

	public WhatsAppWebhookController(WhatsAppProperties properties, InboundMessages messages,
			InboundProcessor processor, Outbox outbox, ObjectMapper json, Clock clock) {
		this.properties = properties;
		this.messages = messages;
		this.processor = processor;
		this.outbox = outbox;
		this.json = json;
		this.clock = clock;
	}

	/** Meta's one-time subscription check: echo the challenge if the verify token matches. */
	@GetMapping
	public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
			@RequestParam(name = "hub.verify_token", required = false) String token,
			@RequestParam(name = "hub.challenge", required = false) String challenge) {
		if ("subscribe".equals(mode) && StringUtils.hasText(properties.verifyToken())
				&& MessageDigest.isEqual(bytes(properties.verifyToken()), bytes(token))) {
			return ResponseEntity.ok(challenge);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	/** Stores each new message and returns at once; processing happens on a background lane. */
	@PostMapping
	public ResponseEntity<String> receive(@RequestBody byte[] body,
			@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
		if (StringUtils.hasText(properties.appSecret())) {
			if (!validSignature(body, signature)) {
				log.warn("Rejected WhatsApp webhook call with a bad signature");
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			}
		}
		else if (!properties.simulator()) {
			log.error("WHATSAPP_APP_SECRET is not set; refusing unsigned webhook calls in cloud mode");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		WebhookPayload payload = WebhookPayload.parse(json.readTree(body));
		OffsetDateTime now = OffsetDateTime.now(clock);
		for (WebhookPayload.Message message : payload.messages()) {
			if (message.waMessageId() == null || message.from() == null) {
				continue;
			}
			messages
				.insertIfNew(new InboundMessages.NewInbound(null, message.waMessageId(), message.from(),
						message.phoneNumberId(), message.contactName(), message.type(), message.text(),
						message.mediaId(), message.mimeType(), message.filename(), null, message.contextId(),
						message.timestamp() != null ? message.timestamp() : now))
				.ifPresent((id) -> processor.dispatch(id, message.from()));
		}
		for (WebhookPayload.Status status : payload.statuses()) {
			if (status.waMessageId() != null) {
				outbox.updateDeliveryStatus(status.waMessageId(), status.status().toUpperCase(java.util.Locale.ROOT),
						status.error(), now);
			}
		}
		return ResponseEntity.ok("ok");
	}

	private boolean validSignature(byte[] body, String header) {
		if (header == null || !header.startsWith("sha256=")) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(bytes(properties.appSecret()), "HmacSHA256"));
			byte[] expected = mac.doFinal(body);
			byte[] given = HexFormat.of().parseHex(header.substring("sha256=".length()));
			return MessageDigest.isEqual(expected, given);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException ex) {
			return false;
		}
	}

	private static byte[] bytes(String value) {
		return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
	}

}
