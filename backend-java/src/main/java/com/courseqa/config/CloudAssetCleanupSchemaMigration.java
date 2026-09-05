package com.courseqa.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Additive bootstrap so the durable cleanup queue is available after deployment. */
@Component
public class CloudAssetCleanupSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public CloudAssetCleanupSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                IF OBJECT_ID('cloud_asset_cleanup_jobs', 'U') IS NULL
                BEGIN
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
                END
                """);
        jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cloud_asset_cleanup_due')
                    CREATE INDEX ix_cloud_asset_cleanup_due
                    ON cloud_asset_cleanup_jobs(status, next_attempt_at, created_at);
                """);
        jdbcTemplate.execute("""
                IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_cloud_asset_cleanup_pending')
                    DROP INDEX ux_cloud_asset_cleanup_pending ON cloud_asset_cleanup_jobs;
                IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cloud_asset_cleanup_asset')
                    CREATE INDEX ix_cloud_asset_cleanup_asset
                    ON cloud_asset_cleanup_jobs(public_id, resource_type, status);
                """);
    }
}
