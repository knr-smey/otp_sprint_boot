-- Product simplification: email-OTP login is the only second step.
-- TOTP (Google Authenticator) material and backup codes are removed.
DROP TABLE IF EXISTS two_factor_backup_code;
DROP TABLE IF EXISTS user_two_factor_auth;
ALTER TABLE users
    DROP COLUMN two_factor_enabled;

-- Pending login OTP: hashed single-use code mailed after a successful password check.
CREATE TABLE user_email_otp (
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_user_email_otp PRIMARY KEY (user_id),
    CONSTRAINT fk_email_otp_user FOREIGN KEY (user_id) REFERENCES users (id)
);
