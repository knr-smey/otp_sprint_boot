package com.example.spring_boot_project_api.exception;

/** A JWT is missing/malformed/expired/wrong type, or a 2FA pending token was already used. */
public class InvalidTokenException extends RuntimeException {

	public InvalidTokenException() {
		this("Invalid or expired token.");
	}

	public InvalidTokenException(String message) {
		super(message);
	}
}
