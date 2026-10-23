package com.liabletech.platform.whatsapp;

import java.util.List;
import java.util.function.Supplier;

/**
 * What the local WhatsApp simulator offers for an app: the phones that can "send" messages (the
 * account's drivers, technicians, mechanics...) and sample documents to send with one click.
 */
public interface SimulatorDirectory {

	List<Contact> contacts(long accountId);

	List<Sample> samples(long accountId);

	record Contact(String name, String phone, String role) {
	}

	/** A generated sample: a POD photo, a delivery challan, an appliance label, a worn brake pad. */
	record Sample(String id, String label, String caption, Supplier<SampleFile> file) {
	}

	record SampleFile(byte[] bytes, String contentType, String filename) {
	}

}
