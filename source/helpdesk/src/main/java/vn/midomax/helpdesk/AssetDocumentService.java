package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.midomax.helpdesk.storage.StorageService;
import vn.midomax.helpdesk.storage.StoredFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lưu ảnh biên bản giao nhận / thu hồi tài sản.
 *
 * Ảnh đi qua StorageService (MinIO khi production, ổ đĩa khi dev) dưới thư mục
 * {@link #STORAGE_FOLDER}, là file RIÊNG TƯ: không phục vụ qua /uploads mà qua
 * AssetDocumentFileController sau khi kiểm đăng nhập. Biên bản là giấy tờ cần giữ lâu
 * nên không được nằm trong container hay thư mục build.
 */
@Service
public class AssetDocumentService {

    /** Thư mục con trên kho file chứa biên bản. */
    public static final String STORAGE_FOLDER = "asset-docs";

    /** Đường dẫn web của biên bản, giữ nguyên dạng cũ để dữ liệu đã lưu trong DB vẫn dùng được. */
    public static final String URL_PREFIX = "/asset-docs/";

    /** Chỉ nhận ảnh và PDF. Chặn .html/.svg vì file đó phục vụ từ domain app sẽ chạy được script. */
    private static final Set<String> ALLOWED_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".pdf");

    private static final long MAX_BYTES = 15L * 1024 * 1024;

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,199}");

    @Autowired
    private AssetDocumentRepository documentRepository;

    @Autowired
    private StorageService storageService;

    /** Kết quả một lần tải lên nhiều ảnh: mấy tấm được, mấy tấm bị loại và vì sao. */
    public static class UploadResult {
        private int saved = 0;
        private final List<String> errors = new ArrayList<>();

        public int getSaved() { return saved; }
        public List<String> getErrors() { return errors; }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    private static String extOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        String ext = filename.substring(dot).toLowerCase();
        // Đuôi lấy từ tên người dùng đặt nên phải chặn ký tự đường dẫn, tránh ghi ra ngoài thư mục
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : "";
    }

    public UploadResult upload(Long assetId, String docType, MultipartFile[] files,
                               LocalDate docDate, String personName, String note, String actor) {
        UploadResult result = new UploadResult();
        if (files == null) return result;

        String type = AssetDocument.TYPE_RECLAIM.equals(docType) ? AssetDocument.TYPE_RECLAIM
                : AssetDocument.TYPE_REPAIR.equals(docType) ? AssetDocument.TYPE_REPAIR
                : AssetDocument.TYPE_BROKEN.equals(docType) ? AssetDocument.TYPE_BROKEN
                : AssetDocument.TYPE_HANDOVER;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            String original = file.getOriginalFilename();
            String ext = extOf(original);
            if (!ALLOWED_EXT.contains(ext)) {
                result.errors.add("Bỏ qua \"" + original + "\": chỉ nhận ảnh (jpg, png, webp, heic) hoặc PDF.");
                continue;
            }
            if (file.getSize() > MAX_BYTES) {
                result.errors.add("Bỏ qua \"" + original + "\": nặng hơn 15 MB.");
                continue;
            }

            String key = storageService.storePrivate(STORAGE_FOLDER, file);
            if (key == null) {
                result.errors.add("Không lưu được \"" + original + "\" lên kho file. Thử lại hoặc báo IT.");
                continue;
            }

            AssetDocument doc = new AssetDocument();
            doc.setAssetId(assetId);
            doc.setDocType(type);
            doc.setFilePath(URL_PREFIX + key.substring(STORAGE_FOLDER.length() + 1));
            doc.setOriginalName(original);
            doc.setContentType(file.getContentType());
            doc.setSizeBytes(file.getSize());
            doc.setDocDate(docDate);
            doc.setPersonName(personName == null || personName.isBlank() ? null : personName.trim());
            doc.setNote(note == null || note.isBlank() ? null : note.trim());
            doc.setUploadedAt(LocalDateTime.now());
            doc.setUploadedBy(actor);
            documentRepository.save(doc);
            result.saved++;
        }
        return result;
    }

    public List<AssetDocument> listOf(Long assetId) {
        return documentRepository.findByAssetIdOrderByUploadedAtDesc(assetId);
    }

    /** Số biên bản của từng tài sản, để danh sách hiện được huy hiệu mà không truy vấn từng dòng. */
    public Map<Long, Integer> countByAsset(List<Long> assetIds) {
        Map<Long, Integer> counts = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return counts;
        for (AssetDocument d : documentRepository.findByAssetIdIn(assetIds)) {
            counts.merge(d.getAssetId(), 1, Integer::sum);
        }
        return counts;
    }

    public AssetDocument get(Long id) {
        return id == null ? null : documentRepository.findById(id).orElse(null);
    }

    /** Mở file biên bản theo tên trên URL (/asset-docs/{tên}). Null nếu tên lạ hoặc không có file. */
    public StoredFile open(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches() || name.contains("..")) return null;
        return storageService.load(STORAGE_FOLDER + "/" + name);
    }

    /** Xóa cả bản ghi lẫn file trên kho, đừng để file rác nằm lại. */
    public boolean delete(Long id) {
        AssetDocument doc = get(id);
        if (doc == null) return false;
        deleteFileOf(doc);
        documentRepository.delete(doc);
        return true;
    }

    /** Xóa tài sản thì xóa kèm biên bản của nó. */
    public void deleteAllOfAsset(Long assetId) {
        List<AssetDocument> docs = documentRepository.findByAssetIdOrderByUploadedAtDesc(assetId);
        for (AssetDocument d : docs) deleteFileOf(d);
        if (!docs.isEmpty()) documentRepository.deleteAll(docs);
    }

    private void deleteFileOf(AssetDocument doc) {
        String key = keyOf(doc);
        if (key != null) storageService.delete(key);
    }

    /** "/asset-docs/uuid.jpg" trong DB -> khóa "asset-docs/uuid.jpg" trên kho file. */
    private static String keyOf(AssetDocument doc) {
        if (doc.getFilePath() == null) return null;
        String name = doc.getFilePath().substring(doc.getFilePath().lastIndexOf('/') + 1);
        return name.isBlank() ? null : STORAGE_FOLDER + "/" + name;
    }
}
