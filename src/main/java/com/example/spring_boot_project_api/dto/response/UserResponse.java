package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;

@Builder
public record UserResponse(
		Long id,
		String username,
		boolean twoFactorEnabled) {
}
