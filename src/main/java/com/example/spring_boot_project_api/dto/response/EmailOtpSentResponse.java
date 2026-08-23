package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;

@Builder
public record EmailOtpSentResponse(
		boolean sent,
		String maskedEmail,
		long expiresInSeconds) {
}
