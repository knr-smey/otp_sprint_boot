package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.service.CryptoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM. Output format: {@code Base64(IV[12] || ciphertext+tag)} with a fresh random IV
 * per value, so identical plaintexts never produce identical ciphertexts.
 *
 * <p>The key comes from the {@code app.crypto.totp-key} property
 * ({@code TOTP_ENCRYPTION_KEY} env var in production) and is validated at startup.</p>
 */
@Service
public class AesGcmCryptoServiceImpl implements CryptoService {

	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BYTES = 32;
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public AesGcmCryptoServiceImpl(@Value("${app.crypto.totp-key}") String base64Key) {
		byte[] raw = Base64.getDecoder().decode(base64Key);
		if (raw.length != KEY_BYTES) {
			throw new IllegalStateException(
					"app.crypto.totp-key must decode to exactly " + KEY_BYTES + " bytes (AES-256); "
							+ "generate one with: openssl rand -base64 32");
		}
		this.key = new SecretKeySpec(raw, "AES");
	}

	@Override
	public String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cipherText.length)
					.put(iv)
					.put(cipherText)
					.array());
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Encryption failed", ex);
		}
	}

	@Override
	public String decrypt(String ciphertext) {
		byte[] decoded;
		try {
			decoded = Base64.getDecoder().decode(ciphertext);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Malformed encrypted payload");
		}
		if (decoded.length <= IV_BYTES) {
			throw new IllegalArgumentException("Malformed encrypted payload");
		}
		ByteBuffer buffer = ByteBuffer.wrap(decoded);
		byte[] iv = new byte[IV_BYTES];
		buffer.get(iv);
		byte[] cipherText = new byte[buffer.remaining()];
		buffer.get(cipherText);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException ex) {
			throw new IllegalArgumentException("Decryption failed");
		}
	}
}
