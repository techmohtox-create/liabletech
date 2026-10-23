package com.liabletech.payoutcheck.outlet;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.payoutcheck.common.Platform;

@Repository
public class OutletRepository {

	private final JdbcClient jdbc;

	public OutletRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public long insert(long orgId, String name, String city, String swiggyId, String zomatoId) {
		KeyHolder keys = new GeneratedKeyHolder();
		this.jdbc.sql("""
				INSERT INTO outlet (org_id, name, city, swiggy_id, zomato_id, active, created_at)
				VALUES (:orgId, :name, :city, :swiggyId, :zomatoId, TRUE, :now)
				""")
			.param("orgId", orgId)
			.param("name", name)
			.param("city", city)
			.param("swiggyId", swiggyId)
			.param("zomatoId", zomatoId)
			.param("now", OffsetDateTime.now(ZoneOffset.UTC))
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void update(long orgId, long id, String name, String city, String swiggyId, String zomatoId, boolean active) {
		this.jdbc.sql("""
				UPDATE outlet SET name = :name, city = :city, swiggy_id = :swiggyId, zomato_id = :zomatoId, active = :active
				WHERE id = :id AND org_id = :orgId
				""")
			.param("name", name)
			.param("city", city)
			.param("swiggyId", swiggyId)
			.param("zomatoId", zomatoId)
			.param("active", active)
			.param("id", id)
			.param("orgId", orgId)
			.update();
	}

	public Optional<Outlet> find(long orgId, long id) {
		return this.jdbc.sql("SELECT * FROM outlet WHERE id = :id AND org_id = :orgId")
			.param("id", id)
			.param("orgId", orgId)
			.query(Outlet.class)
			.optional();
	}

	public List<Outlet> list(long orgId) {
		return this.jdbc.sql("SELECT * FROM outlet WHERE org_id = :orgId ORDER BY name").param("orgId", orgId).query(Outlet.class).list();
	}

	public int countActive(long orgId) {
		return this.jdbc.sql("SELECT COUNT(*) FROM outlet WHERE org_id = :orgId AND active = TRUE")
			.param("orgId", orgId)
			.query(Integer.class)
			.single();
	}

	public List<PlatformTerms> terms(long outletId) {
		return this.jdbc.sql("SELECT * FROM platform_terms WHERE outlet_id = :outletId ORDER BY platform")
			.param("outletId", outletId)
			.query(PlatformTerms.class)
			.list();
	}

	public Optional<PlatformTerms> terms(long outletId, Platform platform) {
		return this.jdbc.sql("SELECT * FROM platform_terms WHERE outlet_id = :outletId AND platform = :platform")
			.param("outletId", outletId)
			.param("platform", platform.name())
			.query(PlatformTerms.class)
			.optional();
	}

	/** Inserts or replaces the outlet's terms for one platform. */
	public void saveTerms(PlatformTerms terms) {
		int updated = this.jdbc.sql("""
				UPDATE platform_terms SET commission_pct = :commission, pg_fee_pct = :pg, gst_on_fees_pct = :gst,
				    tcs_pct = :tcs, tds_pct = :tds, commission_on_packaging = :onPackaging, tolerance = :tolerance,
				    updated_at = :now
				WHERE outlet_id = :outletId AND platform = :platform
				""")
			.paramSource(termsParams(terms))
			.update();
		if (updated == 0) {
			this.jdbc.sql("""
					INSERT INTO platform_terms (outlet_id, platform, commission_pct, pg_fee_pct, gst_on_fees_pct, tcs_pct, tds_pct,
					    commission_on_packaging, tolerance, updated_at)
					VALUES (:outletId, :platform, :commission, :pg, :gst, :tcs, :tds, :onPackaging, :tolerance, :now)
					""")
				.paramSource(termsParams(terms))
				.update();
		}
	}

	private static Map<String, Object> termsParams(PlatformTerms terms) {
		return Map.of("outletId", terms.outletId(), "platform", terms.platform().name(), "commission",
				terms.commissionPct(), "pg", terms.pgFeePct(), "gst", terms.gstOnFeesPct(), "tcs", terms.tcsPct(), "tds",
				terms.tdsPct(), "onPackaging", terms.commissionOnPackaging(), "tolerance", terms.tolerance(), "now",
				OffsetDateTime.now(ZoneOffset.UTC));
	}

}
