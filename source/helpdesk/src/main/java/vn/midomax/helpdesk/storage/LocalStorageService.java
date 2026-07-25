package vn.midomax.helpdesk.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** Lưu file xuống ổ đĩa — dùng khi chạy dev trên máy cá nhân. */
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        log.info("Lưu file upload tại thư mục: {}", root);
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String filename = StorageService.randomFilename(file);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(in, root.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return URL_PREFIX + filename;
        } catch (IOException e) {
            log.error("Không lưu được file {} vào {}", filename, root, e);
            return null;
        }
    }

    @Override
    public StoredFile load(String filename) {
        Path path = root.resolve(filename).normalize();
        // Chặn kiểu tên file "../../etc/passwd" nhảy ra ngoài thư mục gốc.
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return new StoredFile(Files.newInputStream(path), Files.probeContentType(path), Files.size(path));
        } catch (IOException e) {
            log.error("Không đọc được file {}", path, e);
            return null;
        }
    }
}
