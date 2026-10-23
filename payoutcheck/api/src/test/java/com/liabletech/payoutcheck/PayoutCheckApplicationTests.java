package com.liabletech.payoutcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.liabletech.payoutcheck.demo.DemoSeeder;
import com.liabletech.payoutcheck.recon.FindingType;

@SpringBootTest
@AutoConfigureMockMvc
class PayoutCheckApplicationTests {

	private static final String ORDERS = """
			Order ID,Order Date,Status,Item Total,Packaging Charges,Restaurant Discount
			S-1,13/10/2026 13:05,Delivered,450,20,40
			S-2,14/10/2026 20:10,Delivered,300,0,0
			S-3,15/10/2026 21:00,Delivered,600,0,0
			S-4,16/10/2026 12:30,Cancelled,250,0,0
			""";

	// S-1 is correct, S-2 has commission at 22%, S-3 is missing, S-4 carries a cancellation charge,
	// and an unexplained recovery sits at the bottom.
	private static final String STATEMENT = """
			Swiggy payout annexure,,,,,,,,,
			Order No,Order Date,Order Status,Item Total,Packaging Charges,Restaurant Discount,Commission,Payment Collection Charges,GST on Fees,TDS,Cancellation Deduction,Other Adjustments,Net Payout,Remarks
			S-1,13-10-2026,Delivered,450,20,40,77.40,8.60,15.48,0.43,0,0,328.09,
			S-2,14-10-2026,Delivered,300,0,0,66.00,6.00,12.96,0.30,0,0,214.74,
			S-4,16-10-2026,Cancelled,0,0,0,0,0,0,0,37.50,0,-37.50,Customer cancelled
			,,,0,0,0,0,0,0,0,0,-299,-299,Misc recovery
			Total,,,,,,,,,,,,206.33,
			""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private DemoSeeder demoSeeder;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void healthIsPublicAndTheApiIsNot() throws Exception {
		this.mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		this.mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/plans")).andExpect(status().isOk()).andExpect(jsonPath("$[0].monthlyPriceInr").value(199));
	}

	@Test
	void writesNeedACsrfToken() throws Exception {
		this.mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(uniqueEmail())))
			.andExpect(status().isForbidden());
	}

	@Test
	void fullFlowFromSignUpToDisputeLetter() throws Exception {
		MockHttpSession session = signUp(uniqueEmail());
		long outletId = firstOutlet(session);

		this.mvc
			.perform(multipart("/api/imports/orders").file(file("orders.csv", ORDERS))
				.param("outletId", String.valueOf(outletId))
				.param("platform", "SWIGGY")
				.session(session)
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(4))
			.andExpect(jsonPath("$.rejected").value(0));

		MvcResult imported = this.mvc
			.perform(multipart("/api/imports/statements").file(file("swiggy-week.csv", STATEMENT))
				.param("outletId", String.valueOf(outletId))
				.param("platform", "SWIGGY")
				.param("periodStart", "2026-10-12")
				.param("periodEnd", "2026-10-18")
				.param("creditedAmount", "206.33")
				.session(session)
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(4))
			.andExpect(jsonPath("$.skipped").value(1))
			.andReturn();
		int statementId = JsonPath.read(imported.getResponse().getContentAsString(), "$.statementId");

		String findings = this.mvc.perform(get("/api/findings").param("statementId", String.valueOf(statementId)).session(session))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		java.util.List<String> types = JsonPath.read(findings, "$.items[*].type");
		assertThat(types).containsExactlyInAnyOrder("MISSING_ORDER", "COMMISSION_OVERCHARGE", "CANCELLATION_DEDUCTION",
				"UNEXPLAINED_ADJUSTMENT");

		// Same period again is refused.
		this.mvc
			.perform(multipart("/api/imports/statements").file(file("swiggy-week.csv", STATEMENT))
				.param("outletId", String.valueOf(outletId))
				.param("platform", "SWIGGY")
				.param("periodStart", "2026-10-12")
				.param("periodEnd", "2026-10-18")
				.session(session)
				.with(csrf()))
			.andExpect(status().isConflict());

		// Starter has no dispute letters; Growth does.
		this.mvc.perform(post("/api/findings/letter/" + statementId).session(session).with(csrf()))
			.andExpect(status().isBadRequest());
		this.mvc
			.perform(put("/api/organization/plan").contentType(MediaType.APPLICATION_JSON)
				.content("{\"plan\":\"GROWTH\"}")
				.session(session)
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.plan.code").value("GROWTH"));
		this.mvc.perform(post("/api/findings/letter/" + statementId).session(session).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.findings").value(4))
			.andExpect(jsonPath("$.body", containsString("S-3")));

		// Work a finding through to recovery.
		int findingId = JsonPath.read(findings, "$.items[0].id");
		this.mvc
			.perform(put("/api/findings/" + findingId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"RECOVERED\",\"ticketRef\":\"SW-123\"}")
				.session(session)
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RECOVERED"))
			.andExpect(jsonPath("$.recoveredAmount").isNumber());
		this.mvc.perform(get("/api/dashboard").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totals.recovered", greaterThan(0.0)))
			.andExpect(jsonPath("$.totals.statements").value(1));
		this.mvc.perform(get("/api/findings/export").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("SW-123")));
		this.mvc.perform(get("/api/audit").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total", greaterThan(4)));

		// Re-checking keeps the worked finding and its status.
		this.mvc.perform(post("/api/statements/" + statementId + "/check").session(session).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.findings").value(4))
			.andExpect(jsonPath("$.newFindings").value(0));
		this.mvc.perform(get("/api/findings").param("status", "RECOVERED").session(session))
			.andExpect(jsonPath("$.total").value(1));
	}

	@Test
	void anotherBusinessSeesNothing() throws Exception {
		MockHttpSession owner = signUp(uniqueEmail());
		long outletId = firstOutlet(owner);
		MvcResult imported = this.mvc
			.perform(multipart("/api/imports/statements").file(file("w.csv", STATEMENT))
				.param("outletId", String.valueOf(outletId))
				.param("platform", "ZOMATO")
				.session(owner)
				.with(csrf()))
			.andExpect(status().isOk())
			.andReturn();
		int statementId = JsonPath.read(imported.getResponse().getContentAsString(), "$.statementId");

		MockHttpSession stranger = signUp(uniqueEmail());
		this.mvc.perform(get("/api/statements/" + statementId).session(stranger)).andExpect(status().isNotFound());
		this.mvc.perform(get("/api/findings").session(stranger)).andExpect(jsonPath("$.total").value(0));
		this.mvc
			.perform(multipart("/api/imports/orders").file(file("o.csv", ORDERS))
				.param("outletId", String.valueOf(outletId))
				.param("platform", "SWIGGY")
				.session(stranger)
				.with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void viewersCanReadButNotChange() throws Exception {
		MockHttpSession owner = signUp(uniqueEmail());
		this.mvc
			.perform(put("/api/organization/plan").contentType(MediaType.APPLICATION_JSON)
				.content("{\"plan\":\"GROWTH\"}")
				.session(owner)
				.with(csrf()))
			.andExpect(status().isOk());
		String viewerEmail = uniqueEmail();
		this.mvc
			.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"fullName\":\"Accountant\",\"password\":\"viewerpass1\",\"role\":\"VIEWER\"}"
					.formatted(viewerEmail))
				.session(owner)
				.with(csrf()))
			.andExpect(status().isCreated());
		MockHttpSession viewer = login(viewerEmail, "viewerpass1");
		this.mvc.perform(get("/api/dashboard").session(viewer)).andExpect(status().isOk());
		this.mvc
			.perform(multipart("/api/imports/orders").file(file("o.csv", ORDERS))
				.param("outletId", String.valueOf(firstOutlet(owner)))
				.param("platform", "SWIGGY")
				.session(viewer)
				.with(csrf()))
			.andExpect(status().isForbidden());
	}

	@Test
	void locksOutAfterRepeatedBadPasswords() throws Exception {
		String email = uniqueEmail();
		signUp(email);
		for (int i = 0; i < 5; i++) {
			this.mvc
				.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email))
					.with(csrf()))
				.andExpect(status().isUnauthorized());
		}
		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"correct-horse-1\"}".formatted(email))
				.with(csrf()))
			.andExpect(status().isTooManyRequests());
	}

	@Test
	void demoDataExercisesEveryKindOfCheck() {
		this.demoSeeder.seedNow(LocalDate.of(2026, 10, 23));
		Set<FindingType> found = this.jdbc
			.sql("SELECT DISTINCT f.type FROM finding f JOIN app_user u ON u.org_id = f.org_id WHERE u.email = :email")
			.param("email", DemoSeeder.DEMO_EMAIL)
			.query(String.class)
			.list()
			.stream()
			.map(FindingType::valueOf)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(FindingType.class)));
		assertThat(found).containsAll(EnumSet.complementOf(EnumSet.of(FindingType.TCS_OVERDEDUCTED)));
	}

	private MockHttpSession signUp(String email) throws Exception {
		MockHttpSession session = new MockHttpSession();
		this.mvc
			.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(email))
				.session(session)
				.with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.plan").value("STARTER"));
		return session;
	}

	private MockHttpSession login(String email, String password) throws Exception {
		MockHttpSession session = new MockHttpSession();
		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
				.session(session)
				.with(csrf()))
			.andExpect(status().isOk());
		return session;
	}

	private long firstOutlet(MockHttpSession session) throws Exception {
		String body = this.mvc.perform(get("/api/outlets").session(session)).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$[0].id")).longValue();
	}

	private static String signupJson(String email) {
		return "{\"businessName\":\"Test Dhaba\",\"fullName\":\"Asha Rao\",\"email\":\"%s\",\"password\":\"correct-horse-1\"}"
			.formatted(email);
	}

	private static String uniqueEmail() {
		return "owner-" + UUID.randomUUID() + "@example.com";
	}

	private static MockMultipartFile file(String name, String content) {
		return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
	}

}
