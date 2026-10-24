package com.liabletech.platform.account;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.web.NotFoundException;

@Repository
public class Accounts {

	private final JdbcClient jdbc;

	public Accounts(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Account get(long id) {
		return find(id).orElseThrow(() -> new NotFoundException("Account " + id));
	}

	public Optional<Account> find(long id) {
		return jdbc.sql("SELECT * FROM account WHERE id = ?").param(id).query(Account.class).optional();
	}

	/** The business that owns a WhatsApp Business number, for messages sent to that number. */
	public Optional<Account> findByWhatsAppNumber(String phoneNumberId) {
		if (phoneNumberId == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM account WHERE whatsapp_phone_number_id = ?")
			.param(phoneNumberId)
			.query(Account.class)
			.optional();
	}

	public long create(String name, String ownerName, String phone, String email, String planCode,
			LocalDate trialEndsOn) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO account (name, owner_name, phone, email, plan_code, trial_ends_on)
				VALUES (:name, :ownerName, :phone, :email, :planCode, :trialEndsOn)
				""")
			.param("name", name)
			.param("ownerName", ownerName)
			.param("phone", phone)
			.param("email", email)
			.param("planCode", planCode)
			.param("trialEndsOn", trialEndsOn)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void setPlan(long id, String planCode) {
		jdbc.sql("UPDATE account SET plan_code = ? WHERE id = ?").params(planCode, id).update();
	}

	public void updateProfile(long id, ProfileForm form) {
		jdbc.sql("""
				UPDATE account SET name = :name, owner_name = :ownerName, phone = :phone, email = :email,
				    address = :address, gstin = :gstin, pan = :pan, upi_id = :upiId,
				    bank_account_name = :bankAccountName, bank_account_number = :bankAccountNumber,
				    bank_ifsc = :bankIfsc, bank_name = :bankName
				WHERE id = :id
				""")
			.param("id", id)
			.param("name", form.getName().trim())
			.param("ownerName", ProfileForm.clean(form.getOwnerName()))
			.param("phone", form.normalizedPhone())
			.param("email", ProfileForm.clean(form.getEmail()))
			.param("address", ProfileForm.clean(form.getAddress()))
			.param("gstin", ProfileForm.upper(form.getGstin()))
			.param("pan", ProfileForm.upper(form.getPan()))
			.param("upiId", ProfileForm.clean(form.getUpiId()))
			.param("bankAccountName", ProfileForm.clean(form.getBankAccountName()))
			.param("bankAccountNumber", ProfileForm.clean(form.getBankAccountNumber()))
			.param("bankIfsc", ProfileForm.upper(form.getBankIfsc()))
			.param("bankName", ProfileForm.clean(form.getBankName()))
			.update();
	}

	public void setWhatsAppNumber(long id, String phoneNumberId) {
		jdbc.sql("UPDATE account SET whatsapp_phone_number_id = ? WHERE id = ?")
			.params(phoneNumberId == null || phoneNumberId.isBlank() ? null : phoneNumberId.trim(), id)
			.update();
	}

}
