package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.service.impl.AesGcmCryptoServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCryptoServiceImplTest {

	private static final String KEY = "QNiK7ASt82FAhg3ekAmQt+HCz/lXoNjZKYtZB+pdltA=";

	private final AesGcmCryptoServiceImpl cryptoService = new AesGcmCryptoServiceImpl(KEY);

	@Test
	void encryptDecrypt_roundTrips() {
		String secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

		String encrypted = cryptoService.encrypt(secret);

		assertThat(encrypted).isNotEqualTo(secret);
		assertThat(cryptoService.decrypt(encrypted)).isEqualTo(secret);
	}

	@Test
	void samePlaintext_producesDifferentCiphertexts_randomIv() {
		assertThat(cryptoService.encrypt("same-value")).isNotEqualTo(cryptoService.encrypt("same-value"));
	}

	@Test
	void tamperedCiphertext_isRejected() {
		String encrypted = cryptoService.encrypt("top-secret");
		String tampered = "AAAA" + encrypted.substring(4);

		assertThatThrownBy(() -> cryptoService.decrypt(tampered))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void keyMustBe32Bytes() {
		String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

		assertThatThrownBy(() -> new AesGcmCryptoServiceImpl(shortKey))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}
}
