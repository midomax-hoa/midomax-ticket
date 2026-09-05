package vn.midomax.helpdesk.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.UUID;

/**
 * Lưu file lên MinIO / S3 — dùng khi chạy production.
 *
 * Bucket phải để RIÊNG TƯ (không bật anonymous download): file riêng tư (biên bản CCDC,
 * selfie chấm công) nằm cùng bucket, app tự kiểm quyền rồi mới đọc ra trả cho trình duyệt.
 */
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client client;
    private final String bucket;

    public S3StorageService(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
        log.info("Lưu file upload lên bucket S3/MinIO: {}", bucket);
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String key = StorageService.randomFilename(file);
        try (InputStream in = file.getInputStream()) {
            put(key, file.getContentType(), RequestBody.fromInputStream(in, file.getSize()));
            return URL_PREFIX + key;
        } catch (Exception e) {
            log.error("Không upload được file {} lên bucket {}", key, bucket, e);
            return null;
        }
    }

    @Override
    public String storePrivate(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String key = StorageService.privateKey(folder, StorageService.randomFilename(file));
        try (InputStream in = file.getInputStream()) {
            put(key, file.getContentType(), RequestBody.fromInputStream(in, file.getSize()));
            return key;
        } catch (Exception e) {
            log.error("Không upload được file {} lên bucket {}", key, bucket, e);
            return null;
        }
    }

    @Override
    public String storePrivate(String folder, byte[] data, String extension, String contentType) {
        if (data == null || data.length == 0) {
            return null;
        }
        String key = StorageService.privateKey(folder, UUID.randomUUID() + StorageService.safeExtension(extension));
        try {
            put(key, contentType, RequestBody.fromBytes(data));
            return key;
        } catch (Exception e) {
            log.error("Không upload được file {} lên bucket {}", key, bucket, e);
            return null;
        }
    }

    @Override
    public StoredFile load(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            ResponseInputStream<GetObjectResponse> in = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            GetObjectResponse meta = in.response();
            long size = meta.contentLength() == null ? -1L : meta.contentLength();
            return new StoredFile(in, meta.contentType(), size);
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            log.error("Không tải được file {} từ bucket {}", key, bucket, e);
            return null;
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.error("Không xóa được file {} trên bucket {}", key, bucket, e);
        }
    }

    private void put(String key, String contentType, RequestBody body) {
        PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(key);
        if (contentType != null && !contentType.isBlank()) {
            request.contentType(contentType);
        }
        client.putObject(request.build(), body);
    }
}
