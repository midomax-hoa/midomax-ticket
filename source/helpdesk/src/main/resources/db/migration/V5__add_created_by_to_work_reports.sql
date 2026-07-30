-- Add created_by column to track who created each report
ALTER TABLE `work_reports` ADD COLUMN `created_by` VARCHAR(255) COLLATE utf8mb4_unicode_ci;

-- Backfill existing reports: assume creator is the assignee (historical data)
UPDATE `work_reports` SET `created_by` = `assignee` WHERE `created_by` IS NULL;

-- Make column NOT NULL after backfill
ALTER TABLE `work_reports` MODIFY COLUMN `created_by` VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL;

-- Add index for efficient filtering by creator
CREATE INDEX `idx_created_by` ON `work_reports`(`created_by`);
