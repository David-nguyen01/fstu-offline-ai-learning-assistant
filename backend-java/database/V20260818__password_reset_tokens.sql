IF OBJECT_ID('password_reset_tokens', 'U') IS NULL
BEGIN
    CREATE TABLE password_reset_tokens (
        reset_token_id UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT pk_password_reset_tokens PRIMARY KEY DEFAULT NEWID(),
        user_id UNIQUEIDENTIFIER NOT NULL,
        token_hash CHAR(64) NOT NULL,
        expires_at DATETIME2 NOT NULL,
        used_at DATETIME2 NULL,
        created_at DATETIME2 NOT NULL DEFAULT GETDATE(),
        CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
        CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
            REFERENCES users(user_id) ON DELETE CASCADE
    );

    CREATE INDEX ix_password_reset_tokens_user_active
        ON password_reset_tokens(user_id, used_at, created_at);
END;
GO
