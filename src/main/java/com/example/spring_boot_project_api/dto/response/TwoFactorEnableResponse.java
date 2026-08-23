package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;

import java.util.List;

/** Result of {@code POST /auth/2fa/enable}; backup codes are shown exactly once. */
@Builder
public record TwoFactorEnableResponse(
		boolean enabled,
		List<String> backupCodes) {
}
