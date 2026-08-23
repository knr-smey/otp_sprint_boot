package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;

/**
 * Result of {@code POST /auth/login}.
 *
 * <ul>
 *   <li>{@code twoFactorRequired=false}: password ok, 2FA off - full credentials returned.</li>
 *   <li>{@code twoFactorRequired=true}: password ok, 2FA on - only the short-lived
 *       {@code temporaryToken} is returned; it grants access to {@code POST /auth/2fa/verify} alone.</li>
 * </ul>
 */
@Builder
public record LoginResponse(
		boolean twoFactorRequired,
		String temporaryToken,
		String accessToken,
		String refreshToken) {

	public static LoginResponse authenticated(AuthResponse authResponse) {
		return LoginResponse.builder()
				.twoFactorRequired(false)
				.accessToken(authResponse.accessToken())
				.refreshToken(authResponse.refreshToken())
				.build();
	}

	public static LoginResponse twoFactorRequired(String temporaryToken) {
		return LoginResponse.builder()
				.twoFactorRequired(true)
				.temporaryToken(temporaryToken)
				.build();
	}
}
