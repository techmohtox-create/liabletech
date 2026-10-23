package com.liabletech.payoutcheck.common;

import java.util.List;

public record PageResult<T>(List<T> items, long total, int page, int size) {

	public static int clampSize(Integer size) {
		return size == null ? 50 : Math.max(1, Math.min(size, 500));
	}

	public static int clampPage(Integer page) {
		return page == null ? 0 : Math.max(0, page);
	}

}
