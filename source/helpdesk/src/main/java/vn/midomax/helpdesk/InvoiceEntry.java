package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Một dòng hóa đơn / phiếu chi trong Sổ Hóa Đơn.
 *
 * Ánh xạ 1-1 với bảng Excel phiếu chi đang dùng thủ công, để nhập file cũ vào là
 * khớp cột. Khác {@link ToolExpense} (khoản chi trừ vào quỹ CCDC): sổ này ghi mọi
 * chi tiêu công ty gồm CAPEX, thuê bao định kỳ, nhà cung cấp pháp nhân.
 *
 * Loại chi và trạng thái thanh toán lưu bằng String thay vì enum: file Excel nhập
 * vào có giá trị song ngữ, viết hoa/thường lẫn lộn nên enum sẽ ném lỗi giữa chừng
 * và hỏng cả lần nhập. Chuẩn hóa khi ghi, xem {@link #normalizeExpenseType}.
 */
@Entity
@Table(name = "invoice_entries")
public class InvoiceEntry {

    public static final String TYPE_ONE_TIME = "ONE_TIME";
    public static final String TYPE_RECURRING = "RECURRING";

    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_UNPAID = "UNPAID";
    public static final String STATUS_PENDING = "PENDING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ngày nhập liệu (Entry Date) — khác ngày giao dịch khi nhập bù. */
    private LocalDate entryDate;

    /** Ngày giao dịch thật trên chứng từ (Trans. Date) — mốc để tính kỳ kế toán. */
    private LocalDate transDate;

    /** Kỳ kế toán dạng "yyyy-MM", suy từ transDate. Lưu sẵn để lọc/gộp không phải tính lại. */
    private String periodKey;

    /** Số PO / số tham chiếu chứng từ, vd "PC_2425_06_2026". */
    private String poRef;

    /** Danh mục lớn, vd "CAPEX — Thiết bị & Bản quyền | Hardware & Licenses". */
    private String category;

    /** Phân loại nhỏ, vd "Trang Thiết Bị". */
    private String subCategory;

    /** Nhà cung cấp hoặc người nhận tiền. */
    private String vendor;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Số tiền (VND). Dùng long vì VND không có phần lẻ. */
    private long amount;

    /** ONE_TIME | RECURRING */
    private String expenseType;

    /** PAID | UNPAID | PENDING */
    private String paymentStatus;

    private String enteredBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Ảnh/file chứng từ đính kèm (/uploads/...). */
    private String attachmentPath;

    private LocalDateTime createdAt;

    public InvoiceEntry() {
    }

    // ----- chuẩn hóa giá trị nhập từ Excel (song ngữ, hoa thường lẫn lộn) -----

    /** "Định kỳ | Recurring", "recurring", "DINH KY" -> RECURRING; còn lại ONE_TIME. */
    public static String normalizeExpenseType(String raw) {
        if (raw == null) return TYPE_ONE_TIME;
        String v = raw.toLowerCase();
        if (v.contains("định kỳ") || v.contains("dinh ky") || v.contains("recurring")) {
            return TYPE_RECURRING;
        }
        return TYPE_ONE_TIME;
    }

    /** Nhận cả tiếng Việt lẫn tiếng Anh; không rõ thì coi như chưa thanh toán. */
    public static String normalizePaymentStatus(String raw) {
        if (raw == null) return STATUS_UNPAID;
        String v = raw.toLowerCase();
        if (v.contains("đã thanh toán") || v.contains("da thanh toan") || v.contains("paid")) {
            // "unpaid" cũng chứa "paid" nên phải loại trừ trước
            if (v.contains("chưa") || v.contains("chua") || v.contains("unpaid")) return STATUS_UNPAID;
            return STATUS_PAID;
        }
        if (v.contains("chờ") || v.contains("cho duyet") || v.contains("pending")) return STATUS_PENDING;
        return STATUS_UNPAID;
    }

    /** Kỳ kế toán "yyyy-MM" lấy theo ngày giao dịch, thiếu thì lùi về ngày nhập. */
    public static String periodKeyOf(LocalDate transDate, LocalDate entryDate) {
        LocalDate base = transDate != null ? transDate : entryDate;
        if (base == null) return null;
        return String.format("%04d-%02d", base.getYear(), base.getMonthValue());
    }

    // ----- nhãn hiển thị -----

    public String getExpenseTypeLabel() {
        return TYPE_RECURRING.equals(expenseType) ? "Định kỳ" : "Một lần";
    }

    public String getPaymentStatusLabel() {
        if (STATUS_PAID.equals(paymentStatus)) return "Đã thanh toán";
        if (STATUS_PENDING.equals(paymentStatus)) return "Chờ duyệt";
        return "Chưa thanh toán";
    }

    public String getPeriodLabel() {
        if (periodKey == null || !periodKey.contains("-")) return "";
        String[] p = periodKey.split("-");
        return "T" + Integer.parseInt(p[1]) + "/" + p[0];
    }

    public String getEntryDateStr() { return fmt(entryDate); }

    public String getTransDateStr() { return fmt(transDate); }

    private String fmt(LocalDate d) {
        return d == null ? "" : d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // ----- getters / setters -----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }

    public LocalDate getTransDate() { return transDate; }
    public void setTransDate(LocalDate transDate) { this.transDate = transDate; }

    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }

    public String getPoRef() { return poRef; }
    public void setPoRef(String poRef) { this.poRef = poRef; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getExpenseType() { return expenseType; }
    public void setExpenseType(String expenseType) { this.expenseType = expenseType; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getEnteredBy() { return enteredBy; }
    public void setEnteredBy(String enteredBy) { this.enteredBy = enteredBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
