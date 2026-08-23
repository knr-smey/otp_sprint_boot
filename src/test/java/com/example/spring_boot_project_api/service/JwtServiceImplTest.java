package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.config.JwtProperties;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.service.impl.JwtServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceImplTest {

	private static final String SECRET = "test-jwt-signing-secret-at-least-32-chars-long";

	private JwtProperties props;
	private JwtServiceImpl jwtService;
	private User user;

	@BeforeEach
	void setUp() {
		props = new JwtProperties(SECRET, "test-issuer",
				Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofMinutes(5));
		jwtService = new JwtServiceImpl(props);
		user = User.builder()
				.id(42L)
				.username("alice")
				.passwordHash("hash")
				.build();
	}

	@Test
	void issueTokens_returnsVerifiableAccessAndRefreshTokens() {
		AuthResponse response = jwtService.issueTokens(user);

		assertThat(response.tokenType()).isEqualTo("Bearer");
		JwtService.Claims access = jwtService.parseAndValidate(response.accessToken(), JwtService.TokenType.ACCESS);
		assertThat(access.subject()).isEqualTo("alice");
		assertThat(access.userId()).isEqualTo(42L);
		assertThat(access.jti()).isNotBlank();

		JwtService.Claims refresh = jwtService.parseAndValidate(response.refreshToken(), JwtService.TokenType.REFRESH);
		assertThat(refresh.subject()).isEqualTo("alice");
	}

	@Test
	void issueTokens_accessTokenCannotBeUsedAsRefreshAndViceVersa() {
		AuthResponse response = jwtService.issueTokens(user);

		assertThatThrownBy(() -> jwtService.parseAndValidate(response.accessToken(), JwtService.TokenType.REFRESH))
				.isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> jwtService.parseAndValidate(response.refreshToken(), JwtService.TokenType.ACCESS))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void pendingOtpToken_isShortLived() {
		String pending = jwtService.generateOtpPendingToken(user);

		JwtService.Claims claims = jwtService.parseAndValidate(pending, JwtService.TokenType.OTP_PENDING);
		assertThat(claims.subject()).isEqualTo("alice");
		assertThat(claims.userId()).isEqualTo(42L);
		assertThat(claims.expiresAt()).isBefore(Instant.now().plus(Duration.ofMinutes(6)));
	}

	@Test
	void expiredToken_isRejected() throws InterruptedException {
		JwtProperties shortLived = new JwtProperties(SECRET, "test-issuer",
				Duration.ofMillis(150), Duration.ofDays(7), Duration.ofMinutes(5));
		JwtServiceImpl fastExpiring = new JwtServiceImpl(shortLived);

		String token = fastExpiring.issueTokens(user).accessToken();
		// Deterministic: wait comfortably past the 150ms TTL before validating.
		Thread.sleep(500);
		assertThatThrownBy(() -> fastExpiring.parseAndValidate(token, JwtService.TokenType.ACCESS))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void tamperedToken_isRejected() {
		String token = jwtService.issueTokens(user).accessToken();
		String tampered = token.substring(0, token.length() - 4) + "AAAA";

		assertThatThrownBy(() -> jwtService.parseAndValidate(tampered, JwtService.TokenType.ACCESS))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void tokenFromDifferentIssuer_isRejected() {
		JwtProperties otherIssuer = new JwtProperties(SECRET, "someone-else",
				Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofMinutes(5));

		String foreign = new JwtServiceImpl(otherIssuer).issueTokens(user).accessToken();
		assertThatThrownBy(() -> jwtService.parseAndValidate(foreign, JwtService.TokenType.ACCESS))
				.isInstanceOf(InvalidTokenException.class);
	}
}
