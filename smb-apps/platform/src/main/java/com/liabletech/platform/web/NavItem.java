package com.liabletech.platform.web;

/** A link in the top navigation. {@code path} also marks the item active for pages beneath it. */
public record NavItem(String label, String path) {

	public boolean activeFor(String requestPath) {
		if ("/".equals(path)) {
			return "/".equals(requestPath);
		}
		return requestPath != null && (requestPath.equals(path) || requestPath.startsWith(path + "/"));
	}

}
