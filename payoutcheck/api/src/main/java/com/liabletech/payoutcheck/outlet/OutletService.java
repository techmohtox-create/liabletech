package com.liabletech.payoutcheck.outlet;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.liabletech.payoutcheck.account.AccountRepository;
import com.liabletech.payoutcheck.account.Plan;
import com.liabletech.payoutcheck.audit.AuditService;
import com.liabletech.payoutcheck.common.BadRequestException;
import com.liabletech.payoutcheck.common.NotFoundException;
import com.liabletech.payoutcheck.common.Platform;
import com.liabletech.payoutcheck.security.CurrentUser;

@Service
public class OutletService {

	private final OutletRepository outlets;

	private final AccountRepository accounts;

	private final AuditService audit;

	public OutletService(OutletRepository outlets, AccountRepository accounts, AuditService audit) {
		this.outlets = outlets;
		this.accounts = accounts;
		this.audit = audit;
	}

	public List<Outlet> list(long orgId) {
		return this.outlets.list(orgId);
	}

	public Outlet get(long orgId, long id) {
		return this.outlets.find(orgId, id).orElseThrow(() -> new NotFoundException("Outlet"));
	}

	@Transactional
	public Outlet create(CurrentUser actor, OutletRequest request) {
		requireCapacity(actor.orgId());
		long id = this.outlets.insert(actor.orgId(), request.name().trim(), trim(request.city()), trim(request.swiggyId()),
				trim(request.zomatoId()));
		for (Platform platform : Platform.values()) {
			this.outlets.saveTerms(PlatformTerms.defaults(id, platform));
		}
		this.audit.record(actor, "outlet.create", "outlet", id, request.name().trim());
		return get(actor.orgId(), id);
	}

	/** Used by sign-up and demo seeding, where there is no request user yet. */
	@Transactional
	public long createFirst(long orgId, String name, String city) {
		long id = this.outlets.insert(orgId, name, city, null, null);
		for (Platform platform : Platform.values()) {
			this.outlets.saveTerms(PlatformTerms.defaults(id, platform));
		}
		return id;
	}

	@Transactional
	public Outlet update(CurrentUser actor, long id, OutletRequest request) {
		Outlet outlet = get(actor.orgId(), id);
		boolean active = request.active() == null || request.active();
		if (active && !outlet.active()) {
			requireCapacity(actor.orgId());
		}
		this.outlets.update(actor.orgId(), id, request.name().trim(), trim(request.city()), trim(request.swiggyId()),
				trim(request.zomatoId()), active);
		this.audit.record(actor, "outlet.update", "outlet", id, request.name().trim() + (active ? "" : " (deactivated)"));
		return get(actor.orgId(), id);
	}

	public List<PlatformTerms> terms(long orgId, long outletId) {
		get(orgId, outletId);
		return this.outlets.terms(outletId);
	}

	public PlatformTerms termsFor(long outletId, Platform platform) {
		return this.outlets.terms(outletId, platform).orElseGet(() -> PlatformTerms.defaults(outletId, platform));
	}

	@Transactional
	public PlatformTerms saveTerms(CurrentUser actor, long outletId, Platform platform, TermsRequest request) {
		get(actor.orgId(), outletId);
		PlatformTerms terms = new PlatformTerms(0, outletId, platform, request.commissionPct(), request.pgFeePct(),
				request.gstOnFeesPct(), request.tcsPct(), request.tdsPct(), request.commissionOnPackaging(), request.tolerance(),
				null);
		this.outlets.saveTerms(terms);
		this.audit.record(actor, "terms.update", "outlet", outletId,
				"%s commission %s%%, PG %s%%, GST %s%%, TCS %s%%, TDS %s%%".formatted(platform.label(), request.commissionPct(),
						request.pgFeePct(), request.gstOnFeesPct(), request.tcsPct(), request.tdsPct()));
		return termsFor(outletId, platform);
	}

	public int activeCount(long orgId) {
		return this.outlets.countActive(orgId);
	}

	private void requireCapacity(long orgId) {
		Plan plan = this.accounts.findOrganization(orgId).orElseThrow().plan();
		if (this.outlets.countActive(orgId) >= plan.maxOutlets()) {
			throw new BadRequestException("The %s plan covers %d outlet(s). Upgrade to add more.".formatted(plan.label(),
					plan.maxOutlets()));
		}
	}

	private static String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
