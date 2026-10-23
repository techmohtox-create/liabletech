package com.liabletech.platform.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class WebhookPayloadTests {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void readsAPhotoWithCaptionAndAStatusUpdate() {
		String body = """
				{"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
				  "messaging_product":"whatsapp",
				  "metadata":{"display_phone_number":"15550001111","phone_number_id":"PNID1"},
				  "contacts":[{"profile":{"name":"Ramesh"},"wa_id":"919876543210"}],
				  "messages":[{"from":"919876543210","id":"wamid.A1","timestamp":"1760000000","type":"image",
				    "image":{"caption":"LR 4521","mime_type":"image/jpeg","sha256":"x","id":"MEDIA9"}}],
				  "statuses":[{"id":"wamid.OUT1","status":"read","timestamp":"1760000100","recipient_id":"919876543210"}]
				}}]}]}
				""";

		WebhookPayload payload = WebhookPayload.parse(json.readTree(body));

		assertThat(payload.messages()).singleElement().satisfies((message) -> {
			assertThat(message.waMessageId()).isEqualTo("wamid.A1");
			assertThat(message.from()).isEqualTo("+919876543210");
			assertThat(message.phoneNumberId()).isEqualTo("PNID1");
			assertThat(message.contactName()).isEqualTo("Ramesh");
			assertThat(message.type()).isEqualTo("image");
			assertThat(message.text()).isEqualTo("LR 4521");
			assertThat(message.mediaId()).isEqualTo("MEDIA9");
			assertThat(message.mimeType()).isEqualTo("image/jpeg");
			assertThat(message.timestamp()).isNotNull();
		});
		assertThat(payload.statuses()).singleElement().satisfies((status) -> {
			assertThat(status.waMessageId()).isEqualTo("wamid.OUT1");
			assertThat(status.status()).isEqualTo("read");
		});
	}

	@Test
	void readsTextAndButtonReplies() {
		String body = """
				{"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"P"},
				  "messages":[
				    {"from":"919000000001","id":"m1","timestamp":"1","type":"text","text":{"body":"OK"}},
				    {"from":"919000000001","id":"m2","timestamp":"2","type":"interactive",
				     "interactive":{"type":"button_reply","button_reply":{"id":"renew","title":"Renew"}}}
				  ]}}]}]}
				""";

		WebhookPayload payload = WebhookPayload.parse(json.readTree(body));

		assertThat(payload.messages()).extracting(WebhookPayload.Message::text).containsExactly("OK", "Renew");
	}

}
