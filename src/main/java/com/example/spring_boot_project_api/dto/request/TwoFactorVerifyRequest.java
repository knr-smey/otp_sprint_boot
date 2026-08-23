package com.example.spring_boot_project_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Second step of the login flow when 2FA is enabled: the short-lived pending token from
 * {@code POST /auth/login} plus the current 6-digit TOTP (or a backup) code.
 */
public record TwoFactorVerifyRequest(

		@NotBlank
		String temporaryToken,

		@NotBlank
		@Pattern(regexp = "^\\d{6}$", message = "must be exactly 6 digits")
		String code) {
}
