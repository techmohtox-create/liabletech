package com.liabletech.platformtest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.PlanCatalog;
import com.liabletech.platform.web.AppNavigation;
import com.liabletech.platform.web.NavItem;
import com.liabletech.platform.whatsapp.InboundContext;
import com.liabletech.platform.whatsapp.InboundHandler;
import com.liabletech.platform.whatsapp.InboundRouter;
import com.liabletech.platform.whatsapp.SimulatorDirectory;

/** A minimal app on the platform, standing in for the real apps in the platform's own tests. */
@SpringBootApplication(scanBasePackages = { "com.liabletech.platform", "com.liabletech.platformtest" })
public class TestPlatformApplication {

	/** Phones that belong to an account, as an app would register its drivers. */
	static final Map<String, Long> KNOWN_PHONES = new java.util.concurrent.ConcurrentHashMap<>();

	@Bean
	AppNavigation navigation() {
		return () -> List.of(new NavItem("Home", "/"));
	}

	@Bean
	PlanCatalog plans() {
		return new PlanCatalog() {
			@Override
			public String defaultPlanCode() {
				return "TEST";
			}

			@Override
			public PlanSummary summarize(Account account) {
				return new PlanSummary("Test plan", "₹1/month", BigDecimal.ONE, List.of());
			}
		};
	}

	@Bean
	InboundRouter router() {
		return (phone) -> Optional.ofNullable(KNOWN_PHONES.get(phone));
	}

	@Bean
	RecordingHandler handler() {
		return new RecordingHandler();
	}

	@Bean
	SimulatorDirectory simulatorDirectory() {
		return new SimulatorDirectory() {
			@Override
			public List<Contact> contacts(long accountId) {
				return List.of(new Contact("Ramesh", "+919876543210", "Driver"));
			}

			@Override
			public List<Sample> samples(long accountId) {
				return List.of();
			}
		};
	}

	/** Records what it handled and answers "Got it". */
	public static class RecordingHandler implements InboundHandler {

		final List<InboundContext> handled = new CopyOnWriteArrayList<>();

		@Override
		public void handle(InboundContext context) {
			handled.add(context);
			context.reply("Got it: " + context.message().messageType());
		}

	}

}
