package com.example.spring_boot_project_api.exception;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid username or password.");
	}

	public InvalidCredentialsException(String message) {
		super(message);
	}
}
