package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.config.OtpProperties;
import com.example.spring_boot_project_api.dto.request.LoginRequest;
import com.example.spring_boot_project_api.dto.request.RegisterRequest;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.OtpRequiredResponse;
import com.example.spring_boot_project_api.dto.response.UserResponse;
import com.example.spring_boot_project_api.exception.DuplicateEmailException;
import com.example.spring_boot_project_api.exception.DuplicateUsernameException;
import com.example.spring_boot_project_api.exception.InvalidCredentialsException;
import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.exception.OtpLockedException;
import com.example.spring_boot_project_api.mapper.UserMapper;
import com.example.spring_boot_project_api.model.EmailOtp;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.EmailOtpRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.impl.AuthServiceImpl;
import com.example.spring_boot_project_api.service.impl.InMemoryOtpAttemptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the two-step email-OTP login flow: password -> mailed code -> tokens.
 * Only repositories, JwtService and the mail sender are mocked; rate limiting,
 * hashing and the service logic run for real.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

	private static final String KNOWN_CODE = "654321";

	@Mock
	private UserRepository userRepository;
	@Mock
	private EmailOtpRepository emailOtpRepository;
	@Mock
	private UserMapper userMapper;
	@Mock
	private JwtService jwtService;
	@Mock
	private OtpMailSender otpMailSender;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private OtpAttemptService otpAttemptService;
	private AuthServiceImpl authService;
	private User user;

	@BeforeEach
	void setUp() {
		OtpProperties props = new OtpProperties("Test Issuer", 5,
				Duration.ofMinutes(15), Duration.ofMinutes(15),
				true, Duration.ofMinutes(5), Duration.ofSeconds(60));
		otpAttemptService = new InMemoryOtpAttemptServiceImpl(props,
				Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
		authService = new AuthServiceImpl(userRepository, emailOtpRepository, userMapper,
				passwordEncoder, jwtService, otpAttemptService, otpMailSender, props);
		user = User.builder()
				.id(1L)
				.username("alice")
				.email("alice@example.com")
				.passwordHash(passwordEncoder.encode("secret123"))
				.build();
		lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
		lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		lenient().when(emailOtpRepository.findById(1L)).thenReturn(Optional.empty());
		lenient().when(jwtService.generateOtpPendingToken(user)).thenReturn("pending-token");
		lenient().when(userMapper.toResponse(any(User.class))).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			return new UserResponse(u.getId(), u.getUsername(), u.getEmail());
		});
	}

	private String stubOutstandingOtp(LocalDateTime expiresAt) {
		EmailOtp otp = EmailOtp.builder()
				.userId(1L)
				.codeHash(passwordEncoder.encode(KNOWN_CODE))
				.expiresAt(expiresAt != null ? expiresAt : LocalDateTime.now().plus(Duration.ofMinutes(5)))
				.sentAt(LocalDateTime.now())
				.build();
		lenient().when(emailOtpRepository.findById(1L)).thenReturn(Optional.of(otp));
		return KNOWN_CODE;
	}

	private void stubPendingTokenClaims() {
		lenient().when(jwtService.parseAndValidate(eq("pending-token"), eq(JwtService.TokenType.OTP_PENDING)))
				.thenReturn(new JwtService.Claims("alice", 1L, "jti-1",
						JwtService.TokenType.OTP_PENDING, Instant.now().plus(Duration.ofMinutes(5))));
	}

	private void stubIssuedTokens() {
		lenient().when(jwtService.issueTokens(any(User.class))).thenReturn(AuthResponse.builder()
				.tokenType("Bearer").accessToken("access").refreshToken("refresh").build());
	}

	// --- register ---

	@Test
	void register_rejectsDuplicateUsername() {
		when(userRepository.existsByUsername("alice")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(
				new RegisterRequest("alice", "alice@example.com", "password123")))
				.isInstanceOf(DuplicateUsernameException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void register_rejectsDuplicateEmail() {
		when(userRepository.existsByUsername("carol")).thenReturn(false);
		when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(
				new RegisterRequest("carol", "taken@example.com", "password123")))
				.isInstanceOf(DuplicateEmailException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void register_encodesPassword_andReturnsUser() {
		when(userRepository.existsByUsername("bob")).thenReturn(false);
		when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User toSave = inv.getArgument(0);
			toSave.setId(7L);
			return toSave;
		});

		UserResponse response = authService.register(
				new RegisterRequest("bob", "bob@example.com", "password123"));

		assertThat(response.username()).isEqualTo("bob");
		assertThat(response.email()).isEqualTo("bob@example.com");
		verify(userRepository).save(argThat(saved ->
				saved.getPasswordHash() != null && !saved.getPasswordHash().equals("password123")));
	}

	// --- login: step 1 ---

	@Test
	void login_correctPassword_mailsOtp_andReturnsPendingToken() {
		OtpRequiredResponse response = authService.login(new LoginRequest("alice", "secret123"));

		assertThat(response.otpRequired()).isTrue();
		assertThat(response.temporaryToken()).isEqualTo("pending-token");
		assertThat(response.maskedEmail()).isEqualTo("a***@example.com");

		ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
		verify(emailOtpRepository).save(saved.capture());
		assertThat(saved.getValue().getCodeHash()).isNotBlank();
		assertThat(saved.getValue().getExpiresAt()).isNotNull();
		verify(otpMailSender).sendOtpCode(eq("alice@example.com"), eq("alice"), anyString());
	}

	@Test
	void login_wrongPassword_throws_andSendsNothing() {
		assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
				.isInstanceOf(InvalidCredentialsException.class);
		verify(otpMailSender, never()).sendOtpCode(anyString(), anyString(), anyString());
		verify(emailOtpRepository, never()).save(any());
	}

	@Test
	void login_unknownUser_sameError_asWrongPassword() {
		when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "whatever")))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Invalid username or password.");
	}

	// --- resend ---

	@Test
	void resendOtp_withinCooldown_isRejectedWith429() {
		stubOutstandingOtp(LocalDateTime.now().plus(Duration.ofMinutes(4)));
		stubPendingTokenClaims();

		assertThatThrownBy(() -> authService.resendOtp("pending-token"))
				.isInstanceOf(OtpLockedException.class);
	}

	// --- verifyOtp: step 2 ---

	@Test
	void verifyOtp_correctCode_issuesTokens_andConsumesCode() {
		String code = stubOutstandingOtp(LocalDateTime.now().plus(Duration.ofMinutes(5)));
		stubPendingTokenClaims();
		stubIssuedTokens();

		AuthResponse response = authService.verifyOtp("pending-token", code);

		assertThat(response.accessToken()).isEqualTo("access");
		verify(emailOtpRepository).delete(any(EmailOtp.class)); // single use
	}

	@Test
	void verifyOtp_wrongCode_recordsFailure_andRejects() {
		stubOutstandingOtp(null);
		stubPendingTokenClaims();

		assertThatThrownBy(() -> authService.verifyOtp("pending-token", "000000"))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Invalid or expired code.");
	}

	@Test
	void verifyOtp_expiredCode_isRejected() {
		stubOutstandingOtp(LocalDateTime.now().minusSeconds(1));
		stubPendingTokenClaims();

		assertThatThrownBy(() -> authService.verifyOtp("pending-token", KNOWN_CODE))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void verifyOtp_noOutstandingCode_isRejected() {
		stubPendingTokenClaims();

		assertThatThrownBy(() -> authService.verifyOtp("pending-token", KNOWN_CODE))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void verifyOtp_bruteForce_locksAfterMaxFailures() {
		stubOutstandingOtp(null);
		stubPendingTokenClaims();

		for (int i = 0; i < 5; i++) {
			try {
				authService.verifyOtp("pending-token", "00000" + i);
			} catch (InvalidCredentialsException expected) {
				// each wrong attempt counts toward the lockout
			}
		}
		// Even the correct code is rejected while locked out.
		assertThatThrownBy(() -> authService.verifyOtp("pending-token", KNOWN_CODE))
				.isInstanceOf(OtpLockedException.class);
	}

	@Test
	void verifyOtp_replayedTemporaryToken_isRejected_afterSuccess() {
		String code = stubOutstandingOtp(null);
		stubPendingTokenClaims();
		stubIssuedTokens();

		authService.verifyOtp("pending-token", code);

		assertThatThrownBy(() -> authService.verifyOtp("pending-token", code))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessage("Temporary token already used.");
	}

	// --- refresh ---

	@Test
	void refresh_issuesNewTokens_forValidRefreshToken() {
		lenient().when(jwtService.parseAndValidate("refresh-token", JwtService.TokenType.REFRESH))
				.thenReturn(new JwtService.Claims("alice", 1L, "jti-r",
						JwtService.TokenType.REFRESH, Instant.now().plus(Duration.ofDays(7))));
		stubIssuedTokens();

		var response = authService.refresh(
				new com.example.spring_boot_project_api.dto.request.RefreshTokenRequest("refresh-token"));

		assertThat(response.refreshToken()).isEqualTo("refresh");
	}
}
