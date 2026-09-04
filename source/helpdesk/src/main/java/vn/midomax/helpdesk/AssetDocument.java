package vn.midomax.helpdesk;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ảnh chụp biên bản giao nhận / thu hồi tài sản.
 *
 * Biên bản giấy có chữ ký tay là bằng chứng gốc, nên ở đây chỉ lưu ảnh chụp lại
 * (thường chụp bằng điện thoại) gắn vào đúng tài sản. Một lần bàn giao có thể
 * nhiều tờ nên mỗi tờ là một dòng, gom lại theo cùng tài sản và cùng loại.
 */
@Entity
@Table(name = "asset_documents")
public class AssetDocument {

    public static final String TYPE_HANDOVER = "HANDOVER"; // Biên bản giao nhận
    public static final String TYPE_RECLAIM = "RECLAIM";   // Biên bản thu hồi
    public static final String TYPE_REPAIR = "REPAIR";     // Biên bản sửa chữa
    public static final String TYPE_BROKEN = "BROKEN";     // Biên bản báo hỏng

    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** HANDOVER = giao nhận, RECLAIM = thu hồi. */
    @Column(name = "doc_type", length = 20)
    private String docType = TYPE_HANDOVER;

    /** Đường dẫn web để hiển thị ảnh, dạng /asset-docs/<tên file>. */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /** Tên file gốc lúc tải lên, để người dùng nhận ra khi tải về. */
    @Column(name = "original_name", length = 300)
    private String originalName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** Ngày ghi trên biên bản giấy — không nhất thiết trùng ngày tải ảnh lên. */
    @Column(name = "doc_date")
    private LocalDate docDate;

    /** Người nhận (biên bản giao) hoặc người trả (biên bản thu hồi). */
    @Column(name = "person_name", length = 200)
    private String personName;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by", length = 150)
    private String uploadedBy;

    public AssetDocument() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate docDate) { this.docDate = docDate; }

    public String getPersonName() { return personName; }
    public void setPersonName(String personName) { this.personName = personName; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    // ===== Tiện ích cho giao diện =====

    public boolean isReclaim() { return TYPE_RECLAIM.equals(docType); }

    public String getTypeLabel() {
        if (TYPE_RECLAIM.equals(docType)) return "Biên bản thu hồi";
        if (TYPE_REPAIR.equals(docType)) return "Biên bản sửa chữa";
        if (TYPE_BROKEN.equals(docType)) return "Biên bản báo hỏng";
        return "Biên bản giao nhận";
    }

    /** PDF hiển thị bằng icon chứ không nhúng ảnh được. */
    public boolean isImage() {
        return contentType != null && contentType.toLowerCase().startsWith("image/");
    }

    public String getDocDateStr() {
        return docDate == null ? "" : docDate.format(D_FMT);
    }

    public String getUploadedAtStr() {
        return uploadedAt == null ? "" : uploadedAt.format(DT_FMT);
    }

    public String getSizeStr() {
        if (sizeBytes == null || sizeBytes <= 0) return "";
        double mb = sizeBytes / 1024.0 / 1024.0;
        if (mb >= 1) return String.format("%.1f MB", mb);
        return Math.max(1, Math.round(sizeBytes / 1024.0)) + " KB";
    }
}
