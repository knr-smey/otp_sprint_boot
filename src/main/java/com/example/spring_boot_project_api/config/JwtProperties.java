package com.example.spring_boot_project_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** JWT issuance/validation settings (see application.properties). */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
		String secret,
		String issuer,
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		Duration otpPendingTtl) {

	public JwtProperties {
		if (secret == null || secret.length() < 32) {
			throw new IllegalStateException(
					"app.jwt.secret must be set and at least 32 characters long (override with JWT_SECRET)");
		}
		if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
			accessTokenTtl = Duration.ofMinutes(15);
		}
		if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
			refreshTokenTtl = Duration.ofDays(7);
		}
		if (otpPendingTtl == null || otpPendingTtl.isNegative() || otpPendingTtl.isZero()) {
			otpPendingTtl = Duration.ofMinutes(5);
		}
	}
}
