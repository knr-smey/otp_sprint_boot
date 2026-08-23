package com.example.spring_boot_project_api.repository;

import com.example.spring_boot_project_api.model.TwoFactorAuth;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TwoFactorAuthRepository extends JpaRepository<TwoFactorAuth, Long> {
}
