-- Việc cha có việc con: khi mọi việc con đạt 100% thì thanh tổng chỉ dừng ở 90%,
-- phải chờ người tạo báo cáo (created_by) bấm xác nhận mới lên 100% / COMPLETED.
ALTER TABLE `work_reports` ADD COLUMN `owner_confirmed` TINYINT(1) NOT NULL DEFAULT 0;

-- Dữ liệu cũ: việc cha đang là 100% coi như đã được chủ báo cáo chốt, tránh bị tụt về 90%.
UPDATE `work_reports`
SET `owner_confirmed` = 1
WHERE `progress_percentage` = 100 OR UPPER(`status`) = 'COMPLETED';
