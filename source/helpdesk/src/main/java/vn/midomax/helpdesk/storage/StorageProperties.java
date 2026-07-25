package vn.midomax.helpdesk.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình nơi lưu file upload.
 *
 * app.storage.type=local -> lưu xuống ổ đĩa (dùng khi dev trên máy cá nhân)
 * app.storage.type=s3    -> lưu lên MinIO / S3 (dùng khi chạy production)
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** "local" hoặc "s3". Mặc định local để dev không cần cài thêm gì. */
    private String type = "local";

    private final Local local = new Local();

    private final S3 s3 = new S3();

    @Getter
    @Setter
    public static class Local {
        /** Thư mục chứa file upload khi chạy local. */
        private String dir = "src/main/resources/static/uploads";
    }

    @Getter
    @Setter
    public static class S3 {
        /** Địa chỉ MinIO, ví dụ http://minio:9000. Để trống nếu dùng AWS S3 thật. */
        private String endpoint;

        /** MinIO không quan tâm region nhưng SDK bắt buộc phải có. */
        private String region = "us-east-1";

        private String bucket;

        private String accessKey;

        private String secretKey;

        /** MinIO cần path-style (http://host/bucket/key) thay vì virtual-host style. */
        private boolean pathStyleAccess = true;
    }
}
