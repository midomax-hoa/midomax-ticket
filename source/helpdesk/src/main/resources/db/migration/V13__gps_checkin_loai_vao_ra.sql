-- Tách nút chấm công VÀO / RA trên trang chấm GPS: lưu loại từng lần chấm
-- để lịch sử hiện rõ "vào lúc mấy giờ, ra lúc mấy giờ". NULL = bản ghi cũ.
ALTER TABLE gps_checkins ADD COLUMN punch_type VARCHAR(8) NULL;
