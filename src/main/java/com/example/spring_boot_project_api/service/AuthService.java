package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.dto.request.LoginRequest;
import com.example.spring_boot_project_api.dto.request.RefreshTokenRequest;
import com.example.spring_boot_project_api.dto.request.RegisterRequest;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.LoginResponse;
import com.example.spring_boot_project_api.dto.response.UserResponse;

public interface AuthService {

	UserResponse register(RegisterRequest request);

	/**
	 * Password check + 2FA branch point: full tokens when 2FA is off, otherwise only a
	 * short-lived pending token that must be completed via {@code POST /auth/2fa/verify}.
	 */
	LoginResponse login(LoginRequest request);

	AuthResponse refresh(RefreshTokenRequest request);
}
