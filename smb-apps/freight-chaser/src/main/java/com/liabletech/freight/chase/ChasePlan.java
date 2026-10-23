package com.liabletech.freight.chase;

import java.time.LocalDate;
import java.util.List;

/**
 * When the next reminder for an invoice is due. Pure date arithmetic, kept apart from sending so
 * the schedule is easy to reason about and test.
 */
public final class ChasePlan {

	private ChasePlan() {
	}

	/**
	 * The first reminder date after {@code after}: the next configured offset from the due date, or,
	 * once those are used up, every {@code repeatEveryDays} after the last one. A payment date the
	 * party promised wins: the reminder then comes the day after the promise.
	 */
	public static LocalDate next(LocalDate dueDate, List<Integer> offsets, int repeatEveryDays, LocalDate after,
			LocalDate promisedOn) {
		if (promisedOn != null && !promisedOn.isBefore(after)) {
			return promisedOn.plusDays(1);
		}
		for (int offset : offsets) {
			LocalDate candidate = dueDate.plusDays(offset);
			if (candidate.isAfter(after)) {
				return candidate;
			}
		}
		int step = Math.max(1, repeatEveryDays);
		LocalDate last = offsets.isEmpty() ? dueDate : dueDate.plusDays(offsets.getLast());
		LocalDate candidate = last.plusDays(step);
		while (!candidate.isAfter(after)) {
			candidate = candidate.plusDays(step);
		}
		return candidate;
	}

}
