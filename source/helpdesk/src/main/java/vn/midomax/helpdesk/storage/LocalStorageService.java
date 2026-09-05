package vn.midomax.helpdesk.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Lưu file xuống ổ đĩa — dùng khi chạy dev trên máy cá nhân.
 *
 * File công khai nằm trong thư mục static/uploads (trình duyệt lấy qua /uploads/...).
 * File riêng tư nằm ở thư mục KHÁC, ngoài static/, vì mọi thứ trong static/ đều bị
 * phục vụ công khai cho bất kỳ ai biết đường dẫn.
 */
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path publicRoot;
    private final Path privateRoot;

    public LocalStorageService(String publicDir, String privateDir) {
        this.publicRoot = Paths.get(publicDir).toAbsolutePath().normalize();
        this.privateRoot = Paths.get(privateDir).toAbsolutePath().normalize();
        log.info("Lưu file upload tại thư mục: {} (công khai), {} (riêng tư)", publicRoot, privateRoot);
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String filename = StorageService.randomFilename(file);
        try (InputStream in = file.getInputStream()) {
            write(publicRoot.resolve(filename), in);
            return URL_PREFIX + filename;
        } catch (IOException e) {
            log.error("Không lưu được file {} vào {}", filename, publicRoot, e);
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
            write(privateRoot.resolve(key), in);
            return key;
        } catch (IOException e) {
            log.error("Không lưu được file {} vào {}", key, privateRoot, e);
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
            Path target = privateRoot.resolve(key).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            return key;
        } catch (IOException e) {
            log.error("Không lưu được file {} vào {}", key, privateRoot, e);
            return null;
        }
    }

    @Override
    public StoredFile load(String key) {
        Path path = resolve(key);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return new StoredFile(Files.newInputStream(path), Files.probeContentType(path), Files.size(path));
        } catch (IOException e) {
            log.error("Không đọc được file {}", path, e);
            return null;
        }
    }

    @Override
    public void delete(String key) {
        Path path = resolve(key);
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Không xóa được file {}", path, e);
        }
    }

    /** Khóa có "/" là file riêng tư. Chặn kiểu "../../etc/passwd" nhảy ra ngoài thư mục gốc. */
    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Path root = key.contains("/") ? privateRoot : publicRoot;
        Path path = root.resolve(key).normalize();
        return path.startsWith(root) ? path : null;
    }

    private static void write(Path target, InputStream in) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
