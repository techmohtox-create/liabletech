package com.liabletech.materials.site;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.liabletech.materials.catalog.Material;
import com.liabletech.materials.catalog.Materials;
import com.liabletech.materials.catalog.Unit;

/**
 * Per site and material: how much arrived (counted at the gate) against how much was billed. A
 * billed quantity above the received one is money paid, or about to be paid, for nothing.
 */
@Service
public class SiteLedger {

	private final JdbcClient jdbc;

	private final Materials materials;

	public SiteLedger(JdbcClient jdbc, Materials materials) {
		this.jdbc = jdbc;
		this.materials = materials;
	}

	public List<Row> forSite(long accountId, long siteId) {
		Map<Long, BigDecimal> received = new LinkedHashMap<>();
		Map<Long, BigDecimal> billed = new LinkedHashMap<>();
		Map<Long, BigDecimal> billedAmount = new LinkedHashMap<>();
		Map<Long, Material> byId = new LinkedHashMap<>();
		materials.all(accountId).forEach((m) -> byId.put(m.id(), m));
		for (Quantity q : jdbc.sql("""
				SELECT dl.material_id, dl.unit, COALESCE(dl.received_qty, dl.slip_qty) AS qty, CAST(NULL AS NUMERIC(14, 2)) AS amount
				FROM delivery_line dl JOIN delivery d ON d.id = dl.delivery_id
				WHERE d.account_id = ? AND d.site_id = ? AND d.status = 'CONFIRMED' AND dl.material_id IS NOT NULL
				""").params(accountId, siteId).query(Quantity.class).list()) {
			add(received, byId.get(q.materialId()), q);
		}
		for (Quantity q : jdbc.sql("""
				SELECT bl.material_id, bl.unit, bl.qty, bl.amount
				FROM bill_line bl JOIN supplier_bill b ON b.id = bl.bill_id
				WHERE b.account_id = ? AND b.site_id = ? AND b.status IN ('CHECKED', 'APPROVED', 'DISPUTED', 'PAID')
				    AND bl.material_id IS NOT NULL
				""").params(accountId, siteId).query(Quantity.class).list()) {
			add(billed, byId.get(q.materialId()), q);
			if (q.amount() != null) {
				billedAmount.merge(q.materialId(), q.amount(), BigDecimal::add);
			}
		}
		List<Row> rows = new ArrayList<>();
		for (Material material : byId.values()) {
			BigDecimal got = received.get(material.id());
			BigDecimal charged = billed.get(material.id());
			if (got == null && charged == null) {
				continue;
			}
			rows.add(new Row(material, got == null ? BigDecimal.ZERO : got, charged == null ? BigDecimal.ZERO : charged,
					billedAmount.getOrDefault(material.id(), BigDecimal.ZERO)));
		}
		return rows;
	}

	private static void add(Map<Long, BigDecimal> totals, Material material, Quantity q) {
		if (material == null || q.qty() == null) {
			return;
		}
		BigDecimal base = Unit.convert(q.qty(), Unit.parse(q.unit()), material.baseUnit(), material);
		if (base != null) {
			totals.merge(material.id(), base, BigDecimal::add);
		}
	}

	record Quantity(Long materialId, String unit, BigDecimal qty, BigDecimal amount) {
	}

	public record Row(Material material, BigDecimal received, BigDecimal billed, BigDecimal billedAmount) {

		/** Billed beyond what arrived, in the material's unit. */
		public BigDecimal excess() {
			return billed.subtract(received).max(BigDecimal.ZERO);
		}

	}

}
