package com.example.spring_boot_project_api.service;

/**
 * Symmetric encryption for sensitive values at rest (currently: TOTP secrets).
 * Implementations must authenticate ciphertexts (AEAD) so tampering is detected.
 */
public interface CryptoService {

	String encrypt(String plaintext);

	/** @throws IllegalArgumentException if the ciphertext is malformed or fails authentication */
	String decrypt(String ciphertext);
}
