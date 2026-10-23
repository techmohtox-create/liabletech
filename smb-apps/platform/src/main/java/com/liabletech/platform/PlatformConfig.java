package com.liabletech.platform;

import java.time.Clock;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wiring shared by every app. Apps pick it up by scanning {@code com.liabletech.platform} alongside
 * their own package: {@code @SpringBootApplication(scanBasePackages = {"com.liabletech.platform", ...})}.
 */
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:platform-defaults.properties")
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class PlatformConfig {

	/** Business time (due dates, reminders) runs on Indian Standard Time, whatever the server's zone. */
	@Bean
	Clock clock(AppProperties properties) {
		return Clock.system(properties.timezone());
	}

}
