package com.example.spring_boot_project_api.service;

/**
 * Thin facade over the TOTP algorithm (RFC 6238) as implemented by the
 * Google Authenticator defaults: HMAC-SHA1, 6 digits, 30s period, ±1 period drift.
 */
public interface TotpService {

	/** @return a cryptographically random Base32 secret */
	String generateSecret();

	/** @return an {@code otpauth://totp/...} URI for Google Authenticator QR codes */
	String buildOtpauthUri(String secret, String accountLabel);

	/** @return true only if {@code code} is exactly 6 digits and currently valid for {@code secret} */
	boolean verify(String secret, String code);
}
