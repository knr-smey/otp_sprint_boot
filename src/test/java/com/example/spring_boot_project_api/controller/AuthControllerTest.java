package com.example.spring_boot_project_api.controller;

import com.example.spring_boot_project_api.config.SecurityConfig;
import com.example.spring_boot_project_api.config.RestSecurityProblemHandler;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.EmailOtpSentResponse;
import com.example.spring_boot_project_api.dto.response.OtpRequiredResponse;
import com.example.spring_boot_project_api.dto.response.UserResponse;
import com.example.spring_boot_project_api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the public auth surface: register, two-step OTP login, resend, refresh.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, RestSecurityProblemHandler.class})
class AuthControllerTest {

	private static final String REGISTER_BODY =
			"{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"secret123\"}";
	private static final String LOGIN_BODY = "{\"username\":\"alice\",\"password\":\"secret123\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private com.example.spring_boot_project_api.service.JwtService jwtService;

	// --- register ---

	@Test
	void register_returns201_andCreatedUser() throws Exception {
		when(authService.register(any())).thenReturn(new UserResponse(1L, "alice", "alice@example.com"));

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(REGISTER_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.username").value("alice"))
				.andExpect(jsonPath("$.email").value("alice@example.com"));

		ArgumentCaptor<com.example.spring_boot_project_api.dto.request.RegisterRequest> captor =
				ArgumentCaptor.forClass(com.example.spring_boot_project_api.dto.request.RegisterRequest.class);
		verify(authService).register(captor.capture());
		assertThat(captor.getValue().email()).isEqualTo("alice@example.com");
	}

	@Test
	void register_invalidBody_returns400() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"a\",\"email\":\"nope\",\"password\":\"x\"}"))
				.andExpect(status().isBadRequest());
	}

	// --- login step 1 ---

	@Test
	void login_returnsOtpRequired_withTemporaryToken() throws Exception {
		when(authService.login(any())).thenReturn(OtpRequiredResponse.builder()
				.otpRequired(true)
				.temporaryToken("pending-token")
				.maskedEmail("a***@example.com")
				.expiresInSeconds(300)
				.build());

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(LOGIN_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.otpRequired").value(true))
				.andExpect(jsonPath("$.temporaryToken").value("pending-token"))
				.andExpect(jsonPath("$.maskedEmail").value("a***@example.com"));
	}

	@Test
	void login_wrongPassword_returns401() throws Exception {
		when(authService.login(any()))
				.thenThrow(new com.example.spring_boot_project_api.exception.InvalidCredentialsException());

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(LOGIN_BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid username or password."));
	}

	// --- otp endpoints ---

	@Test
	void otpVerify_correctCode_returnsTokens() throws Exception {
		when(authService.verifyOtp(eq("pending-token"), eq("123456")))
				.thenReturn(AuthResponse.builder()
						.tokenType("Bearer").accessToken("at").refreshToken("rt").build());

		mockMvc.perform(post("/auth/otp/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"temporaryToken\":\"pending-token\",\"code\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("at"))
				.andExpect(jsonPath("$.refreshToken").value("rt"));
	}

	@Test
	void otpSend_resends_withinCooldownRules() throws Exception {
		when(authService.resendOtp("pending-token")).thenReturn(EmailOtpSentResponse.builder()
				.sent(true).maskedEmail("a***@example.com").expiresInSeconds(300).build());

		mockMvc.perform(post("/auth/otp/send")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"temporaryToken\":\"pending-token\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sent").value(true));
	}
}
