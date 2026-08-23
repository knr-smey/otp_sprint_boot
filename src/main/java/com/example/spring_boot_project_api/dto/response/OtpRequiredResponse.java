package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;

/** Login step 1 result: password accepted, OTP mailed, awaiting verification. */
@Builder
public record OtpRequiredResponse(
		boolean otpRequired,
		String temporaryToken,
		String maskedEmail,
		long expiresInSeconds) {
}
