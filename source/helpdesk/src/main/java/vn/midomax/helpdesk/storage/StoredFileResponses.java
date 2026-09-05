package vn.midomax.helpdesk.storage;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Đóng gói {@link StoredFile} thành phản hồi HTTP — dùng chung cho mọi chỗ trả file về trình duyệt. */
public final class StoredFileResponses {

    private StoredFileResponses() {
    }

    public static ResponseEntity<Resource> of(StoredFile file, CacheControl cacheControl) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType(file.contentType()))
                .cacheControl(cacheControl);
        if (file.size() >= 0) {
            response.contentLength(file.size());
        }
        return response.body(new InputStreamResource(file.content()));
    }

    private static MediaType mediaType(String contentType) {
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
