package com.example.spring_boot_project_api.config;

import com.example.spring_boot_project_api.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes the standard {@link ApiErrorResponse} JSON for requests rejected by the
 * security filter chain (before they reach the controller advice).
 */
@Component
public class RestSecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Authentication required.");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Access denied.");
	}

	private void write(HttpServletResponse response, int status, String error, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		MAPPER.writeValue(response.getWriter(),
				new ApiErrorResponse(status, error, message, Instant.now()));
	}
}
