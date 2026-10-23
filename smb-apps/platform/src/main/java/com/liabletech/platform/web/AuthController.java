package com.liabletech.platform.web;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.liabletech.platform.AppProperties;
import com.liabletech.platform.account.SignupForm;
import com.liabletech.platform.account.SignupService;
import com.liabletech.platform.account.Users;
import com.liabletech.platform.text.Phones;

@Controller
public class AuthController {

	private final SignupService signups;

	private final Users users;

	private final AppProperties properties;

	public AuthController(SignupService signups, Users users, AppProperties properties) {
		this.signups = signups;
		this.users = users;
		this.properties = properties;
	}

	@GetMapping("/login")
	public String login(Model model) {
		model.addAttribute("demo", properties.demo().enabled() ? properties.demo() : null);
		return "login";
	}

	@GetMapping("/signup")
	public String signupForm(Model model) {
		model.addAttribute("form", new SignupForm());
		return "signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult result) {
		if (!result.hasFieldErrors("email") && users.emailTaken(form.getEmail())) {
			result.rejectValue("email", "taken", "is already registered; sign in instead");
		}
		if (!result.hasFieldErrors("phone") && Phones.normalize(form.getPhone()) == null) {
			result.rejectValue("phone", "invalid", "is not a valid mobile number");
		}
		if (result.hasErrors()) {
			return "signup";
		}
		signups.signUp(form);
		return "redirect:/login?registered";
	}

}
