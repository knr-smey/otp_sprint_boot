package com.example.spring_boot_project_api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

		@NotBlank
		String username,

		@NotBlank
		String password) {
}
