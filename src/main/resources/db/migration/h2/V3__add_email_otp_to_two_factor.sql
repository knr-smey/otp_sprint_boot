-- Email OTP fallback: a hashed single-use code mailed to the user's address,
-- used when they cannot access their authenticator app.
ALTER TABLE user_two_factor_auth
    ADD COLUMN email_otp_hash VARCHAR(255) NULL;
ALTER TABLE user_two_factor_auth
    ADD COLUMN email_otp_expires_at TIMESTAMP(6) NULL;
ALTER TABLE user_two_factor_auth
    ADD COLUMN email_otp_sent_at TIMESTAMP(6) NULL;
