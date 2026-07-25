# TÀI LIỆU DỰ ÁN MIDOMAX HELPDESK & HR (TỪ A ĐẾN Z)

> **Mục đích tài liệu:** Cung cấp cái nhìn toàn diện từ tổng quan, thiết kế kiến trúc, chi tiết các tính năng, cơ sở dữ liệu cho đến hướng dẫn cài đặt của dự án `helpdesk`. Tài liệu này dành cho Developer, Quản trị viên hệ thống và Quản lý dự án.

---

## 1. GIỚI THIỆU CHUNG (INTRODUCTION)

**Midomax Helpdesk** là một ứng dụng Web (Monolithic Application) được thiết kế đặc thù cho doanh nghiệp nhằm tự động hóa và số hóa hai quy trình cốt lõi:
1. **Hỗ trợ Kỹ thuật & Hành chính (Ticketing System):** Cổng thông tin nội bộ để nhân viên gửi các sự cố, yêu cầu (lỗi phần cứng, phần mềm, mạng) và phòng ban chuyên trách (IT) tiếp nhận, theo dõi, và giải quyết.
2. **Quản lý Hồ sơ Nhân sự (HR Management):** Hệ thống lưu trữ tập trung, chi tiết hồ sơ vòng đời của nhân sự từ thông tin cá nhân, hợp đồng, gia đình cho đến theo dõi các giấy tờ tùy thân đính kèm.

---

## 2. NGĂN XẾP CÔNG NGHỆ (TECH STACK)

Dự án được xây dựng trên hệ sinh thái Java Spring Boot hiện đại, cụ thể:

### 2.1. Backend (Server-side)
- **Ngôn ngữ:** Java 17
- **Framework chính:** Spring Boot 3.5.x
- **Bảo mật:** Spring Security 6 (In-memory Authentication hiện tại)
- **Tương tác CSDL:** Spring Data JPA, Hibernate ORM
- **Tiện ích:** Lombok (giảm boilerplate code cho Getter/Setter/Constructor)
- **Xử lý file:** Apache POI 5.2.3 (Dùng để Import/Export dữ liệu Excel)

### 2.2. Frontend (Client-side)
- **Template Engine:** Thymeleaf (Server-Side Rendering)
- **Tương tác API:** Fetch API / AJAX cho các tác vụ cập nhật nhanh không cần tải lại trang.
- **Giao diện:** HTML5, CSS3, JavaScript kết hợp CSS Framework (như Bootstrap/Tailwind tùy chỉnh).

### 2.3. Database & Cấu hình
- **Hệ quản trị CSDL:** MySQL 8+
- **Driver:** MySQL Connector/J (`mysql-connector-j`)

---

## 3. CẤU TRÚC THƯ MỤC DỰ ÁN (PROJECT STRUCTURE)

Dự án tuân theo kiến trúc MVC chuẩn của Spring Boot:
```text
helpdesk/
├── src/main/java/vn/midomax/helpdesk/
│   ├── HelpdeskApplication.java      # Class khởi chạy ứng dụng (Main)
│   ├── SecurityConfig.java           # Cấu hình Spring Security (Phân quyền)
│   ├── WebConfig.java                # Cấu hình MVC, Resource mapping
│   ├── Controllers/                  # Nhận request, trả về View/JSON
│   │   ├── HomeController.java       # Routing trang chủ, login
│   │   ├── DashboardController.java  # Thống kê, biểu đồ cho Admin
│   │   ├── TicketController.java     # Logic điều hướng Module Hỗ trợ
│   │   └── EmployeeController.java   # Logic điều hướng Module Nhân sự
│   ├── Services/                     # Chứa Business Logic cốt lõi
│   │   ├── TicketService(Impl)
│   │   ├── EmployeeService(Impl)
│   │   └── ExcelService.java         # Xử lý thư viện POI (Đọc/Ghi Excel)
│   ├── Repositories/                 # Giao tiếp DB (Interface kế thừa JpaRepository)
│   └── Entities (Models)/            # Mapping với Table trong MySQL
│       ├── Ticket.java
│       ├── Employee.java
│       ├── EmergencyContact.java     # Liên hệ khẩn cấp (1-N với Employee)
│       └── RelativeInfo.java         # Người thân (1-N với Employee)
│
└── src/main/resources/
    ├── application.properties        # Cấu hình Port, Kết nối DB, Hibernate JPA
    ├── static/                       # File tĩnh: CSS, JS, Images, Uploads
    └── templates/                    # Chứa các file giao diện HTML (Thymeleaf)
        ├── layout.html               # File khung layout chung (Header, Sidebar)
        ├── login.html                # Giao diện Đăng nhập
        ├── admin-home.html           # Trang chủ Admin
        ├── user-home.html            # Trang chủ User
        ├── ticket-management.html    # Lưới quản lý Ticket
        └── employee-management.html  # Lưới quản lý Nhân sự
```

---

## 4. CẤU HÌNH HỆ THỐNG (CONFIGURATION)

Thông tin cấu hình chạy ứng dụng được đặt tại `application.properties`:
- **Server Port:** `8080` (Ứng dụng chạy tại http://localhost:8080)
- **Database URL:** `jdbc:mysql://localhost:3306/helpdesk`
- **Tài khoản DB mặc định:** root / (mat khau MySQL cua may ban)
- **Chiến lược JPA Hibernate:** `update` (Tự động cập nhật cấu trúc bảng theo Entity mà không xóa dữ liệu cũ).

---

## 5. CHI TIẾT CÁC MODULE VÀ TÍNH NĂNG (FEATURES)

### 5.1. Module Xác Thực & Phân Quyền (Authentication)
- Sử dụng Spring Security. Mặc định mọi Endpoint đều yêu cầu đăng nhập, ngoại trừ `/login` và các resource tĩnh.
- **Tài khoản có sẵn (In-memory):**
  - **USER:** `user` / Pass: `123` (Nhân viên thường)
  - **ADMIN:** `admin` / Pass: `123` (Quản trị viên IT/Hành chính)
- **Điều hướng thông minh:** Đăng nhập thành công, hệ thống tự rẽ nhánh: Admin vào trang `/admin-home`, User vào trang `/user-home`.

### 5.2. Module Quản Lý Yêu Cầu Hỗ Trợ (Ticket Management)
- **Với Nhân viên (User):**
  - Tạo Ticket mới: Nhập tiêu đề, phân loại (Mạng, Phần cứng, Phần mềm), mô tả và **đính kèm được hình ảnh**.
  - Theo dõi Ticket của riêng mình, xem số lượng ticket đang Mở/Xử lý/Hoàn thành.
- **Với Quản trị viên (Admin):**
  - Xem được toàn bộ Ticket của hệ thống.
  - Phân công (Assign) Ticket cho từng IT cụ thể.
  - Cập nhật độ ưu tiên (LOW/MED/HIGH) và trạng thái (OPEN, PROGRESS, RESOLVED).
  - Có thể tạo Ticket thay cho User khác hoặc khách.
  - Sửa nhanh (Inline-edit) trực tiếp trên lưới dữ liệu thông qua AJAX.
- **Xử lý Upload Ảnh:** Hình ảnh đính kèm của Ticket được lưu vật lý vào `src/main/resources/static/uploads/` và `target/classes/static/uploads/` dưới dạng mã hóa UUID để tránh trùng tên.

### 5.3. Module Quản Lý Nhân Sự (Employee Management)
Module hỗ trợ quản lý hơn 50 trường thông tin chia làm 5 nhóm chính:
- **Thông tin Chung & Cá nhân:** Mã NV, Họ tên, Phòng ban, Giới tính, Quê quán, Hộ khẩu, Trình độ, CCCD, Mã số thuế.
- **Hợp đồng & Bảo hiểm:** Ngày vào, Ngày ký HĐ, Trạng thái (Đang làm/Nghỉ việc), Sổ BH, Thông tin tài khoản ngân hàng, Chế độ thai sản.
- **Gia đình:** Bố, Mẹ, Vợ/Chồng, Con cái.
- **Liên hệ khẩn cấp:** Danh sách động (1-N).
- **Hồ sơ đính kèm:** Checkbox theo dõi 12 loại giấy tờ cần nộp (Sơ yếu lý lịch, Bằng cấp, Ảnh 3x4...).

**Tính năng nâng cao của HR:**
- Tìm kiếm & Lọc phức tạp (Theo tên, bộ phận, trạng thái làm việc).
- **Import từ Excel:** Tải file danh sách `.xlsx` lên, hệ thống tự map vào DB.
- **Export ra Excel:** Xuất toàn bộ danh sách nhân sự hiện tại ra file Excel chuẩn (sử dụng thư viện `Apache POI`).

---

## 6. KIẾN TRÚC CƠ SỞ DỮ LIỆU (DATABASE SCHEMA)

Hệ thống sử dụng cơ chế Code-First (Tạo bảng từ Entity của Hibernate). Có 4 bảng chính:

1. **Bảng `tickets`**
   - Lưu trữ yêu cầu: `id`, `title`, `description`, `category`, `reporterName`, `reporterDepartment`, `priority`, `status`, `assignee`, `imagePath`, v.v.

2. **Bảng `employees`**
   - Bảng khổng lồ lưu mọi thông tin của nhân sự (hơn 50 cột). Sử dụng `employeeCode` làm mã định danh.

3. **Bảng `employee_children` (ElementCollection)**
   - Bảng phụ lưu con cái, mapping N-1 về `employees` qua khóa ngoại `employee_id`.
   
4. **Bảng `employee_emergency_contacts` (ElementCollection)**
   - Bảng phụ lưu liên hệ khẩn cấp, mapping N-1 về `employees` qua khóa ngoại `employee_id`.

---

## 7. HƯỚNG DẪN CÀI ĐẶT VÀ CHẠY DỰ ÁN (INSTALLATION GUIDE)

### Bước 1: Yêu cầu môi trường (Prerequisites)
- Cài đặt Java JDK 17.
- Cài đặt MySQL Server (Phiên bản 8.0 trở lên).
- Cài đặt Maven (hoặc dùng `mvnw` có sẵn trong thư mục dự án).
- IDE khuyên dùng: IntelliJ IDEA, Eclipse, hoặc VS Code.

### Bước 2: Thiết lập Cơ sở dữ liệu
1. Mở MySQL Workbench hoặc Terminal.
2. Chạy lệnh tạo database: 
   ```sql
   CREATE DATABASE helpdesk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
3. Đảm bảo bạn có tài khoản `root` với mật khẩu MySQL của máy bạn. Nếu mật khẩu khác, hãy vào file `src/main/resources/application.properties` để sửa lại `spring.datasource.password`.

### Bước 3: Build và Chạy dự án
Mở terminal/Command Prompt tại thư mục chứa file `pom.xml`:

- Lệnh Build để tải các thư viện (Dependencies):
  ```bash
  mvn clean install -DskipTests
  ```
- Lệnh Chạy Server Spring Boot:
  ```bash
  mvn spring-boot:run
  ```

### Bước 4: Truy cập hệ thống
- Mở trình duyệt web.
- Truy cập địa chỉ: `http://localhost:8080`
- Sử dụng tài khoản đăng nhập:
  - Admin: `admin` / `123`
  - User: `user` / `123`

---

## 8. ĐỊNH HƯỚNG PHÁT TRIỂN TIẾP THEO (FUTURE ROADMAP)
1. **Thay thế In-Memory Auth:** Tạo Entity `User` và bảng `users` để liên kết với cấu hình Spring Security, quản lý đăng nhập qua Database.
2. **Quản lý File Chuyên Nghiệp:** Kết nối với AWS S3, Google Cloud Storage hoặc MinIO để lưu trữ ảnh/file đính kèm thay vì lưu ở local thư mục `static`.
3. **Email Notification:** Tích hợp `spring-boot-starter-mail` để tự động gửi email cho người dùng khi Ticket chuyển trạng thái hoặc IT tiếp nhận.
4. **RESTful APIs:** Bổ sung các controller `@RestController` tách biệt, chuẩn bị sẵn sàng cho việc phát triển Mobile App (iOS/Android) trong tương lai.
