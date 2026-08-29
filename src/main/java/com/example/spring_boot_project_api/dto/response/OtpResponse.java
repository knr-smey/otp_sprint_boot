package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OtpResponse {
    private String message;
    private String email;
}