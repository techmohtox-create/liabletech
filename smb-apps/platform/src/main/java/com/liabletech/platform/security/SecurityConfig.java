package com.liabletech.platform.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.liabletech.platform.AppProperties;
import com.liabletech.platform.account.Users;

/**
 * Owners and staff sign in with email and password. The WhatsApp webhook, public booking pages
 * ({@code /p/**}) and static files are open; everything else needs a login.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users, AppProperties properties)
			throws Exception {
		String rememberMeKey = rememberMeKey(properties.dataDir());
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers("/login", "/signup", "/css/**", "/js/**", "/favicon.svg", "/webhooks/**", "/p/**",
					"/actuator/health", "/error")
			.permitAll()
			.anyRequest()
			.authenticated())
			.formLogin((form) -> form.loginPage("/login").defaultSuccessUrl("/", false).permitAll())
			.rememberMe((remember) -> remember.key(rememberMeKey)
				.userDetailsService(users)
				.tokenValiditySeconds(60 * 60 * 24 * 30))
			.logout((logout) -> logout.logoutSuccessUrl("/login?logout"))
			// Meta signs webhook calls instead (X-Hub-Signature-256), so they carry no CSRF token.
			.csrf((csrf) -> csrf.ignoringRequestMatchers("/webhooks/**"))
			.headers((headers) -> headers.contentSecurityPolicy((csp) -> csp.policyDirectives(
					"default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; frame-ancestors 'self'; form-action 'self'")));
		return http.build();
	}

	@Bean
	UserDetailsService userDetailsService(Users users) {
		return (email) -> users.findByEmail(email)
			.map(CurrentUser::new)
			.orElseThrow(() -> new UsernameNotFoundException("No user " + email));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * Remember-me cookies are signed with a key kept in the data directory, so a restart does not
	 * sign everyone out.
	 */
	private static String rememberMeKey(Path dataDir) throws IOException {
		Path file = dataDir.resolve("remember-me.key");
		if (Files.exists(file)) {
			return Files.readString(file).trim();
		}
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Files.createDirectories(dataDir);
		Files.writeString(file, key);
		return key;
	}

}
