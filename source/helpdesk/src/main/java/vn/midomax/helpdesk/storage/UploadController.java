package vn.midomax.helpdesk.storage;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Phục vụ file đính kèm tại /uploads/{tên-file}.
 *
 * Trước đây file được map thẳng từ thư mục static, nay đi qua StorageService
 * để dùng chung một đường dẫn cho cả ổ đĩa (dev) lẫn MinIO (production).
 */
@RestController
public class UploadController {

    private static final Pattern SAFE_FILENAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,199}");

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/uploads/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        if (!SAFE_FILENAME.matcher(filename).matches() || filename.contains("..")) {
            return ResponseEntity.notFound().build();
        }

        StoredFile file = storageService.load(filename);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)));
        if (file.size() >= 0) {
            response.contentLength(file.size());
        }
        return response.body(new InputStreamResource(file.content()));
    }

    private MediaType mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
