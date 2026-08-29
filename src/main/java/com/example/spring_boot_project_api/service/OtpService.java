package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.dto.request.SendOtpRequest;
import com.example.spring_boot_project_api.dto.response.OtpResponse;

public interface OtpService {
    // object
    OtpResponse sendOtp(SendOtpRequest request);
}