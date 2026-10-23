package com.liabletech.freight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Freight Chaser: small truck owners get paid faster. Drivers photograph the signed delivery
 * receipt on WhatsApp; the app reads it, bills the transporter or broker with the proof attached,
 * and keeps following up until the money arrives.
 */
@SpringBootApplication(scanBasePackages = { "com.liabletech.platform", "com.liabletech.freight" })
public class FreightChaserApplication {

	public static void main(String[] args) {
		SpringApplication.run(FreightChaserApplication.class, args);
	}

}
