package com.example.spring_boot_project_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Shared body for {@code POST /auth/2fa/enable}, {@code /disable} and backup-code regeneration. */
public record TwoFactorCodeRequest(

		@NotBlank
		@Pattern(regexp = "^\\d{6}$", message = "must be exactly 6 digits")
		String code) {

	public TwoFactorCodeRequest(String code) {
		this.code = code == null ? null : code.trim();
	}
}
