package com.liabletech.platform.web;

import java.util.List;

/** Each app declares its main pages; the shared layout renders them as the top navigation. */
public interface AppNavigation {

	List<NavItem> items();

}
