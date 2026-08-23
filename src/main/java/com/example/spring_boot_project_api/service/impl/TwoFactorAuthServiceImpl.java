package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.config.TwoFactorProperties;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.BackupCodesResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorEnableResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorSetupResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorStatusResponse;
import com.example.spring_boot_project_api.exception.InvalidCredentialsException;
import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.exception.InvalidTwoFactorCodeException;
import com.example.spring_boot_project_api.exception.TwoFactorAlreadyEnabledException;
import com.example.spring_boot_project_api.exception.TwoFactorNotEnabledException;
import com.example.spring_boot_project_api.exception.TwoFactorSetupNotFoundException;
import com.example.spring_boot_project_api.model.BackupCode;
import com.example.spring_boot_project_api.model.TwoFactorAuth;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.BackupCodeRepository;
import com.example.spring_boot_project_api.repository.TwoFactorAuthRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.CryptoService;
import com.example.spring_boot_project_api.service.JwtService;
import com.example.spring_boot_project_api.service.OtpAttemptService;
import com.example.spring_boot_project_api.service.TotpService;
import com.example.spring_boot_project_api.service.TwoFactorAuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TwoFactorAuthServiceImpl implements TwoFactorAuthService {

	/** Unambiguous alphabet for human-typed backup codes (no I, O, 0, 1). */
	private static final String BACKUP_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int BACKUP_CODE_LENGTH = 8; // rendered as XXXX-XXXX

	private final UserRepository userRepository;
	private final TwoFactorAuthRepository twoFactorAuthRepository;
	private final BackupCodeRepository backupCodeRepository;
	private final TotpService totpService;
	private final CryptoService cryptoService;
	private final JwtService jwtService;
	private final OtpAttemptService otpAttemptService;
	private final PasswordEncoder passwordEncoder;
	private final TwoFactorProperties twoFactorProps;
	private final SecureRandom random = new SecureRandom();

	public TwoFactorAuthServiceImpl(UserRepository userRepository,
			TwoFactorAuthRepository twoFactorAuthRepository,
			BackupCodeRepository backupCodeRepository,
			TotpService totpService,
			CryptoService cryptoService,
			JwtService jwtService,
			OtpAttemptService otpAttemptService,
			PasswordEncoder passwordEncoder,
			TwoFactorProperties twoFactorProps) {
		this.userRepository = userRepository;
		this.twoFactorAuthRepository = twoFactorAuthRepository;
		this.backupCodeRepository = backupCodeRepository;
		this.totpService = totpService;
		this.cryptoService = cryptoService;
		this.jwtService = jwtService;
		this.otpAttemptService = otpAttemptService;
		this.passwordEncoder = passwordEncoder;
		this.twoFactorProps = twoFactorProps;
	}

	@Override
	@Transactional
	public TwoFactorSetupResponse setup(String username) {
		User user = requireUser(username);
		if (user.isTwoFactorEnabled()) {
			throw new TwoFactorAlreadyEnabledException();
		}
		String secret = totpService.generateSecret();
		twoFactorAuthRepository.findById(user.getId()).ifPresentOrElse(row -> {
			row.setPendingSecretEnc(cryptoService.encrypt(secret));
			twoFactorAuthRepository.save(row);
		}, () -> twoFactorAuthRepository.save(TwoFactorAuth.builder()
				.userId(user.getId())
				.pendingSecretEnc(cryptoService.encrypt(secret))
				.updatedAt(LocalDateTime.now())
				.build()));
		return TwoFactorSetupResponse.builder()
				.enabled(false)
				.secret(secret)
				.otpauthUri(totpService.buildOtpauthUri(secret, user.getUsername()))
				.build();
	}

	@Override
	@Transactional
	public TwoFactorEnableResponse enable(String username, String code) {
		User user = requireUser(username);
		if (user.isTwoFactorEnabled()) {
			throw new TwoFactorAlreadyEnabledException();
		}
		TwoFactorAuth row = twoFactorAuthRepository.findById(user.getId())
				.orElseThrow(TwoFactorSetupNotFoundException::new);
		if (row.getPendingSecretEnc() == null) {
			throw new TwoFactorSetupNotFoundException();
		}
		String stagedSecret = decryptSecret(row.getPendingSecretEnc());
		if (!totpService.verify(stagedSecret, code)) {
			// Staged secret stays in place so the user can retry without re-running setup.
			throw new InvalidTwoFactorCodeException();
		}
		row.setSecretEnc(row.getPendingSecretEnc());
		row.setPendingSecretEnc(null);
		user.setTwoFactorEnabled(true);
		List<String> backupCodes = replaceBackupCodes(user.getId());
		return TwoFactorEnableResponse.builder()
				.enabled(true)
				.backupCodes(backupCodes)
				.build();
	}

	@Override
	public AuthResponse verify(String temporaryToken, String code) {
		JwtService.Claims claims = jwtService.parseAndValidate(temporaryToken,
				JwtService.TokenType.TWO_FACTOR_PENDING);
		if (otpAttemptService.isPendingTokenConsumed(claims.jti())) {
			throw new InvalidTokenException("Temporary token already used.");
		}
		User user = requireUser(claims.subject());
		if (!user.isTwoFactorEnabled()) {
			// 2FA was disabled between login and verify - the pending token must not complete login.
			throw new InvalidTokenException();
		}
		otpAttemptService.assertAttemptsAllowed(user.getId());
		if (!verifyCurrentCode(user, code)) {
			otpAttemptService.recordFailure(user.getId());
			throw new InvalidTwoFactorCodeException();
		}
		otpAttemptService.resetFailures(user.getId());
		otpAttemptService.markPendingTokenConsumed(claims.jti(), claims.expiresAt());
		return jwtService.issueTokens(user);
	}

	@Override
	@Transactional
	public TwoFactorStatusResponse disable(String username, String code) {
		User user = requireUser(username);
		if (!user.isTwoFactorEnabled()) {
			throw new TwoFactorNotEnabledException();
		}
		if (!verifyCurrentCode(user, code)) {
			throw new InvalidTwoFactorCodeException();
		}
		user.setTwoFactorEnabled(false);
		wipeFactors(user.getId());
		otpAttemptService.resetFailures(user.getId());
		return new TwoFactorStatusResponse(false);
	}

	@Override
	@Transactional
	public BackupCodesResponse regenerateBackupCodes(String username, String code) {
		User user = requireUser(username);
		if (!user.isTwoFactorEnabled()) {
			throw new TwoFactorNotEnabledException();
		}
		if (!verifyCurrentCode(user, code)) {
			throw new InvalidTwoFactorCodeException();
		}
		return BackupCodesResponse.builder()
				.backupCodes(replaceBackupCodes(user.getId()))
				.build();
	}

	private User requireUser(String username) {
		return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
	}

	/** TOTP first, then unused single-use backup codes (a matching backup code is burned). */
	private boolean verifyCurrentCode(User user, String code) {
		TwoFactorAuth row = twoFactorAuthRepository.findById(user.getId()).orElse(null);
		if (row == null || row.getSecretEnc() == null) {
			return false;
		}
		String secret = decryptSecret(row.getSecretEnc());
		if (totpService.verify(secret, code)) {
			return true;
		}
		for (BackupCode backup : backupCodeRepository.findByUserIdAndUsedAtIsNull(user.getId())) {
			if (passwordEncoder.matches(code, backup.getCodeHash())) {
				backup.setUsedAt(LocalDateTime.now());
				backupCodeRepository.save(backup);
				return true;
			}
		}
		return false;
	}

	private List<String> replaceBackupCodes(Long userId) {
		int backupCodeCount = twoFactorProps.backupCodeCount();
		backupCodeRepository.deleteByUserId(userId);
		List<String> plainCodes = new ArrayList<>(backupCodeCount);
		List<BackupCode> hashes = new ArrayList<>(backupCodeCount);
		LocalDateTime now = LocalDateTime.now();
		for (int i = 0; i < backupCodeCount; i++) {
			String plainCode = generateBackupCode();
			plainCodes.add(plainCode);
			hashes.add(BackupCode.builder()
					.userId(userId)
					.codeHash(passwordEncoder.encode(plainCode))
					.createdAt(now)
					.build());
		}
		backupCodeRepository.saveAll(hashes);
		return plainCodes;
	}

	private String generateBackupCode() {
		StringBuilder sb = new StringBuilder(BACKUP_CODE_LENGTH);
		for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
			sb.append(BACKUP_CODE_ALPHABET.charAt(random.nextInt(BACKUP_CODE_ALPHABET.length())));
			if (i == 3) {
				sb.append('-');
			}
		}
		return sb.toString();
	}

	private void wipeFactors(Long userId) {
		twoFactorAuthRepository.findById(userId).ifPresent(row -> {
			row.setSecretEnc(null);
			row.setPendingSecretEnc(null);
			twoFactorAuthRepository.save(row);
		});
		backupCodeRepository.deleteByUserId(userId);
	}

	private String decryptSecret(String encryptedSecret) {
		try {
			return cryptoService.decrypt(encryptedSecret);
		} catch (IllegalArgumentException ex) {
			// Corrupt ciphertext or wrong key: fail closed without leaking anything.
			throw new IllegalStateException("Stored TOTP material is unreadable", ex);
		}
	}
}
