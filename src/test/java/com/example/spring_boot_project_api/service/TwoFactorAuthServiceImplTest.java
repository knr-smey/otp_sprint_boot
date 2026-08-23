package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.config.TwoFactorProperties;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.exception.InvalidTwoFactorCodeException;
import com.example.spring_boot_project_api.exception.TwoFactorAlreadyEnabledException;
import com.example.spring_boot_project_api.exception.TwoFactorLockedException;
import com.example.spring_boot_project_api.exception.TwoFactorNotEnabledException;
import com.example.spring_boot_project_api.exception.TwoFactorSetupNotFoundException;
import com.example.spring_boot_project_api.model.BackupCode;
import com.example.spring_boot_project_api.model.TwoFactorAuth;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.BackupCodeRepository;
import com.example.spring_boot_project_api.repository.TwoFactorAuthRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.impl.AesGcmCryptoServiceImpl;
import com.example.spring_boot_project_api.service.impl.InMemoryOtpAttemptServiceImpl;
import com.example.spring_boot_project_api.service.impl.TotpServiceImpl;
import com.example.spring_boot_project_api.service.impl.TwoFactorAuthServiceImpl;
import dev.samstevens.totp.code.DefaultCodeGenerator;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uses the real TotpService, CryptoService, BCrypt encoder and rate limiter so codes are
 * genuinely valid/invalid; only repositories and JwtService are mocked.
 */
@ExtendWith(MockitoExtension.class)
class TwoFactorAuthServiceImplTest {

	private static final String AES_KEY = "QNiK7ASt82FAhg3ekAmQt+HCz/lXoNjZKYtZB+pdltA=";

	@Mock
	private UserRepository userRepository;
	@Mock
	private TwoFactorAuthRepository twoFactorAuthRepository;
	@Mock
	private BackupCodeRepository backupCodeRepository;
	@Mock
	private JwtService jwtService;

	private final CryptoService cryptoService = new AesGcmCryptoServiceImpl(AES_KEY);
	private final TotpService totpService = new TotpServiceImpl(twoFactorProperties());
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private OtpAttemptService otpAttemptService;

	private TwoFactorAuthServiceImpl twoFactorAuthService;
	private User user;

	@BeforeEach
	void setUp() {
		otpAttemptService = new InMemoryOtpAttemptServiceImpl(twoFactorProperties(), fixedClock());
		twoFactorAuthService = new TwoFactorAuthServiceImpl(userRepository, twoFactorAuthRepository,
				backupCodeRepository, totpService, cryptoService, jwtService, otpAttemptService,
				passwordEncoder, twoFactorProperties());
		user = User.builder()
				.id(1L)
				.username("alice")
				.passwordHash("hash")
				.twoFactorEnabled(false)
				.build();
		lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
	}

	private static TwoFactorProperties twoFactorProperties() {
		return new TwoFactorProperties("Test Issuer", 5, Duration.ofMinutes(15), Duration.ofMinutes(15), 10);
	}

	private static Clock fixedClock() {
		return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
	}

	private String currentValidCode(String secret) {
		long counter = Instant.now().getEpochSecond() / 30;
		try {
			return new DefaultCodeGenerator().generate(secret, counter);
		} catch (dev.samstevens.totp.exceptions.CodeGenerationException ex) {
			throw new IllegalStateException("Test code generation failed", ex);
		}
	}

	/** A definitely-wrong code for the given secret right now. */
	private String invalidCode(String secret) {
		String valid = currentValidCode(secret);
		char lastDigit = valid.charAt(5);
		char replacement = lastDigit == '0' ? '1' : '0';
		return valid.substring(0, 5) + replacement;
	}

	private TwoFactorAuth rowWithSecret(String plainSecret) {
		return TwoFactorAuth.builder()
				.userId(1L)
				.secretEnc(cryptoService.encrypt(plainSecret))
				.updatedAt(LocalDateTime.now())
				.build();
	}

	private void enabledUserWithStoredSecret() {
		user.setTwoFactorEnabled(true);
		String secret = totpService.generateSecret();
		lenient().when(twoFactorAuthRepository.findById(1L))
				.thenReturn(Optional.of(rowWithSecret(secret)));
		lenient().when(jwtService.issueTokens(any(User.class))).thenReturn(AuthResponse.builder()
				.tokenType("Bearer").accessToken("access").refreshToken("refresh").build());
	}

	private void stubPendingToken() {
		JwtService.Claims claims = new JwtService.Claims("alice", 1L, "pending-jti",
				JwtService.TokenType.TWO_FACTOR_PENDING, Instant.now().plus(Duration.ofMinutes(5)));
		lenient().when(jwtService.parseAndValidate(anyString(), eq(JwtService.TokenType.TWO_FACTOR_PENDING)))
				.thenReturn(claims);
	}

	private void stubBackupCodes(String... plainCodes) {
		List<BackupCode> codes = Arrays.stream(plainCodes)
				.map(code -> BackupCode.builder()
						.userId(1L)
						.codeHash(passwordEncoder.encode(code))
						.createdAt(LocalDateTime.now())
						.build())
				.toList();
		lenient().when(backupCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(codes);
		lenient().when(backupCodeRepository.saveAll(anyCollection())).thenAnswer(inv -> inv.getArgument(0));
	}

	// --- setup ---

	@Test
	void setup_stagesEncryptedSecret_andReturnsOtpauthUri_withoutEnabling() {
		when(twoFactorAuthRepository.findById(1L)).thenReturn(Optional.empty());

		var response = twoFactorAuthService.setup("alice");

		assertThat(response.enabled()).isFalse();
		assertThat(response.secret()).isNotBlank();

		ArgumentCaptor<TwoFactorAuth> captor = ArgumentCaptor.forClass(TwoFactorAuth.class);
		verify(twoFactorAuthRepository).save(captor.capture());
		String staged = captor.getValue().getPendingSecretEnc();
		assertThat(staged).isNotEqualTo(response.secret());
		assertThat(cryptoService.decrypt(staged)).isEqualTo(response.secret());

		assertThat(response.otpauthUri())
				.startsWith("otpauth://totp/")
				.contains("alice")
				.contains("secret=" + response.secret())
				.contains("issuer=Test%20Issuer");
		assertThat(user.isTwoFactorEnabled()).isFalse();
	}

	@Test
	void setup_overwritesPreviousPendingSecret() {
		TwoFactorAuth existingRow = TwoFactorAuth.builder()
				.userId(1L)
				.pendingSecretEnc(cryptoService.encrypt("OLDOLDOLDOLDOLDOLDOLDOLDOLDOLD"))
				.updatedAt(LocalDateTime.now())
				.build();
		when(twoFactorAuthRepository.findById(1L)).thenReturn(Optional.of(existingRow));

		var response = twoFactorAuthService.setup("alice");

		assertThat(cryptoService.decrypt(existingRow.getPendingSecretEnc())).isEqualTo(response.secret());
	}

	@Test
	void setup_whenAlreadyEnabled_isRejected() {
		user.setTwoFactorEnabled(true);

		assertThatThrownBy(() -> twoFactorAuthService.setup("alice"))
				.isInstanceOf(TwoFactorAlreadyEnabledException.class);
	}

	// --- enable ---

	@Test
	void enable_withCorrectCode_enables2fa_persistsEncryptedSecret_returnsBackupCodes() {
		String secret = totpService.generateSecret();
		TwoFactorAuth stagedRow = TwoFactorAuth.builder()
				.userId(1L)
				.updatedAt(LocalDateTime.now())
				.build();
		stagedRow.setPendingSecretEnc(cryptoService.encrypt(secret));
		when(twoFactorAuthRepository.findById(1L)).thenReturn(Optional.of(stagedRow));
		stubBackupCodes();

		var response = twoFactorAuthService.enable("alice", currentValidCode(secret));

		assertThat(response.enabled()).isTrue();
		assertThat(response.backupCodes()).hasSize(10);
		assertThat(user.isTwoFactorEnabled()).isTrue();

		assertThat(stagedRow.getSecretEnc()).isNotEqualTo(secret);
		assertThat(cryptoService.decrypt(stagedRow.getSecretEnc())).isEqualTo(secret);
		assertThat(stagedRow.getPendingSecretEnc()).isNull();

		ArgumentCaptor<Collection<BackupCode>> savedCaptor = ArgumentCaptor.forClass(Collection.class);
		verify(backupCodeRepository).saveAll(savedCaptor.capture());
		assertThat(savedCaptor.getValue())
				.allSatisfy(saved -> assertThat(response.backupCodes())
						.anySatisfy(plain -> assertThat(passwordEncoder.matches(plain, saved.getCodeHash())).isTrue()));
	}

	@Test
	void enable_withWrongCode_doesNotEnable_keepsStagedSecretForRetry() {
		String secret = totpService.generateSecret();
		TwoFactorAuth stagedRow = TwoFactorAuth.builder()
				.userId(1L)
				.updatedAt(LocalDateTime.now())
				.build();
		stagedRow.setPendingSecretEnc(cryptoService.encrypt(secret));
		when(twoFactorAuthRepository.findById(1L)).thenReturn(Optional.of(stagedRow));

		String wrongCode = invalidCode(secret);

		assertThatThrownBy(() -> twoFactorAuthService.enable("alice", wrongCode))
				.isInstanceOf(InvalidTwoFactorCodeException.class);

		assertThat(user.isTwoFactorEnabled()).isFalse();
		assertThat(stagedRow.getSecretEnc()).isNull();
		assertThat(stagedRow.getPendingSecretEnc()).isNotNull();
		verify(backupCodeRepository, never()).saveAll(anyCollection());
	}

	@Test
	void enable_withoutPriorSetup_isRejected() {
		when(twoFactorAuthRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> twoFactorAuthService.enable("alice", "123456"))
				.isInstanceOf(TwoFactorSetupNotFoundException.class);
	}

	// --- verify (second login step) ---

	@Test
	void verify_withCorrectTotp_issuesTokens_andConsumesPendingToken() {
		enabledUserWithStoredSecret();
		stubPendingToken();

		String secret = cryptoService.decrypt(
				twoFactorAuthRepository.findById(1L).map(TwoFactorAuth::getSecretEnc).orElseThrow());
		AuthResponse response = twoFactorAuthService.verify("pending-token", currentValidCode(secret));

		assertThat(response.accessToken()).isEqualTo("access");
		assertThat(response.refreshToken()).isEqualTo("refresh");
		assertThat(otpAttemptService.isPendingTokenConsumed("pending-jti")).isTrue();
	}

	@Test
	void verify_withReusedTemporaryToken_isRejected_evenWithValidCode() {
		enabledUserWithStoredSecret();
		stubPendingToken();

		String secret = cryptoService.decrypt(
				twoFactorAuthRepository.findById(1L).map(TwoFactorAuth::getSecretEnc).orElseThrow());
		twoFactorAuthService.verify("pending-token", currentValidCode(secret));

		assertThatThrownBy(() -> twoFactorAuthService.verify("pending-token", currentValidCode(secret)))
				.isInstanceOf(InvalidTokenException.class)
				.hasMessageContaining("already used");
	}

	@Test
	void verify_withExpiredOrMalformedTemporaryToken_propagatesRejection_andIssuesNoTokens() {
		when(jwtService.parseAndValidate(anyString(), eq(JwtService.TokenType.TWO_FACTOR_PENDING)))
				.thenThrow(new InvalidTokenException());

		assertThatThrownBy(() -> twoFactorAuthService.verify("expired", "123456"))
				.isInstanceOf(InvalidTokenException.class);
		verify(jwtService, never()).issueTokens(any());
	}

	@Test
	void verify_withWrongTotp_recordsFailure_andRejects() {
		enabledUserWithStoredSecret();
		stubPendingToken();

		String secret = cryptoService.decrypt(
				twoFactorAuthRepository.findById(1L).map(TwoFactorAuth::getSecretEnc).orElseThrow());
		String wrongButWellFormed = invalidCode(secret);

		assertThatThrownBy(() -> twoFactorAuthService.verify("pending-token", wrongButWellFormed))
				.isInstanceOf(InvalidTwoFactorCodeException.class);

		verify(jwtService, never()).issueTokens(any());
		// One recorded failure + four more reaches the lockout threshold.
		otpAttemptService.recordFailure(1L);
		otpAttemptService.recordFailure(1L);
		otpAttemptService.recordFailure(1L);
		otpAttemptService.recordFailure(1L);
		String validCode = currentValidCode(cryptoService.decrypt(
				twoFactorAuthRepository.findById(1L).map(TwoFactorAuth::getSecretEnc).orElseThrow()));
		assertThatThrownBy(() -> twoFactorAuthService.verify("pending-token", validCode))
				.isInstanceOf(TwoFactorLockedException.class);
	}

	@Test
	void verify_locksOutAfterTooManyFailures() {
		enabledUserWithStoredSecret();
		stubPendingToken();

		for (int i = 0; i < 5; i++) {
			otpAttemptService.recordFailure(1L);
		}
		String secret = cryptoService.decrypt(
				twoFactorAuthRepository.findById(1L).map(TwoFactorAuth::getSecretEnc).orElseThrow());
		String validCode = currentValidCode(secret);

		assertThatThrownBy(() -> twoFactorAuthService.verify("pending-token", validCode))
				.isInstanceOf(TwoFactorLockedException.class);
	}

	// --- backup codes ---

	@Test
	void verify_acceptsUnusedBackupCode_once() {
		enabledUserWithStoredSecret();
		stubPendingToken();
		stubBackupCodes("ABCD-2345", "WXYZ-6789");

		AuthResponse response = twoFactorAuthService.verify("pending-token", "ABCD-2345");
		assertThat(response.accessToken()).isEqualTo("access");

		ArgumentCaptor<BackupCode> captor = ArgumentCaptor.forClass(BackupCode.class);
		verify(backupCodeRepository).save(captor.capture());
		assertThat(captor.getValue().getUsedAt()).isNotNull();
	}

	@Test
	void verify_backupCodeCannotBeReused() {
		enabledUserWithStoredSecret();
		stubPendingToken();
		// Simulates the post-consumption state: the used code no longer appears as unused.
		when(backupCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of());

		assertThatThrownBy(() -> twoFactorAuthService.verify("pending-token", "ABCD-2345"))
				.isInstanceOf(InvalidTwoFactorCodeException.class);
	}

	// --- disable ---

	@Test
	void disable_withValidCode_disables_andWipesAllFactors() {
		enabledUserWithStoredSecret();
		TwoFactorAuth storedRow = twoFactorAuthRepository.findById(1L).orElseThrow();

		String secret = cryptoService.decrypt(storedRow.getSecretEnc());
		var response = twoFactorAuthService.disable("alice", currentValidCode(secret));

		assertThat(response.enabled()).isFalse();
		assertThat(user.isTwoFactorEnabled()).isFalse();
		assertThat(storedRow.getSecretEnc()).isNull();
		assertThat(storedRow.getPendingSecretEnc()).isNull();
		verify(backupCodeRepository).deleteByUserId(1L);
	}

	@Test
	void disable_withBackupCode_alsoWorks_lostDeviceScenario() {
		enabledUserWithStoredSecret();
		stubBackupCodes("ABCD-2345");

		var response = twoFactorAuthService.disable("alice", "ABCD-2345");

		assertThat(response.enabled()).isFalse();
		assertThat(user.isTwoFactorEnabled()).isFalse();
		verify(backupCodeRepository).deleteByUserId(1L);
	}

	@Test
	void disable_withWrongCode_isRejected_staysEnabled() {
		enabledUserWithStoredSecret();

		assertThatThrownBy(() -> twoFactorAuthService.disable("alice", "999999"))
				.isInstanceOf(InvalidTwoFactorCodeException.class);
		assertThat(user.isTwoFactorEnabled()).isTrue();
	}

	@Test
	void disable_when2faNotEnabled_isRejected() {
		assertThatThrownBy(() -> twoFactorAuthService.disable("alice", "123456"))
				.isInstanceOf(TwoFactorNotEnabledException.class);
	}

	// --- regenerate backup codes ---

	@Test
	void regenerate_withValidCode_replacesBackupCodes() {
		enabledUserWithStoredSecret();

		String secret = cryptoService.decrypt(
				twoFactorAuthRepository.findById(1L).map(TwoFactorAuth::getSecretEnc).orElseThrow());
		var response = twoFactorAuthService.regenerateBackupCodes("alice", currentValidCode(secret));

		assertThat(response.backupCodes()).hasSize(10);
		verify(backupCodeRepository).deleteByUserId(1L);
		verify(backupCodeRepository).saveAll(anyCollection());
	}

	@Test
	void regenerate_withWrongCode_isRejected() {
		enabledUserWithStoredSecret();

		assertThatThrownBy(() -> twoFactorAuthService.regenerateBackupCodes("alice", "111222"))
				.isInstanceOf(InvalidTwoFactorCodeException.class);
		verify(backupCodeRepository, never()).deleteByUserId(1L);
	}
}
