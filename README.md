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

## Nơi lưu file đính kèm

| `STORAGE_TYPE` | Lưu ở đâu | Dùng khi |
|---|---|---|
| `local` (mặc định) | Thư mục trên ổ đĩa | Dev trên máy cá nhân |
| `s3` | MinIO / S3 | Chạy production |

Đường dẫn lưu trong database luôn là `/uploads/{tên-file}` cho cả hai chế độ, nên
đổi qua lại không cần sửa dữ liệu cũ.
