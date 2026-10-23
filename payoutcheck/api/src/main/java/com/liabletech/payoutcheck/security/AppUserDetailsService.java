package com.liabletech.payoutcheck.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.liabletech.payoutcheck.account.AccountRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AccountRepository accounts;

	public AppUserDetailsService(AccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return this.accounts.findUserByEmail(email.trim().toLowerCase(Locale.ROOT))
			.map(CurrentUser::of)
			.orElseThrow(() -> new UsernameNotFoundException("No such user"));
	}

}
