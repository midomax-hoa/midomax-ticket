package vn.midomax.helpdesk.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Nơi lưu file đính kèm (ảnh ticket, hóa đơn, chứng từ...).
 *
 * Đường dẫn trả về luôn có dạng "/uploads/{tên-file}" để giữ nguyên dữ liệu cũ
 * đang lưu trong database, bất kể file nằm ở ổ đĩa hay trên MinIO.
 */
public interface StorageService {

    String URL_PREFIX = "/uploads/";

    /**
     * Lưu file và trả về đường dẫn web ("/uploads/xxx.jpg").
     * Trả null nếu không có file hoặc lưu thất bại.
     */
    String store(MultipartFile file);

    /** Đọc lại file đã lưu theo tên. Trả null nếu không tìm thấy. */
    StoredFile load(String filename);

    /** Sinh tên file ngẫu nhiên, chỉ giữ phần đuôi an toàn của tên gốc. */
    static String randomFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0 && dot < original.length() - 1) {
                ext = original.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
                if (ext.length() > 10) {
                    ext = ext.substring(0, 10);
                }
                if (!ext.isEmpty()) {
                    ext = "." + ext.toLowerCase();
                }
            }
        }
        return UUID.randomUUID() + ext;
    }
}
