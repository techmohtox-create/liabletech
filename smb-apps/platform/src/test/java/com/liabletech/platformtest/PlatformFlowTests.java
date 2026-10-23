package com.liabletech.platformtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.liabletech.platform.account.SignupService;
import com.liabletech.platform.account.Users;
import com.liabletech.platform.messaging.OutboundMessage;
import com.liabletech.platform.messaging.Outbox;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.whatsapp.InboundMessage;
import com.liabletech.platform.whatsapp.InboundMessages;

@SpringBootTest(classes = TestPlatformApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:platform-test.properties",
		properties = "app.whatsapp.app-secret=test-secret")
class PlatformFlowTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SignupService signups;

	@Autowired
	private Users users;

	@Autowired
	private Outbox outbox;

	@Autowired
	private InboundMessages inbound;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TestPlatformApplication.RecordingHandler handler;

	private long accountId;

	private CurrentUser owner;

	@BeforeEach
	void createAccount() {
		String email = "owner" + System.nanoTime() + "@example.com";
		accountId = signups.createDemo("Shree Transport", "Suresh", "98200 11111", email, "password123");
		owner = new CurrentUser(users.findByEmail(email).orElseThrow());
		handler.handled.clear();
	}

	@Test
	void signupCreatesAnAccountThatCanSignIn() throws Exception {
		mvc.perform(post("/signup").with(csrf())
			.param("businessName", "Gupta Logistics")
			.param("ownerName", "Anil Gupta")
			.param("phone", "98111 22233")
			.param("email", "anil@example.com")
			.param("password", "longenough"))
			.andExpect(redirectedUrl("/login?registered"));

		mvc.perform(post("/login").with(csrf()).param("username", "anil@example.com").param("password", "longenough"))
			.andExpect(redirectedUrl("/"));
		assertThat(users.findByEmail("ANIL@example.com")).isPresent();
	}

	@Test
	void pagesNeedASignIn() throws Exception {
		mvc.perform(get("/settings")).andExpect(status().is3xxRedirection());
		mvc.perform(get("/settings").with(user(owner))).andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Shree Transport")));
	}

	@Test
	void webhookRoutesByRegisteredPhoneAndRepliesOnce() throws Exception {
		TestPlatformApplication.KNOWN_PHONES.put("+919876543210", accountId);
		String body = """
				{"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"PLATFORM"},
				  "messages":[{"from":"919876543210","id":"wamid.flow1","timestamp":"1760000000","type":"text",
				               "text":{"body":"hello"}}]}}]}]}
				""";

		mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON)
			.header("X-Hub-Signature-256", sign(body))
			.content(body)).andExpect(status().isOk());
		// WhatsApp retries deliveries; the same message id must not be handled twice.
		mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON)
			.header("X-Hub-Signature-256", sign(body))
			.content(body)).andExpect(status().isOk());

		assertThat(handler.handled).singleElement()
			.satisfies((context) -> assertThat(context.accountId()).isEqualTo(accountId));
		List<OutboundMessage> sent = outbox.forAccount(accountId, "WHATSAPP", 10);
		assertThat(sent).singleElement().satisfies((message) -> {
			assertThat(message.recipient()).isEqualTo("+919876543210");
			assertThat(message.body()).isEqualTo("Got it: text");
			assertThat(message.status()).isEqualTo(OutboundMessage.SIMULATED);
		});
	}

	@Test
	void webhookRejectsABadSignature() throws Exception {
		mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON)
			.header("X-Hub-Signature-256", "sha256=00")
			.content("{}")).andExpect(status().isUnauthorized());
	}

	@Test
	void unknownSenderIsNotHandedToTheApp() throws Exception {
		String body = """
				{"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"PLATFORM"},
				  "messages":[{"from":"919999999999","id":"wamid.stranger","timestamp":"1760000000","type":"text",
				               "text":{"body":"hi"}}]}}]}]}
				""";

		mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON)
			.header("X-Hub-Signature-256", sign(body))
			.content(body)).andExpect(status().isOk());

		assertThat(handler.handled).isEmpty();
		String status = jdbc.sql("SELECT status FROM wa_inbound WHERE wa_message_id = 'wamid.stranger'")
			.query(String.class)
			.single();
		assertThat(status).isEqualTo(InboundMessage.UNROUTED);
	}

	@Test
	void simulatorSendsAPhotoThroughTheSamePipeline() throws Exception {
		TestPlatformApplication.KNOWN_PHONES.put("+919876543210", accountId);
		byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F' };

		mvc.perform(multipart("/dev/whatsapp").file(new MockMultipartFile("file", "pod.jpg", "image/jpeg", jpeg))
			.param("phone", "98765 43210")
			.param("text", "LR 4521")
			.with(user(owner))
			.with(csrf())).andExpect(status().is3xxRedirection());

		assertThat(handler.handled).singleElement().satisfies((context) -> {
			assertThat(context.message().isImage()).isTrue();
			assertThat(context.message().text()).isEqualTo("LR 4521");
			assertThat(context.file()).hasValueSatisfying((file) -> {
				assertThat(file.contentType()).isEqualTo("image/jpeg");
				assertThat(file.accountId()).isEqualTo(accountId);
			});
		});
		mvc.perform(get("/dev/whatsapp/thread").param("phone", "+919876543210").with(user(owner)))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Got it: image")));
	}

	@Test
	void filesAreOnlyServedToTheirAccount() throws Exception {
		TestPlatformApplication.KNOWN_PHONES.put("+919876543210", accountId);
		byte[] pdf = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);
		mvc.perform(multipart("/dev/whatsapp").file(new MockMultipartFile("file", "lr.pdf", "application/pdf", pdf))
			.param("phone", "+919876543210")
			.with(user(owner))
			.with(csrf()));
		long fileId = handler.handled.getFirst().file().orElseThrow().id();

		mvc.perform(get("/files/" + fileId).with(user(owner))).andExpect(status().isOk())
			.andExpect(content().contentType("application/pdf"));

		long otherAccount = signups.createDemo("Other", "Other", "98200 22222", "other" + System.nanoTime() + "@example.com",
				"password123");
		CurrentUser stranger = new CurrentUser(users.forAccount(otherAccount).getFirst());
		mvc.perform(get("/files/" + fileId).with(user(stranger))).andExpect(status().isNotFound());
	}

	private static String sign(String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
	}

}
