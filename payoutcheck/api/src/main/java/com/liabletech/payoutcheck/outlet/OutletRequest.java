package com.liabletech.payoutcheck.outlet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OutletRequest(@NotBlank @Size(max = 120) String name, @Size(max = 80) String city,
		@Size(max = 40) String swiggyId, @Size(max = 40) String zomatoId, Boolean active) {
}
