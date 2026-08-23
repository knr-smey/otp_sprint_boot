package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.config.OtpProperties;
import com.example.spring_boot_project_api.service.OtpMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmtpOtpMailSender implements OtpMailSender {

	private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
	private final OtpProperties props;

	@Override
	public boolean isEnabled() {
		return props.emailEnabled() && javaMailSenderProvider.getIfAvailable() != null;
	}

	@Override
	public void sendOtpCode(String toEmail, String username, String code) {
		if (!isEnabled()) {
			throw new IllegalStateException("Email OTP is not enabled or SMTP is not configured.");
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(toEmail);
		message.setSubject("Your " + props.issuer() + " login code");
		message.setText("""
				Hi %s,

				Your one-time login code is:

				%s

				It expires in %d minutes and can be used only once.
				If you did not request it, ignore this email.

				- %s""".formatted(username, code, props.codeTtl().toMinutes(), props.issuer()));
		javaMailSenderProvider.getObject().send(message);
	}
}
