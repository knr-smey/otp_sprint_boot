package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;

/** Result of {@code POST /auth/2fa/setup}. 2FA stays disabled until {@code POST /auth/2fa/enable} succeeds. */
@Builder
public record TwoFactorSetupResponse(
		boolean enabled,
		String secret,
		String otpauthUri) {
}
