package com.example.spring_boot_project_api.exception;

public class TwoFactorAlreadyEnabledException extends RuntimeException {

	public TwoFactorAlreadyEnabledException() {
		super("Two-factor authentication is already enabled.");
	}
}
