package com.example.spring_boot_project_api.exception;

public class TwoFactorNotEnabledException extends RuntimeException {

	public TwoFactorNotEnabledException() {
		super("Two-factor authentication is not enabled.");
	}
}
