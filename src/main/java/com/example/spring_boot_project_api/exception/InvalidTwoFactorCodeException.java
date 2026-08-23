package com.example.spring_boot_project_api.exception;

public class InvalidTwoFactorCodeException extends RuntimeException {

	public InvalidTwoFactorCodeException() {
		super("Invalid verification code.");
	}
}
