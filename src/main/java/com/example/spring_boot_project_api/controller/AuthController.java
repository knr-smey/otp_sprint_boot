package com.example.spring_boot_project_api.controller;

import com.example.spring_boot_project_api.dto.request.LoginRequest;
import com.example.spring_boot_project_api.dto.request.OtpSendRequest;
import com.example.spring_boot_project_api.dto.request.OtpVerifyRequest;
import com.example.spring_boot_project_api.dto.request.RefreshTokenRequest;
import com.example.spring_boot_project_api.dto.request.RegisterRequest;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.EmailOtpSentResponse;
import com.example.spring_boot_project_api.dto.response.OtpRequiredResponse;
import com.example.spring_boot_project_api.dto.response.UserResponse;
import com.example.spring_boot_project_api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication: register, two-step login (password, then OTP from email) and refresh.
 * Every endpoint here is public - the API has no other authenticated surface except
 * bearer-token protected resources.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	/** Step 1: checks credentials and emails a single-use OTP to the registered address. */
	@PostMapping("/login")
	public OtpRequiredResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	/** Re-sends the OTP for a pending login (60s cooldown). */
	@PostMapping("/otp/send")
	public EmailOtpSentResponse resendOtp(@Valid @RequestBody OtpSendRequest request) {
		return authService.resendOtp(request.temporaryToken());
	}

	/** Step 2: exchanges the pending token + emailed code for real access/refresh tokens. */
	@PostMapping("/otp/verify")
	public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		return authService.verifyOtp(request.temporaryToken(), request.code());
	}

	@PostMapping("/refresh")
	public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request);
	}
}
