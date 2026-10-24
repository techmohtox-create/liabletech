package com.liabletech.parts.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.liabletech.platform.text.Fuzzy;
import com.liabletech.platform.web.NotFoundException;

@Repository
public class Parts {

	private final JdbcClient jdbc;

	public Parts(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Part get(long accountId, long id) {
		return find(accountId, id).orElseThrow(() -> new NotFoundException("Part " + id));
	}

	public Optional<Part> find(long accountId, Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM part WHERE account_id = ? AND id = ?").params(accountId, id).query(Part.class).optional();
	}

	public Optional<Part> byNumber(long accountId, String partNumber) {
		if (partNumber == null || partNumber.isBlank()) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT * FROM part WHERE account_id = ? AND UPPER(part_number) = ?")
			.params(accountId, partNumber.trim().toUpperCase())
			.query(Part.class)
			.optional();
	}

	/**
	 * Parts that could answer an enquiry: everything of the type asked for (and types that can stand
	 * in for it), and anything whose own
	 * or replaced number contains one of the numbers seen. Inactive parts are left out.
	 */
	public List<Part> candidates(long accountId, PartType type, Collection<String> numbers) {
		Map<Long, Part> found = new LinkedHashMap<>();
		if (type != null && type != PartType.OTHER) {
			List<String> categories = new ArrayList<>(List.of(type.name()));
			type.related().forEach((related) -> categories.add(related.name()));
			jdbc.sql("SELECT * FROM part WHERE account_id = :accountId AND active = TRUE AND category IN (:categories)")
				.param("accountId", accountId)
				.param("categories", categories)
				.query(Part.class)
				.list()
				.forEach((p) -> found.put(p.id(), p));
		}
		for (String number : numbers) {
			String code = Fuzzy.code(number);
			if (code.length() < 4) {
				continue;
			}
			jdbc.sql("""
					SELECT * FROM part WHERE account_id = ? AND active = TRUE
					    AND (REPLACE(REPLACE(REPLACE(UPPER(part_number), '-', ''), ' ', ''), '/', '') LIKE ?
					        OR REPLACE(REPLACE(REPLACE(UPPER(COALESCE(alt_numbers, '')), '-', ''), ' ', ''), '/', '') LIKE ?)
					""")
				.params(accountId, "%" + code + "%", "%" + code + "%")
				.query(Part.class)
				.list()
				.forEach((p) -> found.putIfAbsent(p.id(), p));
		}
		return new ArrayList<>(found.values());
	}

	/** Free-text search at the counter: number, name, brand or a vehicle it fits. */
	public List<Part> search(long accountId, String text, int limit) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String like = "%" + text.trim().toLowerCase() + "%";
		String code = "%" + Fuzzy.code(text) + "%";
		return jdbc.sql("""
				SELECT * FROM part p WHERE p.account_id = :accountId AND p.active = TRUE
				    AND (LOWER(p.name) LIKE :like OR LOWER(COALESCE(p.brand, '')) LIKE :like
				        OR REPLACE(REPLACE(UPPER(p.part_number), '-', ''), ' ', '') LIKE :code
				        OR REPLACE(REPLACE(UPPER(COALESCE(p.alt_numbers, '')), '-', ''), ' ', '') LIKE :code
				        OR EXISTS (SELECT 1 FROM fitment f WHERE f.part_id = p.id AND LOWER(f.model) LIKE :like))
				ORDER BY p.stock > 0 DESC, p.name
				LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("like", like)
			.param("code", code)
			.param("limit", limit)
			.query(Part.class)
			.list();
	}

	/**
	 * @param filter "out" for out of stock, "low" for two or fewer, anything else for all
	 */
	public List<Part> list(long accountId, String search, String category, String filter, int limit) {
		String like = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
		return jdbc.sql("""
				SELECT * FROM part p WHERE p.account_id = :accountId AND p.active = TRUE
				    AND (CAST(:category AS VARCHAR(30)) IS NULL OR p.category = :category)
				    AND (:filter <> 'out' OR p.stock <= 0)
				    AND (:filter <> 'low' OR p.stock <= 2)
				    AND (CAST(:search AS VARCHAR(200)) IS NULL OR LOWER(p.name) LIKE :search
				        OR LOWER(p.part_number) LIKE :search OR LOWER(COALESCE(p.brand, '')) LIKE :search
				        OR LOWER(COALESCE(p.alt_numbers, '')) LIKE :search
				        OR EXISTS (SELECT 1 FROM fitment f WHERE f.part_id = p.id AND LOWER(f.model) LIKE :search))
				ORDER BY p.category, p.name
				LIMIT :limit
				""")
			.param("accountId", accountId)
			.param("category", category == null || category.isBlank() ? null : category)
			.param("filter", filter == null ? "" : filter)
			.param("search", like)
			.param("limit", limit)
			.query(Part.class)
			.list();
	}

	public long create(long accountId, PartData data, OffsetDateTime at) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				INSERT INTO part (account_id, part_number, name, category, brand, alt_numbers, price, mrp, stock, rack,
				    created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""")
			.params(accountId, data.partNumber().trim(), data.name().trim(), data.type().name(), blank(data.brand()),
					blank(data.altNumbers()), data.price(), data.mrp(), data.stock(), blank(data.rack()), at, at)
			.update(keys, "id");
		return keys.getKeyAs(Long.class);
	}

	public void update(long accountId, long id, PartData data, OffsetDateTime at) {
		jdbc.sql("""
				UPDATE part SET part_number = ?, name = ?, category = ?, brand = ?, alt_numbers = ?, price = ?, mrp = ?,
				    stock = ?, rack = ?, active = TRUE, updated_at = ?
				WHERE account_id = ? AND id = ?
				""")
			.params(data.partNumber().trim(), data.name().trim(), data.type().name(), blank(data.brand()),
					blank(data.altNumbers()), data.price(), data.mrp(), data.stock(), blank(data.rack()), at, accountId, id)
			.update();
	}

	public void deactivate(long accountId, long id, OffsetDateTime at) {
		jdbc.sql("UPDATE part SET active = FALSE, updated_at = ? WHERE account_id = ? AND id = ?")
			.params(at, accountId, id)
			.update();
	}

	/** Adds to (or with a negative change takes from) the stock; false if that would go below zero. */
	public boolean changeStock(long accountId, long id, int change, OffsetDateTime at) {
		return jdbc.sql("""
				UPDATE part SET stock = stock + ?, updated_at = ? WHERE account_id = ? AND id = ? AND stock + ? >= 0
				""").params(change, at, accountId, id, change).update() == 1;
	}

	public void setStock(long accountId, long id, int stock, OffsetDateTime at) {
		jdbc.sql("UPDATE part SET stock = ?, updated_at = ? WHERE account_id = ? AND id = ?")
			.params(Math.max(0, stock), at, accountId, id)
			.update();
	}

	public List<Fitment> fitments(long partId) {
		return jdbc.sql("SELECT * FROM fitment WHERE part_id = ? ORDER BY model, year_from").param(partId)
			.query(Fitment.class)
			.list();
	}

	/** The fitments of several parts at once, by part. */
	public Map<Long, List<Fitment>> fitments(Collection<Long> partIds) {
		if (partIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("SELECT * FROM fitment WHERE part_id IN (:ids) ORDER BY model, year_from")
			.param("ids", partIds)
			.query(Fitment.class)
			.list()
			.stream()
			.collect(Collectors.groupingBy(Fitment::partId, LinkedHashMap::new, Collectors.toList()));
	}

	public void replaceFitments(long partId, List<Fitment> fitments) {
		jdbc.sql("DELETE FROM fitment WHERE part_id = ?").param(partId).update();
		for (Fitment fitment : fitments) {
			jdbc.sql("INSERT INTO fitment (part_id, make, model, year_from, year_to, variant) VALUES (?, ?, ?, ?, ?, ?)")
				.params(partId, fitment.make(), fitment.model(), fitment.yearFrom(), fitment.yearTo(), fitment.variant())
				.update();
		}
	}

	public long countActive(long accountId) {
		return jdbc.sql("SELECT COUNT(*) FROM part WHERE account_id = ? AND active = TRUE").param(accountId)
			.query(Long.class)
			.single();
	}

	/** Parts booked lately that are down to their last one or two: what to reorder. */
	public List<Part> runningLow(long accountId, OffsetDateTime bookedSince, int limit) {
		return jdbc.sql("""
				SELECT p.* FROM part p WHERE p.account_id = ? AND p.active = TRUE AND p.stock <= 2
				    AND EXISTS (SELECT 1 FROM booking b WHERE b.part_id = p.id AND b.status <> 'CANCELLED' AND b.created_at >= ?)
				ORDER BY p.stock, p.name LIMIT ?
				""").params(accountId, bookedSince, limit).query(Part.class).list();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	/** What the stock form and the CSV import give for a part. */
	public record PartData(String partNumber, String name, PartType type, String brand, String altNumbers,
			BigDecimal price, BigDecimal mrp, int stock, String rack) {
	}

}
