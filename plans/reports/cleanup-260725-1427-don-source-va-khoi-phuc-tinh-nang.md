# Dọn source + khôi phục tính năng thất lạc — 25/07/2026

## Kết quả

Dung lượng project: **275MB → 102MB** (giảm 173MB). Code nguồn thật chỉ 2.2MB.

| Commit | Nội dung |
|---|---|
| `3071369` | Chuyển lưu file sang MinIO S3, thêm cấu hình deploy Dokploy |
| `beab277` | Dọn backup, script chữa cháy, JAR build sẵn |
| `0ccbf05` | Khôi phục tính năng tự tính tiến độ việc cha theo việc con |

## Đã xoá

- `jar_deploy/helpdesk.jar` (158MB — riêng `microsoft-graph-6.20.0.jar` bên trong đã 55.7MB)
- `source/_tmp_extract/` (11MB, bản sao) — đã trích 2 file có giá trị trước khi xoá
- 4 file zip backup, 3 file sql backup, `backup_temp/`, `backup_codebase_extracted/`,
  `recovered_june28/`, `restored_20h/`, `recovery_tools/`
- 14 script `Fix*.java` / `*Sidebar*.java`, 10 script `.py` / `.ps1` chữa cháy
- `source/target/`, `.apt_generated*`, file rỗng

## Tính năng khôi phục

Bản 24/07 nằm trong `_tmp_extract` mới hơn bản đang dùng (14/07) và có tính năng
tự tính tiến độ việc cha:

- `WorkReportServiceImpl.recalcFromChildren()` — tiến độ cha = trung bình % các con;
  con xong hết → cha COMPLETED; có con đang chạy → PROGRESS
- `WorkReportServiceImpl.syncHierarchy()` — lan cập nhật lên cha, ông
- `work-reports.html` — khoá ô % và ô trạng thái khi việc có việc con, hiện icon ổ khoá

Kiểm chứng trước khi chép: `WorkReportController` và `WorkReportRepository` giống hệt
nhau ở 2 bản, `childReportsMap` (Controller:100-123) và `findByParentIdOrderByCreatedAtAsc`
(Repository:14) đã có sẵn → chép 2 file là đủ, không thiếu phụ thuộc.

Xác nhận `jar_deploy/helpdesk.jar` (build 25/07) không chứa `recalcFromChildren`
→ tính năng này chưa từng chạy trên hệ thống.

## Giữ nguyên có chủ ý

**`sidebar.html`** — bản 24/07 chỉ khác bảng màu, không khác chức năng. Giữ màu
teal hiện tại vì người dùng đang quen. Mã màu bản xanh dương nếu sau này muốn đổi
(dòng 720-726):

```
#1d4ed8  (thay #0e7490)
#2563eb  (thay #059669)
#60a5fa  (thay #34d399)
#1e3a8a  (thay #134e4a)
#3b82f6  (thay #0e7490)
```

**Ảnh upload cũ** — `source/src/main/resources/static/uploads/` có 3 file, nhưng DB
chỉ trỏ tới `d29f4553-d7cf-4d34-831f-32919d2d7b4d.jpg`. Cần đẩy file này lên bucket
MinIO trước khi chạy production, 2 file còn lại mồ côi.

## Chưa kiểm chứng

- **Chưa biên dịch** — máy chưa có JDK/Maven/Docker. Toàn bộ thay đổi (storage MinIO,
  tính năng khôi phục) sẽ được Dokploy build lần đầu trên server.
- Tên network `dokploy-network` trong `docker-compose.yml` chưa đối chiếu với VPS thật.

## Câu hỏi còn treo

1. `AZURE_CLIENT_SECRET` cũ đã lộ trong gói deploy — đã tạo secret mới trên Azure chưa?
2. Host / user / password của MySQL và MinIO ngoài — cần điền vào Environment của Dokploy.
3. Thư viện `microsoft-graph` chiếm 55MB trong JAR. `AzureUserSyncService` đã gọi Graph
   bằng `RestTemplate` thuần — nếu chuyển `GraphEmailService` sang cách đó thì bỏ được
   thư viện này. Chưa làm, chờ quyết định.
