package com.liabletech.payoutcheck.demo;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.liabletech.payoutcheck.account.AccountRepository;
import com.liabletech.payoutcheck.account.Plan;
import com.liabletech.payoutcheck.account.Role;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.ingest.OrderImportService;
import com.liabletech.payoutcheck.ingest.StatementImportService;
import com.liabletech.payoutcheck.ingest.StatementImportService.StatementDetails;
import com.liabletech.payoutcheck.outlet.OutletRepository;
import com.liabletech.payoutcheck.outlet.OutletService;
import com.liabletech.payoutcheck.recon.Finding;
import com.liabletech.payoutcheck.recon.FindingRepository;
import com.liabletech.payoutcheck.recon.FindingStatus;
import com.liabletech.payoutcheck.recon.FindingType;
import com.liabletech.payoutcheck.statements.PayoutStatement;
import com.liabletech.payoutcheck.statements.StatementRepository;

/**
 * Creates a demo business on first start so the app has something to show. The data goes through
 * the same import pipeline as an uploaded file. Turn off with {@code PAYOUTCHECK_DEMO=false}.
 */
@Component
public class DemoSeeder {

	public static final String DEMO_EMAIL = "demo@payoutcheck.local";

	public static final String DEMO_PASSWORD = "demo1234";

	private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

	private final DemoProperties properties;

	private final AccountRepository accounts;

	private final OutletService outletService;

	private final OutletRepository outlets;

	private final OrderImportService orderImports;

	private final StatementImportService statementImports;

	private final StatementRepository statements;

	private final FindingRepository findings;

	private final PasswordEncoder passwordEncoder;

	private final TransactionTemplate transactions;

	public DemoSeeder(DemoProperties properties, AccountRepository accounts, OutletService outletService,
			OutletRepository outlets, OrderImportService orderImports, StatementImportService statementImports,
			StatementRepository statements, FindingRepository findings, PasswordEncoder passwordEncoder,
			TransactionTemplate transactions) {
		this.properties = properties;
		this.accounts = accounts;
		this.outletService = outletService;
		this.outlets = outlets;
		this.orderImports = orderImports;
		this.statementImports = statementImports;
		this.statements = statements;
		this.findings = findings;
		this.passwordEncoder = passwordEncoder;
		this.transactions = transactions;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void seed() {
		if (!this.properties.enabled() || this.accounts.findUserByEmail(DEMO_EMAIL).isPresent()) {
			return;
		}
		this.transactions.executeWithoutResult((status) -> seedNow(LocalDate.now(ZoneId.of("Asia/Kolkata"))));
		log.info("Demo business created. Sign in with {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
	}

	public void seedNow(LocalDate today) {
		long orgId = this.accounts.insertOrganization("Spice Route Kitchens", Plan.GROWTH);
		long ownerId = this.accounts.insertUser(orgId, DEMO_EMAIL, "Priya Nair", this.passwordEncoder.encode(DEMO_PASSWORD),
				Role.OWNER);
		this.accounts.insertUser(orgId, "accounts@payoutcheck.local", "Ravi Kumar", this.passwordEncoder.encode(DEMO_PASSWORD),
				Role.VIEWER);
		long outletId = this.outletService.createFirst(orgId, "Spice Route, Indiranagar", "Bengaluru");
		this.outlets.update(orgId, outletId, "Spice Route, Indiranagar", "Bengaluru", "482913", "19876543", true);

		SampleData.Dataset data = SampleData.generate(today);
		this.orderImports.importOrders(orgId, ownerId, DEMO_EMAIL, outletId, null, "pos-orders-export.csv",
				data.ordersCsv().getBytes(StandardCharsets.UTF_8));
		for (SampleData.StatementFile file : data.statements()) {
			this.statementImports.importStatement(orgId, ownerId, DEMO_EMAIL, outletId,
					new StatementDetails(file.platform(), file.periodStart(), file.periodEnd(), file.payoutDate(), file.utr(),
							file.creditedAmount()),
					file.fileName(), file.csv().getBytes(StandardCharsets.UTF_8));
		}
		markHistory(orgId);
	}

	/** The oldest week has already been disputed, so the dashboard shows recoveries too. */
	private void markHistory(long orgId) {
		for (Platform platform : Platform.values()) {
			List<PayoutStatement> list = this.statements.list(orgId, null, platform);
			if (list.isEmpty()) {
				continue;
			}
			PayoutStatement oldest = list.get(list.size() - 1);
			String ticket = (platform == Platform.SWIGGY ? "SW-" : "ZM-") + "TKT-" + (40000 + oldest.id() * 7);
			for (Finding finding : this.findings.forStatement(oldest.id())) {
				FindingStatus status;
				if (finding.type() == FindingType.MISSING_ORDER || finding.type() == FindingType.COMMISSION_OVERCHARGE) {
					status = FindingStatus.RECOVERED;
				}
				else if (finding.type() == FindingType.CUSTOMER_REFUND) {
					status = FindingStatus.REJECTED;
				}
				else if (finding.type() == FindingType.AD_DEDUCTION) {
					status = FindingStatus.IGNORED;
				}
				else {
					status = FindingStatus.DISPUTED;
				}
				this.findings.updateStatus(finding.id(), status, status == FindingStatus.IGNORED ? null : ticket,
						status == FindingStatus.RECOVERED ? finding.amountAtRisk() : null,
						status == FindingStatus.IGNORED ? "Matches the ads dashboard invoice" : null);
			}
		}
	}

}
