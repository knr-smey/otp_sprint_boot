package com.example.spring_boot_project_api.exception;

public class InvalidOtpCodeException extends RuntimeException {

	public InvalidOtpCodeException() {
		super("Invalid verification code.");
	}
}
