package com.liabletech.payoutcheck.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.liabletech.payoutcheck.common.BadRequestException;
import com.liabletech.payoutcheck.common.ConflictException;
import com.liabletech.payoutcheck.common.NotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(BadRequestException.class)
	public ProblemDetail handleBadRequest(BadRequestException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	public ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	public ProblemDetail handleConflict(ConflictException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleDenied(AccessDeniedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Your role does not allow this action");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "File is larger than the 10 MB limit");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) throws Exception {
		if (ex instanceof org.springframework.web.ErrorResponse) {
			throw ex;
		}
		log.error("Unhandled error", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. It has been logged.");
	}

}
