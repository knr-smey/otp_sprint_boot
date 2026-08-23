package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.config.OtpProperties;
import com.example.spring_boot_project_api.dto.request.LoginRequest;
import com.example.spring_boot_project_api.dto.request.RefreshTokenRequest;
import com.example.spring_boot_project_api.dto.request.RegisterRequest;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.EmailOtpSentResponse;
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
import com.example.spring_boot_project_api.service.AuthService;
import com.example.spring_boot_project_api.service.JwtService;
import com.example.spring_boot_project_api.service.OtpAttemptService;
import com.example.spring_boot_project_api.service.OtpMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

	/** BCrypt hash of an unrelated throwaway value; keeps timing equal for unknown usernames. */
	private static final String DUMMY_PASSWORD_HASH =
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	private final UserRepository userRepository;
	private final EmailOtpRepository emailOtpRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final OtpAttemptService otpAttemptService;
	private final OtpMailSender otpMailSender;
	private final OtpProperties otpProps;
	private final SecureRandom random = new SecureRandom();

	public AuthServiceImpl(UserRepository userRepository,
			EmailOtpRepository emailOtpRepository,
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			OtpAttemptService otpAttemptService,
			OtpMailSender otpMailSender,
			OtpProperties otpProps) {
		this.userRepository = userRepository;
		this.emailOtpRepository = emailOtpRepository;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.otpAttemptService = otpAttemptService;
		this.otpMailSender = otpMailSender;
		this.otpProps = otpProps;
	}

	@Override
	@Transactional
	public UserResponse register(RegisterRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new DuplicateUsernameException(request.username());
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new DuplicateEmailException(request.email());
		}
		User user = userRepository.save(User.builder()
				.username(request.username())
				.email(request.email())
				.passwordHash(passwordEncoder.encode(request.password()))
				.build());
		return userMapper.toResponse(user);
	}

	@Override
	public OtpRequiredResponse login(LoginRequest request) {
		User user = userRepository.findByUsername(request.username()).orElse(null);
		boolean passwordOk = user != null
				&& passwordEncoder.matches(request.password(), user.getPasswordHash());
		if (!passwordOk) {
			// Same failure for unknown user and wrong password; burns one BCrypt round to equalize timing.
			passwordEncoder.matches("timing-equalization", DUMMY_PASSWORD_HASH);
			throw new InvalidCredentialsException();
		}
		return issueOtpAndBuildResponse(user);
	}

	@Override
	public EmailOtpSentResponse resendOtp(String temporaryToken) {
		JwtService.Claims claims = requirePendingClaims(temporaryToken);
		otpAttemptService.assertAttemptsAllowed(claims.userId());
		User user = userRepository.findById(claims.userId())
				.orElseThrow(InvalidCredentialsException::new);
		return sendOtp(user);
	}

	@Override
	public AuthResponse verifyOtp(String temporaryToken, String code) {
		JwtService.Claims claims = requirePendingClaims(temporaryToken);
		User user = userRepository.findById(claims.userId())
				.orElseThrow(InvalidCredentialsException::new);
		otpAttemptService.assertAttemptsAllowed(user.getId());
		if (!consumeMatchingCode(user, code)) {
			otpAttemptService.recordFailure(user.getId());
			throw new InvalidCredentialsException("Invalid or expired code.");
		}
		otpAttemptService.resetFailures(user.getId());
		otpAttemptService.markPendingTokenConsumed(claims.jti(), claims.expiresAt());
		return jwtService.issueTokens(user);
	}

	@Override
	public AuthResponse refresh(RefreshTokenRequest request) {
		JwtService.Claims claims = jwtService.parseAndValidate(request.refreshToken(), JwtService.TokenType.REFRESH);
		User user = userRepository.findByUsername(claims.subject())
				.orElseThrow(InvalidCredentialsException::new);
		return jwtService.issueTokens(user);
	}

	// --- internal ---

	private OtpRequiredResponse issueOtpAndBuildResponse(User user) {
		String temporaryToken = jwtService.generateOtpPendingToken(user);
		EmailOtpSentResponse sent = sendOtp(user);
		return OtpRequiredResponse.builder()
				.otpRequired(true)
				.temporaryToken(temporaryToken)
				.maskedEmail(sent.maskedEmail())
				.expiresInSeconds(otpProps.codeTtl().toSeconds())
				.build();
	}

	private EmailOtpSentResponse sendOtp(User user) {
		LocalDateTime now = LocalDateTime.now();
		EmailOtp existing = emailOtpRepository.findById(user.getId()).orElse(null);
		if (existing != null) {
			LocalDateTime cooldownEnds = existing.getSentAt().plus(otpProps.resendCooldown());
			if (now.isBefore(cooldownEnds)) {
				throw new OtpLockedException(Duration.between(now, cooldownEnds).getSeconds());
			}
		}

		String code = generateNumericCode(6);
		emailOtpRepository.save(EmailOtp.builder()
				.userId(user.getId())
				.codeHash(passwordEncoder.encode(code))
				.expiresAt(now.plus(otpProps.codeTtl()))
				.sentAt(now)
				.build());
		otpMailSender.sendOtpCode(user.getEmail(), user.getUsername(), code);
		return EmailOtpSentResponse.builder()
				.sent(true)
				.maskedEmail(maskEmail(user.getEmail()))
				.expiresInSeconds(otpProps.codeTtl().toSeconds())
				.build();
	}

	/** Single use: the stored code is cleared on success and overwritten by every new send. */
	private boolean consumeMatchingCode(User user, String code) {
		EmailOtp otp = emailOtpRepository.findById(user.getId()).orElse(null);
		if (otp == null || otp.getExpiresAt().isBefore(LocalDateTime.now())) {
			return false;
		}
		if (!passwordEncoder.matches(code, otp.getCodeHash())) {
			return false;
		}
		emailOtpRepository.delete(otp);
		return true;
	}

	private JwtService.Claims requirePendingClaims(String temporaryToken) {
		JwtService.Claims claims = jwtService.parseAndValidate(temporaryToken, JwtService.TokenType.OTP_PENDING);
		if (claims.userId() == null) {
			throw new InvalidTokenException();
		}
		if (otpAttemptService.isPendingTokenConsumed(claims.jti())) {
			throw new InvalidTokenException("Temporary token already used.");
		}
		return claims;
	}

	private String generateNumericCode(int digits) {
		StringBuilder sb = new StringBuilder(digits);
		for (int i = 0; i < digits; i++) {
			sb.append(random.nextInt(10));
		}
		return sb.toString();
	}

	private String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}
}
