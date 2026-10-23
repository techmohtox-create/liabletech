package com.liabletech.materials.catalog;

import java.math.BigDecimal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.liabletech.platform.account.AccountCreated;

/**
 * The materials most small sites buy, with the names suppliers write on slips. Builders edit the
 * list; it only saves them typing it in on day one.
 */
@Component
public class DefaultCatalog {

	private final Materials materials;

	public DefaultCatalog(Materials materials) {
		this.materials = materials;
	}

	@EventListener
	public void onAccountCreated(AccountCreated event) {
		long account = event.accountId();
		BigDecimal fifty = BigDecimal.valueOf(50);
		materials.create(account, "Cement OPC 53", "CEMENT", Unit.BAG,
				"opc 53, opc53, opc 53 grade, ultratech opc, ambuja opc, acc opc, cement opc", fifty, null, BigDecimal.ZERO);
		materials.create(account, "Cement PPC", "CEMENT", Unit.BAG, "ppc, pozzolana, cement ppc, ultratech ppc", fifty, null,
				BigDecimal.ZERO);
		for (int diameter : new int[] { 8, 10, 12, 16, 20, 25 }) {
			materials.create(account, "TMT bar " + diameter + " mm", "STEEL", Unit.KG,
					"tmt, tmt bar, fe500, fe500d, fe 500, saria, rebar, steel bar", null, diameter, BigDecimal.valueOf(2));
		}
		materials.create(account, "River sand", "SAND", Unit.CFT, "river sand, natural sand, reti, ret", null, null,
				BigDecimal.valueOf(5));
		materials.create(account, "M-sand", "SAND", Unit.CFT, "m sand, msand, m-sand, crush sand, crushed sand, plaster sand",
				null, null, BigDecimal.valueOf(5));
		materials.create(account, "Aggregate 20 mm", "AGGREGATE", Unit.CFT, "20mm metal, 20 mm metal, 20mm aggregate, khadi, gitti",
				null, null, BigDecimal.valueOf(5));
		materials.create(account, "Aggregate 10 mm", "AGGREGATE", Unit.CFT, "10mm metal, 10 mm metal, 10mm aggregate, grit",
				null, null, BigDecimal.valueOf(5));
		materials.create(account, "Red bricks", "BRICKS", Unit.NOS, "red brick, red bricks, bricks, clay brick, int", null, null,
				BigDecimal.ONE);
		materials.create(account, "AAC blocks", "BRICKS", Unit.NOS, "aac, aac block, siporex, light weight block", null, null,
				BigDecimal.ONE);
		materials.create(account, "Ready-mix concrete M25", "RMC", Unit.CUM, "rmc, ready mix, readymix, m25, m 25", null, null,
				BigDecimal.ONE);
	}

}
