# Fix: phân quyền phân hệ theo user + phân trang/tìm kiếm + chuyển gán mã chấm công sang Nhân sự

Ngày: 2026-09-05. Nhánh: `main`. Project: `source/helpdesk` (Spring Boot 3.5.14, Thymeleaf, MySQL).

## 1. Vấn đề: cấp/bỏ phân hệ REPORTS riêng từng user không có tác dụng

**Nguyên nhân gốc (đã xác nhận trên code):**
- `ModuleAccessService.modulesOf` cộng dồn 3 nguồn quyền (role, ma trận phòng ban, cấp riêng từng user) và **chỉ cộng, không trừ**.
- Bảng `department_module_access` có dòng `REPORTS` cho mọi phòng ban, nên ai có phòng ban cũng được REPORTS từ ma trận; ô tick riêng từng user không đổi được kết quả.
- Migration `V11__...sql` chỉ `CREATE TABLE`, không seed dòng nào (`grep INSERT` trong `src/main/resources/db/migration` không có kết quả) → dữ liệu này do tick trên UI "Phân hệ theo phòng ban", không phải do code tạo.
- Bẫy còn lại trong code: ma trận được cache trong RAM (`matrixCache`), chỉ nạp lại khi bấm Lưu ma trận hoặc restart. Xóa dòng trực tiếp dưới DB (như đã làm) thì app vẫn dùng bản cũ cho tới khi restart.

**Dữ liệu:** chủ repo đã chạy `DELETE FROM department_module_access WHERE module='REPORTS'` (giữ ITD → ASSETS, FINANCE; giữ nguyên `app_user_modules`).

**Sửa code:** `ModuleAccessService.java` bỏ hẳn cache RAM, đọc ma trận từ DB mỗi lần gọi.
- Bảng chỉ vài chục dòng; cùng request đó `userOf()` đã `appUserRepository.findAll()` nên cache này không tiết kiệm được gì đáng kể.
- Hệ quả: sửa dữ liệu trực tiếp dưới DB hay lưu từ UI đều có hiệu lực ngay, **không cần restart** nữa.
- Javadoc ghi rõ luật "chỉ cộng không trừ": muốn một phân hệ chỉ cấp cho vài người thì ma trận phòng ban không được tick phân hệ đó.

**Cách kiểm tra lại:** đăng nhập bằng tài khoản `ROLE_USER` có phòng ban (không dùng ADMIN/MANAGER vì luôn full quyền, không dùng ROLE_IT vì tự có REPORTS theo role), tick/bỏ tick REPORTS trong popup Phân Quyền User rồi mở `/work-reports`: có tick thì vào được, bỏ tick thì bị đưa về `/` và menu "Báo Cáo Công Việc" biến mất.

## 2. Yêu cầu 1: phân trang + tìm kiếm

**a) Chấm Công Online (`attendance-gps-permissions.html`)** — client-side, JS thuần, không đổi API:
- 10 người/trang, lọc theo `data-search` (tên + email + mã CC) trước rồi mới chia trang; đổi từ khóa thì về trang 1.
- "Chọn tất cả" chỉ áp cho các dòng đang hiện của trang hiện tại (có trạng thái indeterminate); dòng đã tick ở trang khác vẫn giữ, bulk action vẫn gom `.row-check:checked` toàn bảng.
- Hiện "Hiện x–y / N người" + pagination Bootstrap (trang đầu, cuối, ±2 quanh trang hiện tại, còn lại là "…").

**b) Quản Lý User (`user-management.html`)** — mở rộng `renderUserTable()` sẵn có:
- Thêm ô `#userSearch` (input event, lọc theo `data-search` = fullName + email + employeeCode + id), `#roleFilter` (ROLE_ADMIN / ROLE_IT / ROLE_USER, role null coi như ROLE_USER), `#sourceFilter` (LOCAL / M365 theo `isLocal()`).
- Các bộ lọc AND với `#deptFilter`; đổi bất kỳ bộ lọc nào cũng về trang 1; có dòng "Không có user nào khớp bộ lọc" khi lọc ra rỗng.
- `<tr>` gắn `data-role`, `data-source`, `data-search` bằng `th:attr`.

## 3. Yêu cầu 2: chuyển gán mã chấm công + văn phòng từ Admin sang Nhân sự

- **Admin (`user-management.html`):** bỏ `#editUserEmpCode`, `#editUserAttDevice`, phần JS đổ dữ liệu và hai field `employeeCode`/`attendanceDeviceId` trong payload `/api/admin/users/update-role`. Thay bằng một dòng ghi chú chỉ sang trang Nhân sự. Backend `update-role` dùng `containsKey()` nên thiếu key không ghi đè giá trị cũ — giữ nguyên. Bỏ luôn model attribute `attendanceDevices` và field `attendanceDeviceRepository` trong `AdminUserController` vì không còn nơi dùng; bỏ các `data-empcode/attdevice/gpsallowed/gpsfree` chết trên nút sửa.
- **Nhân sự (`/attendance/gps-permissions`, chặn bởi `ModuleAccessInterceptor` → phân hệ HR):** mỗi dòng có input mã CC (`.inp-code`, `data-uid`), select văn phòng (`.inp-device`, option từ model `devices` = `attendanceDeviceRepository.findAll()`), nút lưu `.btn-save-assign` chỉ hiện khi dòng có thay đổi; Enter trong ô mã cũng lưu. Lưu xong cập nhật `data-search` và class `row-nocode` tại chỗ, không reload.
- **Endpoint mới `POST /attendance/gps-permissions/assign`** (`@ResponseBody`, JSON `{id, employeeCode, deviceId}` → `{ok, message}`), validate: user tồn tại; deviceId (nếu có) phải tồn tại trong `attendance_devices`; mã và văn phòng phải cùng có hoặc cùng rỗng (rỗng cả hai = gỡ gán); mã tối đa 20 ký tự (độ dài cột `employee_code`).
- **Tách controller:** 3 handler `/attendance/gps-permissions`, `/bulk`, `/assign` nằm trong `AttendanceGpsPermissionController.java` (mới, ~150 dòng) thay vì `AttendanceController.java` (1334 dòng). URL không đổi nên sidebar, interceptor, SecurityConfig không cần sửa.

## 4. File thay đổi

| File | Việc |
|---|---|
| `ModuleAccessService.java` | bỏ cache RAM, javadoc luật cộng dồn |
| `AttendanceGpsPermissionController.java` (mới) | GET trang + bulk (chuyển từ AttendanceController) + assign (mới) |
| `AttendanceController.java` | bỏ 2 handler đã chuyển |
| `AdminUserController.java` | bỏ `attendanceDevices` / `attendanceDeviceRepository` không còn dùng |
| `attendance-gps-permissions.html` | tìm kiếm + phân trang + gán mã/văn phòng inline |
| `user-management.html` | search + lọc vai trò/nguồn; bỏ gán mã/văn phòng khỏi modal |
| `ModuleAccessServiceUnitTest.java` (mới) | unit test Mockito, không cần DB — có ca hồi quy cho vụ cache |
| `AttendanceGpsPermissionControllerTest.java` (mới) | unit test validate endpoint assign, không cần DB |
| `ModuleAccessServiceTest.java`, `AttendanceGpsPermissionsTest.java`, `UserManagementPageTest.java` (mới) | `@SpringBootTest` + MockMvc, cần DB |

## 5. Kiểm chứng

Đã chạy (JDK 26 trên PATH, `mvnw` offline):
- `mvnw test-compile`: biên dịch OK (Lombok chỉ cảnh báo `sun.misc.Unsafe`).
- `mvnw test -Dtest=ModuleAccessServiceUnitTest,AttendanceGpsPermissionControllerTest`: **8/8 pass**.
- Chứng minh hồi quy: `git stash` file `ModuleAccessService.java` về bản cũ (còn cache) rồi chạy lại `ModuleAccessServiceUnitTest` → ca `matrixRowsRemovedBetweenCallsStopGrantingWithoutRestart` **rớt** đúng như mong đợi; pop stash → pass.
- Render hai template bằng `SpringTemplateEngine` (SpEL, cùng evaluator với lúc chạy thật) với dữ liệu giả, sidebar thay bằng fragment stub: render OK, có đủ `id="qFilter"`, `id="pager"`, option văn phòng `selected`, `data-role/data-source/data-search`, không còn `editUserEmpCode`/`editUserAttDevice`.
- Kiểm tra cú pháp JS inline của hai trang đã render bằng `node vm.Script`: OK.

**Chưa chạy được:** các test `@SpringBootTest` (3 file mới + toàn bộ test cũ) vì context Spring cần MySQL. Máy local có MySQL ở `127.0.0.1:3306` nhưng báo `Access denied for user 'root'@'localhost'` với thông tin mặc định trong `application.properties`; repo không có `.env`; không có Docker để dựng DB tạm. Không tự đoán mật khẩu hay tự trỏ sang DB khác. Cần chạy lại trên máy có `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD` trỏ vào DB test:

```
cd source/helpdesk && ./mvnw test
```

## 6. Lưu ý triển khai

- Sau khi deploy bản này, không cần restart hay bấm Lưu ma trận để dữ liệu `department_module_access` có hiệu lực.
- Trang `/attendance/gps-permissions` chỉ người có phân hệ HR (hoặc ADMIN/MANAGER) vào được, nên endpoint `assign` cũng chỉ những người đó gọi được.
- Comment trong `ModuleAccessService.java` và hai template vẫn viết tiếng Việt cho khớp file hiện có; controller và test mới viết comment tiếng Anh.
- Hook thiết kế báo `dark-glow` ở dòng 24 `user-management.html` (CSS có sẵn, không thuộc thay đổi này) — để nguyên.

## Câu hỏi treo

1. Có muốn chặn gán trùng cặp (mã chấm công, văn phòng) cho hai user khác nhau không? Hiện endpoint `assign` không kiểm tra (giống hành vi cũ của modal Admin), vì có thể tồn tại hai tài khoản LOCAL/M365 của cùng một người.
2. Cần thông tin DB test để chạy trọn bộ `@SpringBootTest` trước khi tin cậy hoàn toàn phần render thật (sidebar, interceptor, JPA flush).
