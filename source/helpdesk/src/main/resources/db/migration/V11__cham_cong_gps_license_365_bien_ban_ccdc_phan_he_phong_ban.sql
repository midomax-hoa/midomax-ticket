-- Bảng và cột mới sau khi gộp mã nguồn từ nhánh main trên GitHub (05/09/2026):
--   1. Chấm công: máy chấm công ZKTeco, ca làm việc, log/bảng công, đơn nghỉ phép, chấm công GPS
--   2. Phân hệ theo phòng ban (ma trận department_module_access + phân hệ cấp riêng app_user_modules)
--   3. License Microsoft 365 và ảnh biên bản giao nhận / thu hồi CCDC (asset_documents)
--   4. Cột mới ở app_users (phòng ban, mã nhân viên, cờ chấm công GPS, trưởng phòng)
--      và expense_funds (kỳ ngân sách: năm, tháng bắt đầu, tháng kết thúc)
--
-- Viết idempotent: DB đang chạy thật đã có sẵn các bảng này (Hibernate ddl-auto=update tạo),
-- chạy lại không lỗi; DB mới chỉ có V1..V10 thì được tạo đủ. Kiểu cột lấy đúng theo DDL
-- Hibernate sinh ra để spring.jpa.hibernate.ddl-auto=validate không báo lệch.

SET NAMES utf8mb4;

-- ===== 1. Bảng mới =====
CREATE TABLE IF NOT EXISTS `attendance_shifts` (
  `active` bit,
  `break_minutes` integer,
  `cross_midnight` bit,
  `early_grace_minutes` integer,
  `end_time` time(6),
  `is_default` bit,
  `late_grace_minutes` integer,
  `ot_start_after_minutes` integer,
  `start_time` time(6),
  `id` bigint not null auto_increment,
  `code` varchar(255),
  `name` varchar(255),
  `working_weekdays` varchar(255),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `attendance_devices` (
  `active` bit,
  `comm_key` integer,
  `latitude` float(53),
  `longitude` float(53),
  `port` integer,
  `radius_meters` integer,
  `id` bigint not null auto_increment,
  `last_sync_at` datetime(6),
  `ip_address` varchar(255),
  `last_sync_status` varchar(255),
  `location` varchar(255),
  `name` varchar(255),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `attendance_device_users` (
  `device_id` bigint,
  `id` bigint not null auto_increment,
  `updated_at` datetime(6),
  `device_name` varchar(255),
  `employee_code` varchar(255) not null,
  `full_name` varchar(255),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `employee_shifts` (
  `device_id` bigint,
  `id` bigint not null auto_increment,
  `shift_id` bigint,
  `employee_code` varchar(255),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `attendance_logs` (
  `punch_state` integer,
  `verify_mode` integer,
  `device_id` bigint,
  `id` bigint not null auto_increment,
  `punch_time` datetime(6),
  `device_name` varchar(255),
  `employee_code` varchar(255),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `attendance_records` (
  `early_minutes` integer,
  `late_minutes` integer,
  `manual_override` bit,
  `ot_minutes` integer,
  `work_date` date,
  `work_days` float(53),
  `worked_minutes` integer,
  `device_id` bigint,
  `first_in` datetime(6),
  `id` bigint not null auto_increment,
  `last_out` datetime(6),
  `shift_id` bigint,
  `department` varchar(255),
  `device_name` varchar(255),
  `employee_code` varchar(255),
  `employee_name` varchar(255),
  `note` varchar(255),
  `shift_name` varchar(255),
  `status` varchar(255),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `leave_requests` (
  `request_date` date not null,
  `to_date` date,
  `total_days` float(53),
  `created_at` datetime(6),
  `device_id` bigint,
  `head_at` datetime(6),
  `hr_at` datetime(6),
  `id` bigint not null auto_increment,
  `department` varchar(10),
  `duration` varchar(10),
  `type` varchar(12) not null,
  `leave_type` varchar(15),
  `employee_code` varchar(20) not null,
  `phone` varchar(20),
  `status` varchar(20) not null,
  `requester_name` varchar(100) not null,
  `requester_full_name` varchar(150),
  `supporter` varchar(150),
  `reject_reason` varchar(500),
  `reason` varchar(1000),
  `head_by` varchar(255),
  `hr_by` varchar(255),
  `reject_by` varchar(255),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `gps_checkins` (
  `accuracy_m` float(53),
  `distance_m` float(53),
  `free_location` bit,
  `latitude` float(53),
  `longitude` float(53),
  `device_id` bigint,
  `id` bigint not null auto_increment,
  `punch_time` datetime(6),
  `employee_code` varchar(20),
  `ip_address` varchar(60),
  `device_name` varchar(100),
  `user_email` varchar(150),
  `selfie_file` varchar(200),
  `location_name` varchar(300),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `department_module_access` (
  `id` bigint not null auto_increment,
  `department` varchar(10) not null,
  `module` varchar(20) not null,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `app_user_modules` (
  `user_id` bigint not null,
  `module` enum('ASSETS','FINANCE','HR','NETWORK','REPORTS')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_documents` (
  `doc_date` date,
  `asset_id` bigint not null,
  `id` bigint not null auto_increment,
  `size_bytes` bigint,
  `uploaded_at` datetime(6),
  `doc_type` varchar(20),
  `content_type` varchar(120),
  `uploaded_by` varchar(150),
  `person_name` varchar(200),
  `original_name` varchar(300),
  `file_path` varchar(500),
  `note` varchar(500),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `license_365` (
  `paid` bit,
  `purchased_qty` integer,
  `id` bigint not null auto_increment,
  `updated_at` datetime(6),
  `sku_id` varchar(64) not null,
  `updated_by` varchar(150),
  `display_name` varchar(200),
  `note` varchar(500),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===== 2. Ràng buộc duy nhất / khóa ngoại của bảng mới (tên do Hibernate sinh, giống DB đang chạy) =====
SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE table_schema = DATABASE() AND table_name = 'attendance_device_users' AND constraint_name = 'UK6hm9cyujyea5pm620k3wa6sgv') > 0,
  'SELECT 1',
  'ALTER TABLE `attendance_device_users` ADD CONSTRAINT `UK6hm9cyujyea5pm620k3wa6sgv` unique (employee_code)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE table_schema = DATABASE() AND table_name = 'attendance_logs' AND constraint_name = 'UKni97itwu1sepdwkwgx1wd67np') > 0,
  'SELECT 1',
  'ALTER TABLE `attendance_logs` ADD CONSTRAINT `UKni97itwu1sepdwkwgx1wd67np` unique (employee_code, punch_time, device_id)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE table_schema = DATABASE() AND table_name = 'attendance_records' AND constraint_name = 'UKacq3e6ox3fskm5e4ebn0lqql9') > 0,
  'SELECT 1',
  'ALTER TABLE `attendance_records` ADD CONSTRAINT `UKacq3e6ox3fskm5e4ebn0lqql9` unique (employee_code, work_date)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE table_schema = DATABASE() AND table_name = 'department_module_access' AND constraint_name = 'UKsr3w5fyvo7a94141vp7gdg8u0') > 0,
  'SELECT 1',
  'ALTER TABLE `department_module_access` ADD CONSTRAINT `UKsr3w5fyvo7a94141vp7gdg8u0` unique (department, module)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE table_schema = DATABASE() AND table_name = 'employee_shifts' AND constraint_name = 'UK3b423kpd48ru1493dofkqa3dt') > 0,
  'SELECT 1',
  'ALTER TABLE `employee_shifts` ADD CONSTRAINT `UK3b423kpd48ru1493dofkqa3dt` unique (employee_code, device_id)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE table_schema = DATABASE() AND table_name = 'license_365' AND constraint_name = 'UKe1rr82xi9bfalguh7mdcquffe') > 0,
  'SELECT 1',
  'ALTER TABLE `license_365` ADD CONSTRAINT `UKe1rr82xi9bfalguh7mdcquffe` unique (sku_id)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE table_schema = DATABASE() AND table_name = 'app_user_modules' AND constraint_name = 'FK5sb8xnf583mown3uluavl6npk') > 0,
  'SELECT 1',
  'ALTER TABLE `app_user_modules` ADD CONSTRAINT `FK5sb8xnf583mown3uluavl6npk` foreign key (user_id) REFERENCES `app_users` (`id`)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== 3. Chỉ mục của bảng mới =====
SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'attendance_logs' AND index_name = 'idx_att_log_time') > 0,
  'SELECT 1',
  'CREATE INDEX `idx_att_log_time` ON `attendance_logs` (punch_time)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'attendance_records' AND index_name = 'idx_att_rec_date') > 0,
  'SELECT 1',
  'CREATE INDEX `idx_att_rec_date` ON `attendance_records` (work_date)'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== 4. Cột mới trên bảng có sẵn =====
SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'department') > 0,
  'SELECT 1',
  'ALTER TABLE `app_users` ADD COLUMN `department` varchar(10) DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'employee_code') > 0,
  'SELECT 1',
  'ALTER TABLE `app_users` ADD COLUMN `employee_code` varchar(20) DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'attendance_device_id') > 0,
  'SELECT 1',
  'ALTER TABLE `app_users` ADD COLUMN `attendance_device_id` bigint DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'gps_checkin_allowed') > 0,
  'SELECT 1',
  'ALTER TABLE `app_users` ADD COLUMN `gps_checkin_allowed` bit(1) DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'gps_free_location') > 0,
  'SELECT 1',
  'ALTER TABLE `app_users` ADD COLUMN `gps_free_location` bit(1) DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'dept_head') > 0,
  'SELECT 1',
  'ALTER TABLE `app_users` ADD COLUMN `dept_head` bit(1) DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'expense_funds' AND column_name = 'budget_year') > 0,
  'SELECT 1',
  'ALTER TABLE `expense_funds` ADD COLUMN `budget_year` int DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'expense_funds' AND column_name = 'from_month') > 0,
  'SELECT 1',
  'ALTER TABLE `expense_funds` ADD COLUMN `from_month` int DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = DATABASE() AND table_name = 'expense_funds' AND column_name = 'to_month') > 0,
  'SELECT 1',
  'ALTER TABLE `expense_funds` ADD COLUMN `to_month` int DEFAULT NULL'
));
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
