package com.example.spring_boot_project_api.controller;

import com.example.spring_boot_project_api.dto.request.SendOtpRequest;
import com.example.spring_boot_project_api.dto.response.OtpResponse;
import com.example.spring_boot_project_api.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    public ResponseEntity<OtpResponse> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        OtpResponse response = otpService.sendOtp(request);
        return ResponseEntity.ok(response);
    }
}