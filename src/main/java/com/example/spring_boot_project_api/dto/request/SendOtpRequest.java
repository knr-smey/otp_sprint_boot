package com.example.spring_boot_project_api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendOtpRequest {

    @NotBlank(message = "Email address is required")
    @Email(message = "Email must be a valid email address")
    private String email;
}