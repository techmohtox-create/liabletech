package com.liabletech.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.security.CurrentUser;

@ControllerAdvice
public class ErrorPages {

	private final LayoutModel layout;

	public ErrorPages(LayoutModel layout) {
		this.layout = layout;
	}

	@ExceptionHandler(NotFoundException.class)
	public String notFound(HttpServletRequest request, HttpServletResponse response,
			@AuthenticationPrincipal CurrentUser user, Model model) {
		return page(request, response, user, model, HttpServletResponse.SC_NOT_FOUND,
				"We could not find that page or record.");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public String tooLarge(HttpServletRequest request, HttpServletResponse response,
			@AuthenticationPrincipal CurrentUser user, Model model) {
		return page(request, response, user, model, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
				"That file is too large. Photos and PDFs up to 20 MB are accepted.");
	}

	@ExceptionHandler(FileStore.UnsupportedFileException.class)
	public String unsupported(HttpServletRequest request, HttpServletResponse response,
			@AuthenticationPrincipal CurrentUser user, Model model, FileStore.UnsupportedFileException ex) {
		return page(request, response, user, model, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
	}

	private String page(HttpServletRequest request, HttpServletResponse response, CurrentUser user, Model model,
			int status, String message) {
		response.setStatus(status);
		layout.populate(model, request, user);
		model.addAttribute("status", status);
		model.addAttribute("message", message);
		return "error";
	}

}
