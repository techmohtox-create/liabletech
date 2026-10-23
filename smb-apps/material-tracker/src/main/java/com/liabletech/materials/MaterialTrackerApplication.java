package com.liabletech.materials;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Material Tracker: small builders stop paying for cement, steel and sand that never arrived.
 * Supervisors photograph each delivery slip on WhatsApp and confirm what they counted; supplier
 * bills are read and checked against those deliveries and the agreed rates.
 */
@SpringBootApplication(scanBasePackages = { "com.liabletech.platform", "com.liabletech.materials" })
public class MaterialTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MaterialTrackerApplication.class, args);
	}

}
