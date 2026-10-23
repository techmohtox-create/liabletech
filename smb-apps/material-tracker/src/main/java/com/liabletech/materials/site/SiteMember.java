package com.liabletech.materials.site;

/** A supervisor or storekeeper who sends delivery slips from a site's gate on WhatsApp. */
public record SiteMember(Long id, Long accountId, Long siteId, String name, String phone, String role, boolean active) {

	public static final String SUPERVISOR = "SUPERVISOR";

	public static final String STOREKEEPER = "STOREKEEPER";

}
