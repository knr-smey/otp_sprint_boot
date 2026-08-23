package com.example.spring_boot_project_api.service.impl;

import com.example.spring_boot_project_api.config.TwoFactorProperties;
import com.example.spring_boot_project_api.service.TotpService;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class TotpServiceImpl implements TotpService {

	private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");
	private static final int TIME_PERIOD_SECONDS = 30;
	private static final int ALLOWED_DRIFT_PERIODS = 1;
	private static final int CODE_DIGITS = 6;

	private final String issuer;
	private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
	private final CodeVerifier codeVerifier;

	public TotpServiceImpl(TwoFactorProperties props) {
		this.issuer = props.issuer();
		CodeGenerator generator = new DefaultCodeGenerator();
		DefaultCodeVerifier verifier = new DefaultCodeVerifier(generator, new SystemTimeProvider());
		verifier.setTimePeriod(TIME_PERIOD_SECONDS);
		verifier.setAllowedTimePeriodDiscrepancy(ALLOWED_DRIFT_PERIODS);
		this.codeVerifier = verifier;
	}

	@Override
	public String generateSecret() {
		return secretGenerator.generate();
	}

	@Override
	public String buildOtpauthUri(String secret, String accountName) {
		QrData data = new QrData.Builder()
				.label(issuer + ":" + accountName)
				.secret(secret)
				.issuer(issuer)
				.algorithm(HashingAlgorithm.SHA1)
				.digits(CODE_DIGITS)
				.period(TIME_PERIOD_SECONDS)
				.build();
		return data.getUri();
	}

	@Override
	public boolean verify(String secret, String code) {
		if (secret == null || code == null || !SIX_DIGITS.matcher(code).matches()) {
			return false;
		}
		return codeVerifier.isValidCode(secret, code);
	}
}
