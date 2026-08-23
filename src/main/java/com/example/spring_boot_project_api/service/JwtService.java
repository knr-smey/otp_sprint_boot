package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.model.User;

import java.time.Instant;

/**
 * Issues and validates all JWTs used by the API. Three token types exist:
 *
 * <ul>
 *   <li>{@link TokenType#ACCESS} - normal API credentials.</li>
 *   <li>{@link TokenType#REFRESH} - exchanged for a fresh pair via {@code POST /auth/refresh}.</li>
 *   <li>{@link TokenType#TWO_FACTOR_PENDING} - issued when a correct password belongs to a
 *       2FA-enabled account. It carries nothing but subject/user id, expires quickly, and is
 *       rejected by the authentication filter as an API credential; only
 *       {@code POST /auth/2fa/verify} accepts it.</li>
 * </ul>
 */
public interface JwtService {

	/**
	 * Parses and validates signature, expiry, issuer and expected type.
	 *
	 * @throws InvalidTokenException on any validation failure
	 */
	Claims parseAndValidate(String token, TokenType expectedType);

	AuthResponse issueTokens(User user);

	String generateTwoFactorPendingToken(User user);

	enum TokenType {
		ACCESS, REFRESH, TWO_FACTOR_PENDING
	}

	record Claims(String subject, Long userId, String jti, TokenType type, Instant expiresAt) {
	}
}
