-- Email is required and unique per user (registration scope decision).
ALTER TABLE users
    ADD COLUMN email VARCHAR(255) NOT NULL;
ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email);
