package com.example.spring_boot_project_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One-time recovery code for 2FA. Only the {@link #codeHash BCrypt hash} is stored;
 * {@code usedAt != null} marks a consumed (single-use) code.
 */
@Entity
@Table(name = "two_factor_backup_code")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BackupCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "code_hash", nullable = false, length = 255)
	private String codeHash;

	@Column(name = "used_at")
	private LocalDateTime usedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
}
