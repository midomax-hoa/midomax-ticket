-- Một số công cụ dụng cụ (chuột, cáp, phụ kiện lẻ) không dán mã kiểm kê.
-- Cho phép để trống; MySQL vẫn giữ ràng buộc duy nhất vì nhiều NULL không bị coi là trùng.
ALTER TABLE `assets` MODIFY COLUMN `inventory_code` VARCHAR(255) NULL;

-- Dữ liệu cũ lỡ lưu chuỗi rỗng thì đưa về NULL để không đụng ràng buộc duy nhất.
UPDATE `assets` SET `inventory_code` = NULL WHERE TRIM(`inventory_code`) = '';
