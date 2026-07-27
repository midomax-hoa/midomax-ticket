# Ghép chức năng mới từ `D:\Project\source` vào repo deploy

Ngày: 2026-07-27 · Nhánh: `main` · Chưa commit

## Bối cảnh

Repo deploy được chụp từ `D:\Project\source` lúc 25/07 13:37, sau đó đi riêng (Docker,
MinIO, Flyway, cấu hình bằng biến môi trường). `D:\Project\source` cũng đi tiếp tới
27/07 14:04. Đây là gộp 3 chiều, không phải copy đè.

## Nguyên tắc đã chốt với chủ dự án

1. Phần hạ tầng deploy **luôn thắng**: `storage/` (MinIO/S3), Flyway, `application.properties`
   toàn biến môi trường, `DataSeeder` đọc mật khẩu admin từ env, `EmailService` dùng `app.base-url`.
2. Chức năng nghiệp vụ mới của `source` **được bưng qua hết**.
3. Chỗ đụng độ thì ghép tay, không bỏ bên nào.

## Đã copy thẳng (source mới hơn, không đụng hạ tầng deploy)

Java: `Ticket`, `TicketRepository`, `TicketService`, `TicketServiceImpl`, `BudgetItem`,
`WorkReport`, `WorkReportController`, `WorkReportService`, `WorkReportServiceImpl`,
`ExcelService`, `DashboardController`, `HomeController`, `HelpdeskApplication`,
`AssetController`, `AssetHandoverService`.

Template: `asset-management`, `dashboard`, `expense-management`, `expense-overview`,
`login`, `schedule`, `ticket-management`, `fragments/ticket-modals`, `fragments/ticket-rig`.
CSS: `ticket-rig.css`.

## File mới

- `AssetUsageHistory.java`, `AssetUsageHistoryRepository.java` — lịch sử người sử dụng tài sản
- `TicketAutoCloseScheduler.java` — tự đóng ticket RESOLVED quá 3 ngày không phản hồi
- `HandoverDocPreviewTest.java`, `ItdBudgetImportTest.java` — test
- `static/images/logo-midomax.png`, `midomax-logo.svg`
- `db/migration/V2__ticket_xac_nhan_ngan_sach_thang_lich_su_tai_san.sql`

## Ghép tay (cả hai bên cùng sửa)

| File | Cách xử lý |
|---|---|
| `TicketController` | Lấy bản source (xác nhận hết lỗi / báo còn lỗi, `itCompletedAt`, `closedAt`, gọi `emailService`), thay 2 khối lưu file xuống ổ đĩa bằng `storageService.store(...)`; bỏ import `Files/Path/Paths/StandardCopyOption/UUID` |
| `ExpenseController` | Lấy bản source (tự tạo quỹ từ Excel, `groupSummaryMap`), thay khối lưu hóa đơn bằng `storageService.store(...)` |
| `EmailService` | Lấy bản source (thêm nhánh `CLOSED` / `REOPENED`), khôi phục `@Value("${app.base-url:...}")` thay cho `http://localhost:8080` hardcode |
| `WorkReportServiceImpl` | Lấy nguyên bản source — nó đã gộp cả checklist con lẫn việc con VÀ đã lan lên hết cả cây (dòng 233-235), tức bao trọn hành vi của commit `0ccbf05` |
| `work-reports.html` | Lấy bản source (cột SLA + giải trình trễ hạn), ghép lại 6 chỗ khoá ô của `0ccbf05`: `th:with="hasKids=..."`, khoá checkbox, khoá dropdown trạng thái, icon "tự tính" cạnh %, `th:data-haskids` trên nút sửa, `#updateAutoNote` + khoá `pRange`/`sel` trong `openUpdateModalFromBtn` |

## KHÔNG lấy từ source (bản source cũ hơn / mâu thuẫn hạ tầng)

- `WebConfig.java` — map `/uploads/**` về ổ đĩa cục bộ, đã bị `storage/UploadController` thay thế
- `InvoiceController`, `AzureUserSyncService`, `config/DataSeeder` — bản source cũ hơn, không có gì mới
- `application.properties`, `pom.xml` — bản source hardcode secret Azure + mật khẩu DB, thiếu AWS SDK/Flyway

## Migration V2 (ĐÃ VIẾT, CHƯA CHẠY)

`tickets`: `it_completed_at`, `closed_at`, `user_feedback`, `close_reason`
`budget_items`: `cost_type`, `monthly_amounts`
`work_reports`: `delay_reason`
Bảng mới: `asset_usage_histories`

`spring.jpa.hibernate.ddl-auto=validate` nên **app sẽ không khởi động được** cho tới khi
migration này chạy. Chưa chạy lên bất kỳ DB nào — chờ chỉ định.

## Kiểm chứng đã làm

- `mvnw compile` → BUILD SUCCESS, 72 file, `javac [debug parameters release 17]`
- `mvnw test-compile` → BUILD SUCCESS, 7 file test
- `mvnw test -Dtest=HandoverDocPreviewTest,ItdBudgetImportTest` → 2/2 pass
  - `ItdBudgetImportTest` thực chất **tự bỏ qua**: file `D:\Midomax\MIDOMAX PROJECT\ITD-2026-...xlsx`
    không tồn tại trên máy, test `return` sớm ⇒ phần parse Excel ITD **chưa được kiểm chứng thật**
- Không chạy `AdminHomeTest`, `HelpdeskApplicationTests`, `InvoiceLedgerTest`,
  `TicketVisibilityOidcTest` — đều là `@SpringBootTest`, cần MySQL thật

## Lưu ý môi trường

Máy hiện **không có JDK 17**; `build_with_java17.bat` trỏ tới
`C:\Users\LENOVO\AppData\Local\Programs\Java\jdk-17.0.19+10` — thư mục này không còn.
Đã build bằng JDK 21 của Android Studio (`D:\Android\jbr`), Maven vẫn dịch ở `release 17`.

## Câu hỏi còn treo

1. Chạy `V2__...sql` lên DB nào (local hay server dev)?
2. Có muốn chạy trọn bộ test `@SpringBootTest` không, và nhắm DB nào?
3. Có cần cài lại JDK 17 cho đúng bản build production không?
4. Muốn commit đợt này thành 1 commit hay tách theo từng nhóm chức năng?
