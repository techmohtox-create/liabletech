package com.liabletech.payoutcheck.security;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.liabletech.payoutcheck.account.AccountRepository;
import com.liabletech.payoutcheck.account.AccountService;
import com.liabletech.payoutcheck.account.Organization;
import com.liabletech.payoutcheck.audit.AuditService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;

	private final SecurityContextRepository contextRepository;

	private final LoginAttempts attempts;

	private final AccountService accountService;

	private final AccountRepository accounts;

	private final AuditService audit;

	public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
			LoginAttempts attempts, AccountService accountService, AccountRepository accounts, AuditService audit) {
		this.authenticationManager = authenticationManager;
		this.contextRepository = contextRepository;
		this.attempts = attempts;
		this.accountService = accountService;
		this.accounts = accounts;
		this.audit = audit;
	}

	/** Touching the token makes Spring Security send the XSRF-TOKEN cookie before the first POST. */
	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (this.attempts.isLocked(email)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
						"Too many failed attempts. Try again in 15 minutes."));
		}
		Authentication authentication;
		try {
			authentication = this.authenticationManager
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
		}
		catch (AuthenticationException ex) {
			this.attempts.failed(email);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
		}
		this.attempts.succeeded(email);
		establishSession(authentication, httpRequest, httpResponse);
		CurrentUser user = (CurrentUser) authentication.getPrincipal();
		this.accounts.touchLogin(user.id());
		this.audit.record(user, "auth.login", "app_user", user.id(), null);
		return ResponseEntity.ok(me(user));
	}

	@PostMapping("/signup")
	public ResponseEntity<Me> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		long userId = this.accountService.signUp(request.businessName(), request.outletName(), request.fullName(),
				request.email(), request.password());
		CurrentUser user = CurrentUser.of(this.accounts.findUserByEmail(request.email().trim().toLowerCase(Locale.ROOT))
			.orElseThrow());
		establishSession(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()), httpRequest,
				httpResponse);
		this.accounts.touchLogin(userId);
		return ResponseEntity.status(HttpStatus.CREATED).body(me(user));
	}

	@GetMapping("/me")
	public Me me(@AuthenticationPrincipal CurrentUser user) {
		Organization org = this.accountService.organization(user.orgId());
		return new Me(user.id(), user.email(), user.fullName(), user.role().name(), org.id(), org.name(), org.plan().name());
	}

	private void establishSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		this.contextRepository.saveContext(context, request, response);
	}

	public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
	}

	public record SignupRequest(@NotBlank @Size(max = 160) String businessName, @Size(max = 120) String outletName,
			@NotBlank @Size(max = 120) String fullName, @NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 8, max = 100) String password) {
	}

	public record Me(long id, String email, String fullName, String role, long orgId, String orgName, String plan) {
	}

}
