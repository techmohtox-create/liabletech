package com.liabletech.payoutcheck.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payoutcheck.demo")
public record DemoProperties(boolean enabled) {
}
