package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.dto.request.LoginRequest;
import com.example.spring_boot_project_api.dto.request.RefreshTokenRequest;
import com.example.spring_boot_project_api.dto.request.RegisterRequest;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.EmailOtpSentResponse;
import com.example.spring_boot_project_api.dto.response.OtpRequiredResponse;
import com.example.spring_boot_project_api.dto.response.UserResponse;

public interface AuthService {

	UserResponse register(RegisterRequest request);

	/**
	 * Step 1 of login: verifies credentials, mails a single-use OTP to the user's
	 * registered address and returns a pending token. No tokens are issued yet.
	 */
	OtpRequiredResponse login(LoginRequest request);

	/** Resends the OTP for an existing pending token (resend cooldown applies). */
	EmailOtpSentResponse resendOtp(String temporaryToken);

	/**
	 * Step 2 of login: validates the pending token and the emailed OTP code,
	 * then issues the real access/refresh tokens.
	 */
	AuthResponse verifyOtp(String temporaryToken, String code);

	AuthResponse refresh(RefreshTokenRequest request);
}
