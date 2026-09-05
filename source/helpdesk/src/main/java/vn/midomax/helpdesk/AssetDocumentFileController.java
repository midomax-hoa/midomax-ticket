package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.midomax.helpdesk.storage.StoredFile;
import vn.midomax.helpdesk.storage.StoredFileResponses;

import java.time.Duration;

/**
 * Trả file biên bản CCDC tại /asset-docs/{tên}.
 *
 * URL giữ nguyên như khi còn phục vụ từ ổ đĩa, nay đọc từ kho file (MinIO / ổ đĩa) qua
 * StorageService. Cần đăng nhập (anyRequest().authenticated() trong SecurityConfig):
 * ai xem được trang tài sản thì xem được biên bản, giống hành vi cũ.
 */
@RestController
public class AssetDocumentFileController {

    @Autowired
    private AssetDocumentService documentService;

    @GetMapping(AssetDocumentService.URL_PREFIX + "{name}")
    public ResponseEntity<Resource> serve(@PathVariable("name") String name) {
        StoredFile file = documentService.open(name);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        // Giấy tờ nội bộ: trình duyệt được cache riêng một lúc, proxy trung gian không được giữ lại
        return StoredFileResponses.of(file, CacheControl.maxAge(Duration.ofHours(1)).cachePrivate());
    }
}
