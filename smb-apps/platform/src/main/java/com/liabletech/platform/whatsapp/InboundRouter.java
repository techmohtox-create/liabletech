package com.liabletech.platform.whatsapp;

import java.util.Optional;

/**
 * Finds which business a WhatsApp message belongs to when it arrives on the shared platform
 * number: each app looks the sender up among its registered drivers, supervisors, technicians or
 * mechanics. Messages to a business's own number never get here; the number decides.
 */
public interface InboundRouter {

	Optional<Long> accountFor(String fromPhone);

}
