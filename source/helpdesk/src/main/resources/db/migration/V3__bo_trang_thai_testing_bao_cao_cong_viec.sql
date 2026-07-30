-- Bỏ trạng thái TESTING khỏi báo cáo công việc.
-- Các bản ghi cũ đang ở TESTING được chuyển về PROGRESS (đang triển khai)
-- vì việc đang kiểm thử nghĩa là chưa hoàn thành.
UPDATE `work_reports` SET `status` = 'PROGRESS' WHERE UPPER(`status`) = 'TESTING';
