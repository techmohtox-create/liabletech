package com.liabletech.amc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.liabletech.platform", "com.liabletech.amc" })
public class AmcRenewalsApplication {

	public static void main(String[] args) {
		SpringApplication.run(AmcRenewalsApplication.class, args);
	}

}
