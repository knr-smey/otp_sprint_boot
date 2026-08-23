package com.example.spring_boot_project_api.service;

import java.time.Instant;

/**
 * Brute-force protection for OTP verification plus single-use tracking of
 * OTP pending-token ids. In-memory: see TODO for multi-node deployments.
 */
public interface OtpAttemptService {

	/**
	 * @throws com.example.spring_boot_project_api.exception.OtpLockedException
	 *         if the user is currently locked out after too many failures
	 */
	void assertAttemptsAllowed(Long userId);

	void recordFailure(Long userId);

	void resetFailures(Long userId);

	/** @return true if this jti has already been consumed (i.e. the token must be rejected) */
	boolean isPendingTokenConsumed(String jti);

	/** Marks a pending token jti as consumed once it has successfully completed authentication. */
	void markPendingTokenConsumed(String jti, Instant expiresAt);
}
