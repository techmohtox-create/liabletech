package com.liabletech.payoutcheck.outlet;

import java.time.OffsetDateTime;

public record Outlet(long id, long orgId, String name, String city, String swiggyId, String zomatoId, boolean active,
		OffsetDateTime createdAt) {
}
