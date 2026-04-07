ALTER TABLE users ADD COLUMN email_verified_at DATETIME(6) NULL;

-- Tài khoản hiện có: coi như đã xác minh (tránh bắt verify lại)
UPDATE users SET email_verified_at = COALESCE(created_at, UTC_TIMESTAMP(6)) WHERE email_verified_at IS NULL;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_email_verification_tokens_token UNIQUE (token),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);
