# Báo cáo cứu nhánh main: gộp code trên GitHub vào base code local (05/09/2026)

## Tình huống

- `origin/main` trên GitHub bị đẩy đè bằng một lịch sử **mới toanh**: commit gốc `e8cf285`
  ("Khởi tạo mã nguồn Helpdesk Midomax", 29/08) không có cha, là snapshot của thư mục dev đang
  chạy thật. Sau đó `d3fa1a8` (chấm công GPS, License 365, biên bản CCDC), rồi `c14ea73` merge
  lịch sử cũ bằng chiến lược **ours** (cây file y nguyên bản của họ, lịch sử cũ chỉ còn cái tên),
  rồi `db67d3c`, `d0bb62a` tách mật khẩu ra file ngoài git.
- Layout khác nhau: local để code ở `source/helpdesk/`, remote đem ra `helpdesk/` ở gốc, kèm
  rác (`recovered_june28/`, `restored_20h/`, `recovery_tools/`, `FixEncoding.exe`, script PS1...).
- Merge thẳng sẽ bị git coi là "họ cố tình xoá" toàn bộ base code local (gốc chung `13775cb`).

## Cách giải quyết

1. Tag backup: `backup/main-before-rescue-20260905` (main local cũ), `backup/origin-main-20260905` (d0bb62a).
2. So cây file để tìm gốc thật của snapshot: gần nhất là `26e8a8e` (27/07) nhưng vẫn lệch ~9.100
   dòng (code họ làm ngoài git từ 27/07 đến 29/08).
3. Dựng commit tổng hợp `1a06834`: lấy `d0bb62a:helpdesk/src` đặt vào `source/helpdesk/src`, cha là
   `26e8a8e`. Giữ nguyên từ gốc: `pom.xml`, `application.properties`, `db/migration/*`, gói
   `storage/*` (remote đã xoá Flyway, MinIO S3, cấu hình theo biến môi trường). Bỏ
   `src/test/.../QueryDb.java` vì chứa mật khẩu DB.
4. `git merge` commit đó vào main: 88 file tự gộp, 17 file conflict (81 chỗ) gỡ tay theo nguyên tắc
   **base local là chuẩn, tính năng mới của remote cộng thêm**.
5. Rà toàn bộ 423 dòng bị xoá so với main cũ để bắt các chỗ git "lùi" về bản cũ do snapshot thiếu
   commit 25/07 (đã phục hồi, xem bên dưới).
6. Commit merge `cca1138`, rồi `f78fa36` = `merge -s ours origin/main` chỉ để ghi nhận lịch sử remote
   là tổ tiên (cây file không đổi) → push lên GitHub là fast-forward, **không cần force**.

## Kết quả

- `main` local = `f78fa36`, ahead 4 so với `origin/main`, `origin/main` là tổ tiên → `git push origin main`.
- Compile xanh với JDK 17 (`mvnw -o -DskipTests compile`). Máy chỉ có JDK 26, Lombok không chạy trên
  JDK 26 nên dùng JDK 17 portable tải về thư mục tạm (không cài vào hệ thống).
- Test: 2 unit test pass; các test `@SpringBootTest` fail vì `Access denied for user 'root'@'localhost'`
  (MySQL local từ chối, lỗi môi trường có sẵn, không đụng tới DB). Không test nào bị merge làm hỏng.
- Migration mới `V11__cham_cong_gps_license_365_bien_ban_ccdc_phan_he_phong_ban.sql`: 12 bảng, 7 ràng
  buộc, 2 chỉ mục, 9 cột thêm (`app_users`, `expense_funds`). Sinh từ chênh lệch DDL Hibernate
  trước/sau merge (chạy trên H2 in-memory, không đụng DB thật). Idempotent: DB đang chạy thật đã có
  bảng do `ddl-auto=update` thì bỏ qua, DB chỉ có V1..V10 thì tạo đủ.

## Lấy từ remote

- Module chấm công: máy ZKTeco, ca làm, đơn nghỉ phép, chấm công GPS + selfie, tự đồng bộ theo cron.
- Phân hệ theo phòng ban: `AppModule`, `Department`, ma trận `DepartmentModuleAccess`,
  `ModuleAccessService/Interceptor/Advice`, UI trong Quản lý User. `SecurityConfig` chuyển các
  phân hệ giới hạn sang `authenticated()` + interceptor quyết định (giữ quyền role cũ, cộng ma trận).
- License 365, ảnh biên bản CCDC (`AssetDocument`, lưu ổ đĩa `data/asset-docs`), trưởng phòng xem
  ticket cả phòng, chuông thông báo báo cáo công việc, `assertCanAccessReport`, xoá chỉ nhận POST,
  API thông báo bỏ tham số `?user=`, API lịch làm việc chỉ trong phạm vi.
- Kỳ ngân sách của quỹ, thực chi từng tháng của hạng mục, popup kế hoạch tháng, trang
  `expense-reconcile.html`, bootstrap local, logo trim + cache-bust, câu chữ mới trên form ticket.

## Giữ của local (remote khác nhưng bị loại)

- Flyway, MinIO S3 `StorageService` (đã phục hồi ở `TicketController`, `InvoiceController`,
  `ExpenseController`: bản remote ghi thẳng file vào `src/main/resources/static/uploads`).
- Cấu hình theo biến môi trường: `application.properties`, mật khẩu admin + user demo trong
  `DataSeeder` (remote fix cứng `admin123`), tenant-id trong `AzureUserSyncService` (remote fix cứng).
- Phân trang phía server ở danh sách tài sản và sổ hoá đơn (remote làm phân trang JS phía trình duyệt).
- `work-reports.html` + `ticket-modals.html` lấy nguyên bản local (SLA, chốt hoàn thành, việc cha tự
  tính theo việc con); phần "xem thêm / phân trang JS" của remote không lấy; câu chữ mới đã chép qua.
- Mô hình đối chiếu hoá đơn: `InvoiceEntry` giữ cả `fundId` lẫn `budgetItemId`; tổng quỹ trừ **mọi**
  hoá đơn PAID của quỹ (luật local), "đã chi" từng hạng mục dùng luồng theo `budgetItemId` của remote
  nhưng chỉ tính hoá đơn PAID và tính **một lần** (trước đó bị cộng trùng).
- `ExcelService` lấy bản local (mẫu Excel ngân sách 3 sheet), thêm `normalizeText` của remote.
- Sản phẩm bàn giao cùng đợt trong `AssetController` (bản local, có mã kiểm kê riêng) + gắn thêm
  luồng upload biên bản khi chuyển "Đang sửa"/"Hỏng" của remote.

## Sửa thêm sau merge

- `application.properties`: `app.asset-doc-dir`, `app.gps-selfie-dir`, `attendance.auto-sync.*`
  (đều override được bằng env), `max-request-size` mặc định 80MB (upload nhiều ảnh biên bản).
  `.env.example` bổ sung biến tương ứng. `.gitignore` thêm `data/`.
- `WebConfig`: `/uploads/**` dùng `toUri()` thay vì `"file:/" + path` (chuỗi cũ hỏng trên Linux).
- `TicketController.assertEditableStatus`: cho lưu lại ticket đang RESOLVED (IT bổ sung ghi chú
  khắc phục / ảnh hoàn thành như bản local từng cho phép).
- `WorkReportController.actorOf` dùng `currentHandle` (tài khoản M365 trả về tên hiển thị).
- `success-modal.html` khôi phục (5 trang còn include); `ticket-modal-fragment.html` để remote xoá
  (không nơi nào tham chiếu).

## Việc còn lại / cần quyết định

1. **Đổi ngay client-secret Microsoft Entra** (`xGW8Q~...`) và mật khẩu MySQL: cả hai đã bị commit
   lên GitHub ở `e8cf285` (và vẫn còn trong `restored_20h/.../application.properties` tại `d0bb62a`).
   Lịch sử đó đã public trong repo, không rút lại được bằng cách xoá file.
2. Push: `git push origin main` (fast-forward). Nếu muốn bỏ hẳn lịch sử rác của remote khỏi GitHub thì
   `git reset --hard cca1138` rồi push `--force-with-lease`; người kia phải clone lại. Hai lựa chọn này
   chờ chủ repo quyết.
3. Chạy V11 thử trên **bản sao** DB đang chạy thật trước khi deploy; deploy với `JPA_DDL_AUTO=validate`
   sẽ báo ngay nếu còn cột lệch. DB thật của remote chưa có `flyway_schema_history` thì
   `baseline-on-migrate=true` sẽ đánh dấu V1, V2..V10 viết idempotent nên chạy được, riêng V3..V10
   nên xem lại nếu DB đó đã có sẵn cột.
4. Quyền vào **Báo cáo công việc** giờ theo ma trận phân hệ: user thường (không phải IT/Admin/Manager)
   chỉ vào được nếu phòng ban được tick `REPORTS` trong Quản lý User. Muốn giữ như trước (ai cũng vào,
   chỉ thấy báo cáo của mình) thì tick REPORTS cho các phòng ban.
5. Ảnh biên bản CCDC và selfie GPS đang lưu ổ đĩa (`data/`), chưa qua MinIO; cần bàn nếu chạy nhiều
   instance / container.
6. Máy dev cần cài JDK 17 cố định (đường dẫn trong `build_with_java17.bat` đã không còn).
7. `application-local.properties` local (25/07) chưa được sửa; mật khẩu DB `Root@123` và secret Azure
   do người kia gửi có thể điền vào đó hoặc `.env` nếu muốn chạy bản của họ, tuyệt đối không commit.
8. Nên có một vòng review code cho các file gỡ conflict tay (`WorkReportController`, `AssetController`,
   `ExpenseController`, `SecurityConfig`, `sidebar.html`, `asset-management.html`).
