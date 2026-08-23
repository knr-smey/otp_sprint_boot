package com.example.spring_boot_project_api.service;

/** Sends one-time login codes by email. Implementations must never log the code. */
public interface OtpMailSender {

	/** @return true when the feature is switched on and SMTP is usable */
	boolean isEnabled();

	/**
	 * @param code the plaintext one-time code - delivered to the user only
	 * @throws IllegalStateException if the sender is not enabled/configured
	 */
	void sendOtpCode(String toEmail, String username, String code);
}
