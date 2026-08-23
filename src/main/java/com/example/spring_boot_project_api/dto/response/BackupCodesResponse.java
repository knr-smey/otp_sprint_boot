package com.example.spring_boot_project_api.dto.response;

import lombok.Builder;

import java.util.List;

/** Fresh backup codes from {@code POST /auth/2fa/backup-codes}; plaintext shown once. */
@Builder
public record BackupCodesResponse(
		List<String> backupCodes) {
}
