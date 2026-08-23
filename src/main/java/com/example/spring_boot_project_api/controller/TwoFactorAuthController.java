package com.example.spring_boot_project_api.controller;

import com.example.spring_boot_project_api.dto.request.TwoFactorCodeRequest;
import com.example.spring_boot_project_api.dto.request.TwoFactorVerifyRequest;
import com.example.spring_boot_project_api.dto.response.AuthResponse;
import com.example.spring_boot_project_api.dto.response.BackupCodesResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorEnableResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorSetupResponse;
import com.example.spring_boot_project_api.dto.response.TwoFactorStatusResponse;
import com.example.spring_boot_project_api.service.TwoFactorAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * TOTP (Google Authenticator) two-factor endpoints. Everything except
 * {@code /verify} requires an authenticated user.
 */
@RestController
@RequestMapping("/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorAuthController {

	private final TwoFactorAuthService twoFactorAuthService;

	/** Stage a new secret + otpauth URI. 2FA is NOT enabled until /enable confirms it. */
	@PostMapping("/setup")
	public TwoFactorSetupResponse setup(Principal principal) {
		return twoFactorAuthService.setup(principal.getName());
	}

	/** Verify a code against the staged secret; on success enable 2FA and return backup codes once. */
	@PostMapping("/enable")
	public TwoFactorEnableResponse enable(Principal principal, @Valid @RequestBody TwoFactorCodeRequest request) {
		return twoFactorAuthService.enable(principal.getName(), request.code());
	}

	/** Public: completes login for 2FA-enabled accounts (temporary token + 6-digit code). */
	@PostMapping("/verify")
	public AuthResponse verify(@Valid @RequestBody TwoFactorVerifyRequest request) {
		return twoFactorAuthService.verify(request.temporaryToken(), request.code());
	}

	/** Requires the current TOTP or an unused backup code before disabling. */
	@PostMapping("/disable")
	public TwoFactorStatusResponse disable(Principal principal, @Valid @RequestBody TwoFactorCodeRequest request) {
		return twoFactorAuthService.disable(principal.getName(), request.code());
	}

	/** Replaces all backup codes; previous codes become invalid. Requires a valid code. */
	@PostMapping("/backup-codes")
	public BackupCodesResponse regenerateBackupCodes(Principal principal,
			@Valid @RequestBody TwoFactorCodeRequest request) {
		return twoFactorAuthService.regenerateBackupCodes(principal.getName(), request.code());
	}
}
