package com.example.spring_boot_project_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
		@NotBlank
		String temporaryToken,

		@NotBlank
		@Pattern(regexp = "^\\d{6}$", message = "must be exactly 6 digits")
		String code) {
}
