package vn.midomax.helpdesk;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Lưu ảnh biên bản giao nhận / thu hồi tài sản.
 *
 * Ảnh KHÔNG lưu vào src/main/resources hay target/ như mấy chỗ upload cũ trong hệ
 * thống: hai thư mục đó là nơi build, chạy "mvn clean" một phát là mất sạch. Biên bản
 * bàn giao là giấy tờ cần giữ lâu nên lưu ra thư mục dữ liệu riêng, đổi được qua
 * cấu hình app.asset-doc-dir nếu muốn để sang ổ khác hoặc thư mục có backup.
 */
@Service
public class AssetDocumentService {

    /** Chỉ nhận ảnh và PDF. Chặn .html/.svg vì file đó phục vụ từ domain app sẽ chạy được script. */
    private static final Set<String> ALLOWED_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".pdf");

    private static final long MAX_BYTES = 15L * 1024 * 1024;

    @Value("${app.asset-doc-dir:data/asset-docs}")
    private String configuredDir;

    @Autowired
    private AssetDocumentRepository documentRepository;

    private Path storageDir;

    @PostConstruct
    public void init() {
        storageDir = Paths.get(configuredDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
            System.out.println("[ASSET DOC] Thư mục lưu biên bản: " + storageDir);
        } catch (IOException e) {
            System.err.println("[ASSET DOC] Không tạo được thư mục " + storageDir + ": " + e.getMessage());
        }
    }

    public Path getStorageDir() {
        return storageDir;
    }

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

            String newName = UUID.randomUUID() + ext;
            Path target = storageDir.resolve(newName).normalize();
            if (!target.startsWith(storageDir)) {
                result.errors.add("Bỏ qua \"" + original + "\": tên file không hợp lệ.");
                continue;
            }

            try {
                Files.createDirectories(storageDir);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                result.errors.add("Không lưu được \"" + original + "\": " + e.getMessage());
                continue;
            }

            AssetDocument doc = new AssetDocument();
            doc.setAssetId(assetId);
            doc.setDocType(type);
            doc.setFilePath("/asset-docs/" + newName);
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

    /** Xoá cả bản ghi lẫn file trên đĩa, đừng để file rác nằm lại. */
    public boolean delete(Long id) {
        AssetDocument doc = get(id);
        if (doc == null) return false;
        deleteFileOf(doc);
        documentRepository.delete(doc);
        return true;
    }

    /** Xoá tài sản thì xoá kèm biên bản của nó. */
    public void deleteAllOfAsset(Long assetId) {
        List<AssetDocument> docs = documentRepository.findByAssetIdOrderByUploadedAtDesc(assetId);
        for (AssetDocument d : docs) deleteFileOf(d);
        if (!docs.isEmpty()) documentRepository.deleteAll(docs);
    }

    private void deleteFileOf(AssetDocument doc) {
        if (doc.getFilePath() == null) return;
        String name = doc.getFilePath().substring(doc.getFilePath().lastIndexOf('/') + 1);
        try {
            Path p = storageDir.resolve(name).normalize();
            if (p.startsWith(storageDir)) Files.deleteIfExists(p);
        } catch (IOException e) {
            System.err.println("[ASSET DOC] Không xoá được file " + name + ": " + e.getMessage());
        }
    }
}
