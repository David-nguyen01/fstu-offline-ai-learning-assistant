SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID('cloud_asset_cleanup_jobs', 'U') IS NULL
        CREATE TABLE cloud_asset_cleanup_jobs(
            cleanup_job_id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
            public_id NVARCHAR(500) NOT NULL,
            resource_type NVARCHAR(30) NOT NULL DEFAULT 'raw',
            status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
            attempt_count INT NOT NULL DEFAULT 0,
            next_attempt_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
            last_error NVARCHAR(1000) NULL,
            created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
            updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
            completed_at DATETIME2 NULL,
            row_version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT chk_cloud_asset_cleanup_status CHECK(status IN('PENDING','COMPLETED'))
        );

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cloud_asset_cleanup_due')
        CREATE INDEX ix_cloud_asset_cleanup_due
        ON cloud_asset_cleanup_jobs(status, next_attempt_at, created_at);

    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_cloud_asset_cleanup_pending')
        DROP INDEX ux_cloud_asset_cleanup_pending ON cloud_asset_cleanup_jobs;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cloud_asset_cleanup_asset')
        CREATE INDEX ix_cloud_asset_cleanup_asset
        ON cloud_asset_cleanup_jobs(public_id, resource_type, status);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
