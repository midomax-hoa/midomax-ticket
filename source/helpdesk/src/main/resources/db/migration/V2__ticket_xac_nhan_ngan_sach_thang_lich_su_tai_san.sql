-- Cac cot / bang moi sinh ra tu dot ghep tinh nang ngay 27/07/2026:
--   1. Ticket: luong nguoi dung xac nhan het loi / bao con loi + tu dong dong sau 3 ngay
--   2. BudgetItem: loai chi phi + ke hoach phan bo ngan sach 12 thang
--   3. WorkReport: giai trinh tre han
--   4. Bang moi asset_usage_histories: lich su nguoi su dung tai san

SET NAMES utf8mb4;

-- 1. Ticket: xac nhan ket qua xu ly
ALTER TABLE `tickets`
  ADD COLUMN `it_completed_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `closed_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `user_feedback` text COLLATE utf8mb4_unicode_ci,
  ADD COLUMN `close_reason` text COLLATE utf8mb4_unicode_ci;

-- 2. BudgetItem: loai chi phi (OPEX/CAPEX/...) va ke hoach 12 thang dang CSV "0,0,131750000,..."
ALTER TABLE `budget_items`
  ADD COLUMN `cost_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `monthly_amounts` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL;

-- 3. WorkReport: giai trinh khi hoan thanh tre han
ALTER TABLE `work_reports`
  ADD COLUMN `delay_reason` text COLLATE utf8mb4_unicode_ci;

-- 4. Lich su nguoi su dung / ban giao tai san
CREATE TABLE `asset_usage_histories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint NOT NULL,
  `inventory_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_to_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_to_position` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_to_department` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_to_location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_date` datetime(6) DEFAULT NULL,
  `returned_date` datetime(6) DEFAULT NULL,
  `note` text COLLATE utf8mb4_unicode_ci,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_asset_usage_histories_asset_id` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
