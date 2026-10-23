package com.liabletech.platform.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.liabletech.platform.AppProperties;
import com.liabletech.platform.security.CurrentUser;
import com.liabletech.platform.whatsapp.WhatsAppProperties;

/** What the shared layout needs on every page: brand, navigation and the signed-in user. */
@ControllerAdvice
public class LayoutModel {

	private final AppProperties properties;

	private final AppNavigation navigation;

	private final WhatsAppProperties whatsApp;

	public LayoutModel(AppProperties properties, AppNavigation navigation, WhatsAppProperties whatsApp) {
		this.properties = properties;
		this.navigation = navigation;
		this.whatsApp = whatsApp;
	}

	@ModelAttribute("brand")
	public AppProperties.Brand brand() {
		return properties.brand();
	}

	@ModelAttribute("nav")
	public List<NavItem> nav() {
		return navigation.items();
	}

	@ModelAttribute("requestPath")
	public String requestPath(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length());
	}

	@ModelAttribute("currentUser")
	public CurrentUser currentUser(@AuthenticationPrincipal CurrentUser user) {
		return user;
	}

	@ModelAttribute("simulator")
	public boolean simulator() {
		return whatsApp.simulator();
	}

	/**
	 * Fills the same attributes for views rendered from {@code @ExceptionHandler} methods, which
	 * do not run {@code @ModelAttribute} methods.
	 */
	void populate(Model model, HttpServletRequest request, CurrentUser user) {
		model.addAttribute("brand", brand());
		model.addAttribute("nav", nav());
		model.addAttribute("requestPath", requestPath(request));
		model.addAttribute("currentUser", user);
		model.addAttribute("simulator", simulator());
	}

}
