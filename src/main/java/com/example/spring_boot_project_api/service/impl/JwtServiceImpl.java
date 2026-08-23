package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.config.JwtProperties;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.exception.InvalidTokenException;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.service.JwtService;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtServiceImpl implements JwtService {

	private static final String CLAIM_TYPE = "typ";
	private static final String CLAIM_USER_ID = "uid";
	private static final String TOKEN_TYPE_BEARER = "Bearer";

	private final JwtProperties props;
	private final JwtEncoder encoder;
	private final JwtDecoder decoder;

	public JwtServiceImpl(JwtProperties props) {
		this.props = props;
		SecretKey key = hmacKey(props.secret());
		JWK jwk = new OctetSequenceKey.Builder(key).keyID("hmac-signing-key").build();
		JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
		this.encoder = new NimbusJwtEncoder(jwks);
		NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		nimbusDecoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
		this.decoder = nimbusDecoder;
	}

	@Override
	public Claims parseAndValidate(String token, TokenType expectedType) {
		if (token == null || token.isBlank()) {
			throw new InvalidTokenException();
		}
		Jwt jwt;
		try {
			jwt = decoder.decode(token);
		} catch (BadJwtException | IllegalArgumentException ex) {
			throw new InvalidTokenException();
		}
		TokenType actualType = typeOf(jwt);
		if (actualType != expectedType || !props.issuer().equals(jwt.getClaimAsString("iss"))) {
			throw new InvalidTokenException();
		}
		Object uid = jwt.getClaim(CLAIM_USER_ID);
		return new Claims(
				jwt.getSubject(),
				uid instanceof Number number ? number.longValue() : null,
				jwt.getId(),
				actualType,
				jwt.getExpiresAt());
	}

	@Override
	public AuthResponse issueTokens(User user) {
		String access = encode(user, TokenType.ACCESS, props.accessTokenTtl());
		String refresh = encode(user, TokenType.REFRESH, props.refreshTokenTtl());
		return AuthResponse.builder()
				.tokenType(TOKEN_TYPE_BEARER)
				.accessToken(access)
				.refreshToken(refresh)
				.build();
	}

	@Override
	public String generateOtpPendingToken(User user) {
		return encode(user, TokenType.OTP_PENDING, props.otpPendingTtl());
	}

	private String encode(User user, TokenType type, Duration ttl) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(props.issuer())
				.subject(user.getUsername())
				.id(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plus(ttl))
				.claim(CLAIM_TYPE, type.name())
				.claim(CLAIM_USER_ID, user.getId())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private TokenType typeOf(Jwt jwt) {
		String raw = jwt.getClaimAsString(CLAIM_TYPE);
		try {
			return TokenType.valueOf(raw);
		} catch (IllegalArgumentException | NullPointerException ex) {
			throw new InvalidTokenException();
		}
	}

	private static SecretKey hmacKey(String secret) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}
}
