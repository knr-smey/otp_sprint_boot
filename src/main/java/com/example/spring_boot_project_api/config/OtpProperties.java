package com.example.spring_boot_project_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Email-OTP login behaviour settings. */
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
		String issuer,
		int maxFailures,
		Duration failureWindow,
		Duration lockDuration,
		boolean emailEnabled,
		Duration codeTtl,
		Duration resendCooldown) {

	public OtpProperties {
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
		if (codeTtl == null || codeTtl.isNegative() || codeTtl.isZero()) {
			codeTtl = Duration.ofMinutes(5);
		}
		if (resendCooldown == null || resendCooldown.isNegative()) {
			resendCooldown = Duration.ofSeconds(60);
		}
	}
}
