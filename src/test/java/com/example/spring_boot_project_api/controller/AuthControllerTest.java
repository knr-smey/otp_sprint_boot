package com.example.spring_boot_project_api.controller;

import com.example.spring_boot_project_api.config.RestSecurityProblemHandler;
import com.example.spring_boot_project_api.config.SecurityConfig;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.LoginResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorSetupResponse;
import com.example.spring_boot_project_api.exception.InvalidCredentialsException;
import com.example.spring_boot_project_api.service.AuthService;
import com.example.spring_boot_project_api.service.JwtService;
import com.example.spring_boot_project_api.service.TwoFactorAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, TwoFactorAuthController.class})
@Import({SecurityConfig.class, RestSecurityProblemHandler.class})
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;
	@MockitoBean
	private TwoFactorAuthService twoFactorAuthService;
	@MockitoBean
	private JwtService jwtService;

	private static final String LOGIN_BODY = "{\"username\":\"alice\",\"password\":\"secret123\"}";

	private static JwtService.Claims claims(String subject) {
		return new JwtService.Claims(subject, 1L, "jti", JwtService.TokenType.ACCESS,
				Instant.now().plus(java.time.Duration.ofMinutes(10)));
	}

	@Test
	void login_when2faDisabled_returnsTokens() throws Exception {
		when(authService.login(any())).thenReturn(LoginResponse.authenticated(AuthResponse.builder()
				.tokenType("Bearer").accessToken("access-token").refreshToken("refresh-token").build()));

		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.twoFactorRequired").value(false))
				.andExpect(jsonPath("$.accessToken").value("access-token"))
				.andExpect(jsonPath("$.refreshToken").value("refresh-token"));
	}

	@Test
	void login_when2faEnabled_returnsTemporaryTokenOnly() throws Exception {
		when(authService.login(any())).thenReturn(LoginResponse.twoFactorRequired("temp-token"));

		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.twoFactorRequired").value(true))
				.andExpect(jsonPath("$.temporaryToken").value("temp-token"))
				.andExpect(jsonPath("$.accessToken").doesNotExist())
				.andExpect(jsonPath("$.refreshToken").doesNotExist());
	}

	@Test
	void login_withWrongPassword_returns401() throws Exception {
		when(authService.login(any())).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid username or password."));
	}

	@Test
	void verify_isPublic_andReturnsTokens() throws Exception {
		when(twoFactorAuthService.verify("temp", "123456")).thenReturn(AuthResponse.builder()
				.tokenType("Bearer").accessToken("access").refreshToken("refresh").build());

		mockMvc.perform(post("/auth/2fa/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"temporaryToken\":\"temp\",\"code\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access"));
	}

	@Test
	void verify_withMalformedCode_returns400() throws Exception {
		mockMvc.perform(post("/auth/2fa/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"temporaryToken\":\"temp\",\"code\":\"12345\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void disable_unauthenticated_returns401() throws Exception {
		mockMvc.perform(post("/auth/2fa/disable")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"123456\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void setup_withAccessToken_succeeds() throws Exception {
		when(jwtService.parseAndValidate(eq("good-access-token"), eq(JwtService.TokenType.ACCESS)))
				.thenReturn(claims("alice"));
		when(twoFactorAuthService.setup("alice")).thenReturn(TwoFactorSetupResponse.builder()
				.enabled(false).secret("SECRET234567").otpauthUri("otpauth://totp/x").build());

		mockMvc.perform(post("/auth/2fa/setup")
						.header("Authorization", "Bearer good-access-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.secret").value("SECRET234567"))
				.andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/x"));

		verify(jwtService).parseAndValidate(eq("good-access-token"), eq(JwtService.TokenType.ACCESS));

		org.mockito.Mockito.verify(jwtService)
				.parseAndValidate(eq("good-access-token"), eq(JwtService.TokenType.ACCESS));
	}

	@Test
	void enable_requiresAuthentication() throws Exception {
		mockMvc.perform(post("/auth/2fa/enable")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"123456\"}"))
				.andExpect(status().isUnauthorized());
	}
}
