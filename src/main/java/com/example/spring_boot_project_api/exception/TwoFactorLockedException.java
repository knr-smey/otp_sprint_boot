package com.example.spring_boot_project_api.exception;

/** Thrown when OTP verification fails too often; the account is temporarily locked out. */
public class TwoFactorLockedException extends RuntimeException {

	private final long retryAfterSeconds;

	public TwoFactorLockedException(long retryAfterSeconds) {
		super("Too many failed verification attempts. Try again later.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
