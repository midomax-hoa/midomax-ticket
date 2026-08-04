-- Người giao khi bàn giao tài sản: thêm chức vụ, phòng ban và địa điểm.
ALTER TABLE `assets` ADD COLUMN `handover_by_position` VARCHAR(255) NULL;
ALTER TABLE `assets` ADD COLUMN `handover_by_department` VARCHAR(255) NULL;
ALTER TABLE `assets` ADD COLUMN `handover_by_location` VARCHAR(255) NULL;
