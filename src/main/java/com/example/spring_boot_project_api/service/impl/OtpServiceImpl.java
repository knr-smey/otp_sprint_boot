package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.dto.request.SendOtpRequest;
import com.example.spring_boot_project_api.dto.response.OtpResponse;
import com.example.spring_boot_project_api.service.OtpService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.from-name:OTP Service}")
    private String fromName;

    @Override
    public OtpResponse sendOtp(SendOtpRequest request) {
        String otpCode = String.format("%06d", new SecureRandom().nextInt(1000000));

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            // Sets sender format to: "My Awesome App <krisjenxz@gmail.com>"
            helper.setFrom(fromEmail, fromName);
            helper.setTo(request.getEmail());
            helper.setSubject("[" + fromName + "] Your Verification Code");
            helper.setText("Your OTP code is: " + otpCode + "\n\nThis code is valid for 5 minutes.");

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage());
        }

        return OtpResponse.builder()
                .message("OTP code successfully dispatched.")
                .email(request.getEmail())
                .build();
    }
}