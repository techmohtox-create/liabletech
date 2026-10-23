package com.liabletech.payoutcheck.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Session-cookie authentication for the single-page app. CSRF uses the SPA pattern: the token is
 * sent in an {@code XSRF-TOKEN} cookie and read back from the {@code X-XSRF-TOKEN} header, which
 * Angular's HttpClient does on its own.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((auth) -> auth
			.requestMatchers("/api/auth/login", "/api/auth/signup", "/api/auth/csrf", "/api/plans").permitAll()
			.requestMatchers("/actuator/health/**").permitAll()
			.requestMatchers("/api/**").authenticated()
			.anyRequest().permitAll())
			.csrf((csrf) -> csrf.spa())
			.exceptionHandling((ex) -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.logout((logout) -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.securityContext((context) -> context.securityContextRepository(securityContextRepository()))
			.headers((headers) -> headers
				.contentSecurityPolicy((csp) -> csp.policyDirectives(
						"default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; frame-ancestors 'none'"))
				.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
			.formLogin((form) -> form.disable())
			.httpBasic((basic) -> basic.disable());
		return http.build();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AppUserDetailsService users, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

}
