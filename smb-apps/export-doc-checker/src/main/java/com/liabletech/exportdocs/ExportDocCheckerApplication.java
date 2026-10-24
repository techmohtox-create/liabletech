package com.liabletech.exportdocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "com.liabletech.platform", "com.liabletech.exportdocs" })
public class ExportDocCheckerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExportDocCheckerApplication.class, args);
	}

}
