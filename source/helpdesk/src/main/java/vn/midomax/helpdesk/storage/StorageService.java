package vn.midomax.helpdesk.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Nơi lưu file của hệ thống (ảnh ticket, chứng từ hóa đơn, biên bản CCDC, selfie chấm công...).
 * Dev lưu xuống ổ đĩa, production lưu lên MinIO / S3 — code nghiệp vụ không cần biết.
 *
 * Có hai loại file:
 * <ul>
 *   <li>File CÔNG KHAI: khóa là tên file phẳng ("abc.jpg"), phục vụ cho mọi người tại
 *       /uploads/{tên}. {@link #store(MultipartFile)} trả về luôn đường dẫn web đó để lưu DB.</li>
 *   <li>File RIÊNG TƯ: khóa nằm trong thư mục con ("asset-docs/abc.jpg", "gps-selfies/1_NV01/x.jpg").
 *       KHÔNG phục vụ qua /uploads; controller nghiệp vụ tự kiểm quyền rồi trả về bằng
 *       {@link #load(String)}. Quy ước: khóa có dấu "/" là file riêng tư.</li>
 * </ul>
 */
public interface StorageService {

    String URL_PREFIX = "/uploads/";

    /**
     * Lưu file công khai và trả về đường dẫn web ("/uploads/xxx.jpg").
     * Trả null nếu không có file hoặc lưu thất bại.
     */
    String store(MultipartFile file);

    /**
     * Lưu file riêng tư vào thư mục con {@code folder} (ví dụ "asset-docs").
     * Trả về KHÓA lưu trữ ("asset-docs/uuid.jpg"), null nếu không có file hoặc lưu thất bại.
     */
    String storePrivate(String folder, MultipartFile file);

    /**
     * Lưu dữ liệu đã có sẵn trong bộ nhớ (ví dụ ảnh selfie sau khi kiểm tra) vào thư mục con.
     * {@code extension} dạng ".jpg". Trả về khóa lưu trữ, null nếu lưu thất bại.
     */
    String storePrivate(String folder, byte[] data, String extension, String contentType);

    /** Đọc lại file theo khóa (tên file công khai hoặc khóa riêng tư). Trả null nếu không tìm thấy. */
    StoredFile load(String key);

    /** Xóa file theo khóa. Không có file thì coi như đã xóa xong. */
    void delete(String key);

    /** Sinh tên file ngẫu nhiên, chỉ giữ phần đuôi an toàn của tên gốc. */
    static String randomFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0 && dot < original.length() - 1) {
                ext = original.substring(dot + 1);
            }
        }
        return UUID.randomUUID() + safeExtension(ext);
    }

    /** ".JPG" / "jpg" -> ".jpg"; đuôi lạ hoặc quá dài thì bỏ, tránh ký tự đường dẫn lọt vào tên file. */
    static String safeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        String ext = extension.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        if (ext.isEmpty()) {
            return "";
        }
        if (ext.length() > 10) {
            ext = ext.substring(0, 10);
        }
        return "." + ext;
    }

    /** Ghép thư mục con + tên file thành khóa; chặn ký tự lạ để không nhảy ra ngoài thư mục gốc. */
    static String privateKey(String folder, String filename) {
        String safeFolder = folder == null ? "" : folder.replaceAll("[^A-Za-z0-9_/-]", "")
                .replaceAll("/+", "/")
                .replaceAll("^/|/$", "");
        if (safeFolder.isEmpty()) {
            throw new IllegalArgumentException("Thư mục lưu file không hợp lệ: " + folder);
        }
        return safeFolder + "/" + filename;
    }
}
