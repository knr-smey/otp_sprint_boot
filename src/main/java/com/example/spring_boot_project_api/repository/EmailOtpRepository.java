package com.example.spring_boot_project_api.repository;

import com.example.spring_boot_project_api.model.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
}
