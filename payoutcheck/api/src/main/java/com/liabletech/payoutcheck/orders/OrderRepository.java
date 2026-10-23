package com.liabletech.payoutcheck.orders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.liabletech.payoutcheck.common.PageResult;
import com.liabletech.payoutcheck.common.Platform;

@Repository
public class OrderRepository {

	private static final String COLUMNS = "id, outlet_id, platform, order_ref, ordered_at, status, item_total, packaging, restaurant_discount";

	private final JdbcClient jdbc;

	private final NamedParameterJdbcTemplate named;

	public OrderRepository(JdbcClient jdbc, NamedParameterJdbcTemplate named) {
		this.jdbc = jdbc;
		this.named = named;
	}

	/** Inserts new orders and overwrites ones already imported. Returns how many were new. */
	public int upsert(long orgId, long importId, List<OrderRecord> orders) {
		Set<String> existing = new HashSet<>();
		Map<String, List<OrderRecord>> groups = new HashMap<>();
		for (OrderRecord order : orders) {
			groups.computeIfAbsent(order.outletId() + ":" + order.platform(), (k) -> new ArrayList<>()).add(order);
		}
		for (List<OrderRecord> group : groups.values()) {
			OrderRecord first = group.get(0);
			Collection<String> refs = group.stream().map(OrderRecord::orderRef).toList();
			findByRefs(first.outletId(), first.platform(), refs).keySet()
				.forEach((ref) -> existing.add(first.outletId() + ":" + first.platform() + ":" + ref));
		}
		List<MapSqlParameterSource> inserts = new ArrayList<>();
		List<MapSqlParameterSource> updates = new ArrayList<>();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		for (OrderRecord order : orders) {
			MapSqlParameterSource params = new MapSqlParameterSource().addValue("orgId", orgId)
				.addValue("outletId", order.outletId())
				.addValue("platform", order.platform().name())
				.addValue("ref", order.orderRef())
				.addValue("orderedAt", order.orderedAt())
				.addValue("status", order.status().name())
				.addValue("itemTotal", order.itemTotal())
				.addValue("packaging", order.packaging())
				.addValue("discount", order.restaurantDiscount())
				.addValue("importId", importId)
				.addValue("now", now);
			if (existing.contains(order.outletId() + ":" + order.platform() + ":" + order.orderRef())) {
				updates.add(params);
			}
			else {
				inserts.add(params);
			}
		}
		if (!inserts.isEmpty()) {
			this.named.batchUpdate("""
					INSERT INTO order_record (org_id, outlet_id, platform, order_ref, ordered_at, status, item_total, packaging,
					    restaurant_discount, import_id, created_at)
					VALUES (:orgId, :outletId, :platform, :ref, :orderedAt, :status, :itemTotal, :packaging, :discount, :importId, :now)
					""", inserts.toArray(MapSqlParameterSource[]::new));
		}
		if (!updates.isEmpty()) {
			this.named.batchUpdate("""
					UPDATE order_record SET ordered_at = :orderedAt, status = :status, item_total = :itemTotal, packaging = :packaging,
					    restaurant_discount = :discount, import_id = :importId
					WHERE outlet_id = :outletId AND platform = :platform AND order_ref = :ref
					""", updates.toArray(MapSqlParameterSource[]::new));
		}
		return inserts.size();
	}

	public Map<String, OrderRecord> findByRefs(long outletId, Platform platform, Collection<String> refs) {
		Map<String, OrderRecord> found = new HashMap<>();
		for (List<String> chunk : chunks(new ArrayList<>(new HashSet<>(refs)), 500)) {
			this.jdbc.sql("SELECT " + COLUMNS + " FROM order_record WHERE outlet_id = :outletId AND platform = :platform AND order_ref IN (:refs)")
				.param("outletId", outletId)
				.param("platform", platform.name())
				.param("refs", chunk)
				.query(OrderRecord.class)
				.list()
				.forEach((order) -> found.put(order.orderRef(), order));
		}
		return found;
	}

	/** Delivered orders placed in the period (dates inclusive). */
	public List<OrderRecord> deliveredBetween(long outletId, Platform platform, LocalDate from, LocalDate to) {
		return this.jdbc.sql("SELECT " + COLUMNS + """
				 FROM order_record WHERE outlet_id = :outletId AND platform = :platform AND status = 'DELIVERED'
				AND ordered_at >= :from AND ordered_at < :to ORDER BY ordered_at
				""")
			.param("outletId", outletId)
			.param("platform", platform.name())
			.param("from", from.atStartOfDay())
			.param("to", to.plusDays(1).atStartOfDay())
			.query(OrderRecord.class)
			.list();
	}

	public PageResult<OrderRecord> search(long orgId, Long outletId, Platform platform, String status, String query,
			LocalDate from, LocalDate to, int page, int size) {
		StringBuilder where = new StringBuilder(" WHERE org_id = :orgId");
		MapSqlParameterSource params = new MapSqlParameterSource("orgId", orgId);
		if (outletId != null) {
			where.append(" AND outlet_id = :outletId");
			params.addValue("outletId", outletId);
		}
		if (platform != null) {
			where.append(" AND platform = :platform");
			params.addValue("platform", platform.name());
		}
		if (status != null && !status.isBlank()) {
			where.append(" AND status = :status");
			params.addValue("status", status);
		}
		if (query != null && !query.isBlank()) {
			where.append(" AND LOWER(order_ref) LIKE :query");
			params.addValue("query", "%" + query.trim().toLowerCase() + "%");
		}
		if (from != null) {
			where.append(" AND ordered_at >= :from");
			params.addValue("from", from.atStartOfDay());
		}
		if (to != null) {
			where.append(" AND ordered_at < :to");
			params.addValue("to", to.plusDays(1).atStartOfDay());
		}
		params.addValue("limit", size).addValue("offset", page * size);
		List<OrderRecord> items = this.jdbc.sql("SELECT " + COLUMNS + " FROM order_record" + where
				+ " ORDER BY ordered_at DESC, id DESC LIMIT :limit OFFSET :offset")
			.paramSource(params)
			.query(OrderRecord.class)
			.list();
		long total = this.jdbc.sql("SELECT COUNT(*) FROM order_record" + where).paramSource(params).query(Long.class).single();
		return new PageResult<>(items, total, page, size);
	}

	public LocalDateTime latestOrder(long orgId) {
		return this.jdbc.sql("SELECT MAX(ordered_at) FROM order_record WHERE org_id = :orgId")
			.param("orgId", orgId)
			.query(LocalDateTime.class)
			.optional()
			.orElse(null);
	}

	static <T> List<List<T>> chunks(List<T> list, int size) {
		List<List<T>> chunks = new ArrayList<>();
		for (int i = 0; i < list.size(); i += size) {
			chunks.add(list.subList(i, Math.min(list.size(), i + size)));
		}
		return chunks;
	}

}
