package vn.midomax.helpdesk.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/** Lưu file lên MinIO / S3 — dùng khi chạy production. */
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
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
            return URL_PREFIX + key;
        } catch (Exception e) {
            log.error("Không upload được file {} lên bucket {}", key, bucket, e);
            return null;
        }
    }

    @Override
    public StoredFile load(String filename) {
        try {
            ResponseInputStream<GetObjectResponse> in = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(filename).build());
            GetObjectResponse meta = in.response();
            long size = meta.contentLength() == null ? -1L : meta.contentLength();
            return new StoredFile(in, meta.contentType(), size);
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            log.error("Không tải được file {} từ bucket {}", filename, bucket, e);
            return null;
        }
    }
}
