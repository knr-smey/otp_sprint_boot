package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;

/** Full authentication result: normal API credentials. */
@Builder
public record AuthResponse(
		String tokenType,
		String accessToken,
		String refreshToken) {
}
