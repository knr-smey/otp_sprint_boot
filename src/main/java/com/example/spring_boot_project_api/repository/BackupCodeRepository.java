package com.example.spring_boot_project_api.repository;

import com.example.spring_boot_project_api.model.BackupCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackupCodeRepository extends JpaRepository<BackupCode, Long> {

	List<BackupCode> findByUserIdAndUsedAtIsNull(Long userId);

	void deleteByUserId(Long userId);
}
