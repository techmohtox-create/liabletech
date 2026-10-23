package com.liabletech.platform.web;

/**
 * A record that does not exist, or belongs to another business. Both show the same 404 page so
 * that ids cannot be probed across accounts.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String what) {
		super(what + " not found");
	}

}
