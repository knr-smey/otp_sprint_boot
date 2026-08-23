package com.example.spring_boot_project_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
		return build(HttpStatus.UNAUTHORIZED, ex);
	}

	@ExceptionHandler(InvalidTokenException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidToken(InvalidTokenException ex) {
		return build(HttpStatus.UNAUTHORIZED, ex);
	}

	@ExceptionHandler(InvalidTwoFactorCodeException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidTwoFactorCode(InvalidTwoFactorCodeException ex) {
		return build(HttpStatus.UNAUTHORIZED, ex);
	}

	@ExceptionHandler(TwoFactorLockedException.class)
	public ResponseEntity<ApiErrorResponse> handleTwoFactorLocked(TwoFactorLockedException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
				.body(new ApiErrorResponse(HttpStatus.TOO_MANY_REQUESTS.value(),
						HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), ex.getMessage(), Instant.now()));
	}

	@ExceptionHandler({DuplicateUsernameException.class, TwoFactorAlreadyEnabledException.class,
			TwoFactorNotEnabledException.class, TwoFactorSetupNotFoundException.class})
	public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex) {
		return build(HttpStatus.CONFLICT, ex);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.orElse("Validation failed.");
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
						message, Instant.now()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
						"Malformed request body.", Instant.now()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
		return ResponseEntity.internalServerError()
				.body(new ApiErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
						HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
						"An unexpected error occurred.", Instant.now()));
	}

	private ResponseEntity<ApiErrorResponse> build(HttpStatus status, RuntimeException ex) {
		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(status.value(), status.getReasonPhrase(), ex.getMessage(), Instant.now()));
	}
}
