-- Initial schema: authentication foundation + TOTP two-factor support.
-- Existing behaviour is unaffected: every user starts with two_factor_enabled = FALSE.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
);

-- One row per user who has started (or completed) 2FA enrollment.
-- Secrets are stored only as AES-GCM ciphertext, never plaintext.
CREATE TABLE user_two_factor_auth (
    user_id BIGINT NOT NULL,
    secret_enc VARCHAR(512) NULL,
    pending_secret_enc VARCHAR(512) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_two_factor_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Backup codes are stored hashed only; used_at marks one-time consumption.
CREATE TABLE two_factor_backup_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_backup_code_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
