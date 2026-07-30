-- Ghi lại mốc hoàn thành thực tế của báo cáo công việc.
-- SLA chấm theo mốc này: xong 100% trước hạn chót thì luôn là "Đúng hạn",
-- không bị chuyển thành "Trễ hẹn" khi thời gian trôi qua hạn chót.
ALTER TABLE `work_reports` ADD COLUMN `completed_at` DATETIME NULL;

-- Dữ liệu cũ: việc đã xong mà không có giải trình trễ thì coi như hoàn thành đúng hạn.
UPDATE `work_reports`
SET `completed_at` = COALESCE(`updated_at`, `due_date`)
WHERE (UPPER(`status`) = 'COMPLETED' OR `progress_percentage` = 100)
  AND `due_date` IS NOT NULL
  AND (`delay_reason` IS NULL OR TRIM(`delay_reason`) = '')
  AND COALESCE(`updated_at`, `due_date`) <= `due_date`;
