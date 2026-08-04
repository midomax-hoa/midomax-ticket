-- Bàn giao công cụ dụng cụ: ghi thêm người giao và thời gian bàn giao.
ALTER TABLE `assets` ADD COLUMN `handover_by` VARCHAR(255) NULL;
ALTER TABLE `assets` ADD COLUMN `handover_date` DATETIME NULL;
