package com.liabletech.parts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.liabletech.platform", "com.liabletech.parts" })
public class PartFinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(PartFinderApplication.class, args);
	}

}
