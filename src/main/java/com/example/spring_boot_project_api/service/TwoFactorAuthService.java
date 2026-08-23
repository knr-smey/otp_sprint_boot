package com.example.spring_boot_project_api.service;

import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.BackupCodesResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorEnableResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorSetupResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorStatusResponse;

public interface TwoFactorAuthService {

	/** Generates a secret + otpauth URI but does NOT enable 2FA until {@link #enable} succeeds. */
	TwoFactorSetupResponse setup(String username);

	/** Confirms possession of the staged secret; enables 2FA and issues one-time backup codes. */
	TwoFactorEnableResponse enable(String username, String code);
	
	/**
	 * Second login step for 2FA-enabled accounts: validates the pending token from
	 * {@code POST /auth/login} and the TOTP/backup code, then issues real tokens.
	 * Accepts a current TOTP code or an unused backup code.
	 */
	AuthResponse verify(String temporaryToken, String code);

	TwoFactorStatusResponse disable(String username, String code);

	BackupCodesResponse regenerateBackupCodes(String username, String code);
}
