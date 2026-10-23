package com.liabletech.platform.account;

/**
 * Published after a business signs up, so each app can set up its defaults (AMC plans, a material
 * catalogue, follow-up settings) with an {@code @EventListener}.
 */
public record AccountCreated(long accountId, boolean demo) {
}
