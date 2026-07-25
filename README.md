# midomax-ticket

Hệ thống Helpdesk nội bộ Midomax — Spring Boot 3.5 + Thymeleaf + MySQL, đăng nhập bằng Microsoft Entra ID.

## Cấu trúc

```
source/helpdesk/        Source Spring Boot + Dockerfile
database/               SQL dump MySQL (không đẩy lên git)
jar_deploy/             JAR build sẵn + script chạy trực tiếp trên Linux (không đẩy lên git)
docker-compose.yml      Dùng cho Dokploy
.env.example            Danh sách biến môi trường cần khai
```

## Chạy dev trên máy cá nhân

1. Cài JDK 17 và MySQL 8, tạo database `helpdesk`, import file trong `database/`.
2. Tạo `source/helpdesk/src/main/resources/application-local.properties` theo mẫu
   `application-local.properties.example` (file này đã gitignore, chứa mật khẩu và secret Azure).
3. Chạy:

```bash
cd source/helpdesk
./mvnw spring-boot:run
```

Mặc định profile là `local`, file đính kèm lưu xuống thư mục
`src/main/resources/static/uploads`.

## Deploy lên Dokploy

Dokploy chạy bằng `docker-compose.yml` ở thư mục gốc. MySQL và MinIO là service
sẵn có bên ngoài, khai địa chỉ qua biến môi trường.

1. Tạo bucket trên MinIO (ví dụ `helpdesk-uploads`) và một cặp access key / secret key.
2. Trong Dokploy tạo service kiểu **Docker Compose**, trỏ vào repo này.
3. Dán nội dung `.env.example` (đã điền giá trị thật) vào tab **Environment**.
4. Tab **Domains**: khai tên miền, port `8080`, bật HTTPS — Dokploy tự cấu hình Traefik.
5. Vào Azure App Registration thêm redirect URI `https://<tên-miền>/login/oauth2/code/microsoft`.

Healthcheck: `GET /actuator/health`.

### Lần deploy đầu tiên

Không cần làm gì thêm. Flyway tự tạo toàn bộ bảng từ `V1__khoi_tao_schema.sql` khi
database còn trống. Nếu database đã có bảng sẵn (import từ dump cũ), Flyway đánh dấu
V1 là đã chạy và bỏ qua, nhờ `baseline-on-migrate=true`.

### Tài khoản đăng nhập

`DataSeeder` chạy tự động mỗi lần ứng dụng khởi động, không cần chạy script riêng.
Nó tạo tài khoản `admin` (nếu chưa có) với mật khẩu lấy từ `SEED_ADMIN_PASSWORD`,
cùng 4 danh mục công cụ dụng cụ mặc định.

Tài khoản thử nghiệm `user` / `user123` chỉ sinh ra khi `SEED_DEMO_USER=true`
(mặc định khi dev). Trên production `docker-compose.yml` đã đặt `false`.

Nhân viên đăng nhập bằng Microsoft 365, tài khoản `admin` chỉ dùng để quản trị.

## Đổi cấu trúc database

Mọi thay đổi schema (thêm bảng, thêm cột, đổi tên, xoá cột) đều phải viết thành một
file SQL mới trong `source/helpdesk/src/main/resources/db/migration/`:

```
V1__khoi_tao_schema.sql        đã có, tạo 16 bảng ban đầu
V2__them_bang_thiet_bi.sql     file tiếp theo anh viết
V3__them_cot_ghi_chu.sql
```

Quy tắc đặt tên: `V{số}__{mô_tả}.sql` — hai dấu gạch dưới, số tăng dần, không trùng.

Lúc ứng dụng khởi động, Flyway đọc bảng `flyway_schema_history` để biết database đang
ở version nào, thiếu file nào thì chạy file đó. File đã chạy rồi **không được sửa nữa**
(Flyway đối chiếu checksum, sửa là báo lỗi) — cần đổi gì thì viết file mới.

Nhờ vậy lịch sử thay đổi database nằm trong git, và dev / production luôn chạy đúng
một bộ lệnh giống nhau.

**Không đặt `JPA_DDL_AUTO=update`.** Hibernate chỉ thêm được bảng và cột mới, còn xoá
cột thì bỏ mặc, đổi tên cột thì tạo cột mới và để dữ liệu cũ kẹt lại trong cột cũ.

## Nơi lưu file đính kèm

| `STORAGE_TYPE` | Lưu ở đâu | Dùng khi |
|---|---|---|
| `local` (mặc định) | Thư mục trên ổ đĩa | Dev trên máy cá nhân |
| `s3` | MinIO / S3 | Chạy production |

Đường dẫn lưu trong database luôn là `/uploads/{tên-file}` cho cả hai chế độ, nên
đổi qua lại không cần sửa dữ liệu cũ.
