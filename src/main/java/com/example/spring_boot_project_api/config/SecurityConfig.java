package com.example.spring_boot_project_api.config;

import com.example.spring_boot_project_api.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final RestSecurityProblemHandler restSecurityProblemHandler;
	private final JwtService jwtService;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Stateless bearer-token API. CSRF is disabled deliberately: no cookies, no server-side
	 * session - every request is authenticated by the {@code Authorization} header alone,
	 * so there is no ambient credential to forge (standard practice for token APIs).
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http)
			throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST,
								"/auth/register",
								"/auth/login",
								"/auth/refresh",
								"/auth/2fa/verify").permitAll()
						.requestMatchers(
								"/v3/api-docs/**",
								"/swagger-ui/**",
								"/swagger-ui.html").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(restSecurityProblemHandler)
						.accessDeniedHandler(restSecurityProblemHandler))
				.addFilterBefore(new JwtAuthenticationFilter(jwtService),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
