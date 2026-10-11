package com.liabletech.bff.landing;

import java.util.List;

public record LandingContent(Hero hero, List<Service> services, List<Principle> principles,
		List<ProcessStep> process, List<TechnologyGroup> technologies, Contact contact) {

	public record Hero(String eyebrow, String headline, String subheadline, Cta primaryCta, Cta secondaryCta) {
	}

	public record Cta(String label, String target) {
	}

	public record Service(String id, String icon, String title, String summary, List<String> capabilities) {
	}

	public record Principle(String title, String description) {
	}

	public record ProcessStep(String title, String outcome, String description) {
	}

	public record TechnologyGroup(String category, List<String> items) {
	}

	public record Contact(String heading, String blurb, String email) {
	}

}
