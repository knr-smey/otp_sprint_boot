package com.example.spring_boot_project_api.config;

import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates {@code Authorization: Bearer <access token>} and populates the security context.
 * Only {@link JwtService.TokenType#ACCESS} tokens authenticate API calls - refresh and
 * 2FA-pending tokens are deliberately rejected here so a pending token can never be used
 * to reach any endpoint except {@code POST /auth/2fa/verify}.
 *
 * <p>Deliberately NOT a {@code @Component}: it must live solely inside the security filter
 * chain (see {@link SecurityConfig}). Registering it additionally as a servlet filter would
 * let it run before the chain's {@code SecurityContextHolderFilter}, which would overwrite
 * the authentication this filter establishes.</p>
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			try {
				JwtService.Claims claims = jwtService.parseAndValidate(
						header.substring(BEARER_PREFIX.length()), JwtService.TokenType.ACCESS);
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						claims.subject(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (InvalidTokenException ex) {
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}
}
