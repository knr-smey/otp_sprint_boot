package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.config.TwoFactorProperties;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.LoginResponse;
import com.example.spring_boot_project_api.dto.response.UserResponse;
import com.example.spring_boot_project_api.exception.DuplicateUsernameException;
import com.example.spring_boot_project_api.exception.InvalidCredentialsException;
import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.mapper.UserMapper;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtService jwtService;

	private AuthServiceImpl authService;

	@BeforeEach
	void setUp() {
		authService = new AuthServiceImpl(userRepository, new UserMapper(), passwordEncoder, jwtService);
	}

	private User user(boolean twoFactorEnabled) {
		return User.builder()
				.id(1L)
				.username("alice")
				.passwordHash("bcrypt-hash")
				.twoFactorEnabled(twoFactorEnabled)
				.build();
	}

	@Test
	void register_rejectsDuplicateUsername() {
		when(userRepository.existsByUsername("alice")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(
				new com.example.spring_boot_project_api.dto.request.RegisterRequest("alice", "password123")))
				.isInstanceOf(DuplicateUsernameException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void register_storesEncodedPassword_andDefaultsTwoFactorOff() {
		when(userRepository.existsByUsername("bob")).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User toSave = invocation.getArgument(0);
			toSave.setId(7L);
			return toSave;
		});

		UserResponse response = authService.register(
				new com.example.spring_boot_project_api.dto.request.RegisterRequest("bob", "password123"));

		assertThat(response.username()).isEqualTo("bob");
		assertThat(response.twoFactorEnabled()).isFalse();
		verify(userRepository).save(argThat(saved -> "encoded-hash".equals(saved.getPasswordHash())));
	}

	@Test
	void login_correctPasswordWithout2fa_returnsFullTokens() {
		when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(false)));
		when(passwordEncoder.matches("secret123", "bcrypt-hash")).thenReturn(true);
		when(jwtService.issueTokens(any(User.class))).thenReturn(AuthResponse.builder()
				.tokenType("Bearer").accessToken("access").refreshToken("refresh").build());

		LoginResponse response = authService.login(
				new com.example.spring_boot_project_api.dto.request.LoginRequest("alice", "secret123"));

		assertThat(response.twoFactorRequired()).isFalse();
		assertThat(response.accessToken()).isEqualTo("access");
		assertThat(response.refreshToken()).isEqualTo("refresh");
		assertThat(response.temporaryToken()).isNull();
		verify(jwtService, never()).generateTwoFactorPendingToken(any());
	}

	@Test
	void login_unknownUser_throwsInvalidCredentials() {
		when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(
				new com.example.spring_boot_project_api.dto.request.LoginRequest("ghost", "whatever")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void login_wrongPassword_throwsInvalidCredentials() {
		when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(false)));
		when(passwordEncoder.matches(any(), any())).thenReturn(false);

		assertThatThrownBy(() -> authService.login(
				new com.example.spring_boot_project_api.dto.request.LoginRequest("alice", "wrong")))
				.isInstanceOf(InvalidCredentialsException.class);
		verify(jwtService, never()).issueTokens(any());
	}

	@Test
	void login_correctPasswordWith2faEnabled_returnsOnlyTemporaryToken() {
		when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(true)));
		when(passwordEncoder.matches("secret123", "bcrypt-hash")).thenReturn(true);
		when(jwtService.generateTwoFactorPendingToken(any(User.class))).thenReturn("pending-token");

		LoginResponse response = authService.login(
				new com.example.spring_boot_project_api.dto.request.LoginRequest("alice", "secret123"));

		assertThat(response.twoFactorRequired()).isTrue();
		assertThat(response.temporaryToken()).isEqualTo("pending-token");
		assertThat(response.accessToken()).isNull();
		assertThat(response.refreshToken()).isNull();
		verify(jwtService, never()).issueTokens(any());
	}

	@Test
	void refresh_validRefreshToken_issuesNewPair() {
		JwtService.Claims claims = new JwtService.Claims("alice", 1L, "jti",
				JwtService.TokenType.REFRESH, Instant.now().plus(Duration.ofDays(1)));
		when(jwtService.parseAndValidate("refresh-token", JwtService.TokenType.REFRESH)).thenReturn(claims);
		when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(false)));
		when(jwtService.issueTokens(any(User.class))).thenReturn(AuthResponse.builder()
				.tokenType("Bearer").accessToken("new-access").refreshToken("new-refresh").build());

		AuthResponse response = authService.refresh(
				new com.example.spring_boot_project_api.dto.request.RefreshTokenRequest("refresh-token"));

		assertThat(response.accessToken()).isEqualTo("new-access");
	}

	@Test
	void refresh_nonRefreshToken_isRejected() {
		when(jwtService.parseAndValidate("access-token-as-refresh", JwtService.TokenType.REFRESH))
				.thenThrow(new InvalidTokenException());

		assertThatThrownBy(() -> authService.refresh(
				new com.example.spring_boot_project_api.dto.request.RefreshTokenRequest("access-token-as-refresh")))
				.isInstanceOf(InvalidTokenException.class);
		verify(jwtService, never()).issueTokens(any());
	}
}
