package com.example.spring_boot_project_api.exception;

/** 2FA enable attempted without a prior, non-consumed setup (no staged secret). */
public class TwoFactorSetupNotFoundException extends RuntimeException {

	public TwoFactorSetupNotFoundException() {
		super("No pending two-factor setup found. Call POST /auth/2fa/setup first.");
	}
}
