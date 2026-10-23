package com.liabletech.freight.fleet;

/** A driver, known by the WhatsApp number they send POD photos from. */
public record Driver(Long id, Long accountId, String name, String phone, Long vehicleId, boolean active) {
}
