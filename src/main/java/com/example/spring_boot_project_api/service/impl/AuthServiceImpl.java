package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.dto.request.LoginRequest;
import com.example.spring_boot_project_api.dto.request.RefreshTokenRequest;
import com.example.spring_boot_project_api.dto.request.RegisterRequest;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.LoginResponse;
import com.example.spring_boot_project_api.dto.response.UserResponse;
import com.example.spring_boot_project_api.exception.DuplicateUsernameException;
import com.example.spring_boot_project_api.exception.InvalidCredentialsException;
import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.mapper.UserMapper;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.AuthService;
import com.example.spring_boot_project_api.service.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

	/** BCrypt hash of an unrelated throwaway value; keeps timing equal for unknown usernames. */
	private static final String DUMMY_PASSWORD_HASH =
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthServiceImpl(UserRepository userRepository,
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Override
	@Transactional
	public UserResponse register(RegisterRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new DuplicateUsernameException(request.username());
		}
		User user = userRepository.save(User.builder()
				.username(request.username())
				.passwordHash(passwordEncoder.encode(request.password()))
				.twoFactorEnabled(false)
				.build());
		return userMapper.toResponse(user);
	}

	@Override
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByUsername(request.username()).orElse(null);
		boolean passwordOk = user != null
				&& passwordEncoder.matches(request.password(), user.getPasswordHash());
		if (!passwordOk) {
			// Same failure for unknown user and wrong password; burns one BCrypt round to equalize timing.
			passwordEncoder.matches("timing-equalization", DUMMY_PASSWORD_HASH);
			throw new InvalidCredentialsException();
		}
		if (user.isTwoFactorEnabled()) {
			return LoginResponse.twoFactorRequired(jwtService.generateTwoFactorPendingToken(user));
		}
		return LoginResponse.authenticated(jwtService.issueTokens(user));
	}

	@Override
	public AuthResponse refresh(RefreshTokenRequest request) {
		JwtService.Claims claims = jwtService.parseAndValidate(request.refreshToken(), JwtService.TokenType.REFRESH);
		User user = userRepository.findByUsername(claims.subject())
				.orElseThrow(InvalidCredentialsException::new);
		return jwtService.issueTokens(user);
	}
}
