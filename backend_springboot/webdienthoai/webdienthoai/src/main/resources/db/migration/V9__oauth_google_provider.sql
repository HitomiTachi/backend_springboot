ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(20) NULL AFTER email_verified_at,
    ADD COLUMN provider_id VARCHAR(191) NULL AFTER auth_provider;

UPDATE users
SET auth_provider = COALESCE(auth_provider, 'LOCAL')
WHERE auth_provider IS NULL;

CREATE INDEX idx_users_auth_provider_provider_id ON users (auth_provider, provider_id);
