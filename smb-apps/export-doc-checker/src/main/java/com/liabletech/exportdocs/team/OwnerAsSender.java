package com.liabletech.exportdocs.team;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.AccountCreated;
import com.liabletech.platform.account.Accounts;

/** The owner's mobile can send documents from day one. */
@Component
public class OwnerAsSender {

	private final Accounts accounts;

	private final Senders senders;

	public OwnerAsSender(Accounts accounts, Senders senders) {
		this.accounts = accounts;
		this.senders = senders;
	}

	@EventListener
	public void onAccountCreated(AccountCreated event) {
		Account account = accounts.get(event.accountId());
		if (account.phone() != null && senders.byPhone(account.phone()).isEmpty()) {
			senders.add(account.id(), account.ownerName() == null ? account.name() : account.ownerName(), account.phone());
		}
	}

}
