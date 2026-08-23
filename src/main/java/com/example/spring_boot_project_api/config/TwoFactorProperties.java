package com.example.spring_boot_project_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** TOTP / 2FA behaviour settings. */
@ConfigurationProperties(prefix = "app.two-factor")
public record TwoFactorProperties(
		String issuer,
		int maxFailures,
		Duration failureWindow,
		Duration lockDuration,
		int backupCodeCount) {

	public TwoFactorProperties {
		if (issuer == null || issuer.isBlank()) {
			issuer = "spring-boot-project-api";
		}
		if (maxFailures <= 0) {
			maxFailures = 5;
		}
		if (failureWindow == null || failureWindow.isNegative() || failureWindow.isZero()) {
			failureWindow = Duration.ofMinutes(15);
		}
		if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
			lockDuration = Duration.ofMinutes(15);
		}
		if (backupCodeCount <= 0) {
			backupCodeCount = 10;
		}
	}
}
