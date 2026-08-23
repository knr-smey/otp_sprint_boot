package com.example.spring_boot_project_api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The user's current pending login OTP. Only the BCrypt hash is stored - the plaintext
 * code exists solely inside the outgoing email. A new send overwrites the previous row,
 * so exactly one code is ever outstanding per user.
 */
@Entity
@Table(name = "user_email_otp")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class EmailOtp {

	@Id
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "code_hash", nullable = false, length = 255)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "sent_at", nullable = false)
	private LocalDateTime sentAt;

	@PreUpdate
	void onUpdate() {
		sentAt = LocalDateTime.now();
	}
}
