package vn.midomax.helpdesk;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * License phần mềm NGOÀI Microsoft 365 (Adobe, Canva, antivirus, phần mềm bản
 * quyền...) — admin/IT tự tạo và cập nhật tay, không có nguồn nào để đồng bộ.
 * License 365 vẫn nằm riêng ở bảng license_365 vì nó đếm số đã cấp từ Microsoft.
 */
@Entity
@Table(name = "software_license")
public class SoftwareLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tên license, ví dụ "Adobe Creative Cloud", "Kaspersky Endpoint". */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Nhà cung cấp / hãng. */
    @Column(name = "vendor", length = 200)
    private String vendor;

    /** Số lượng đã mua theo hợp đồng. */
    @Column(name = "purchased_qty")
    private Integer purchasedQty = 0;

    /** Số đã cấp cho nhân viên — nhập tay vì không có nguồn đồng bộ. */
    @Column(name = "assigned_qty")
    private Integer assignedQty = 0;

    /** Ngày hết hạn gói. Null = vĩnh viễn / mua đứt. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public Integer getPurchasedQty() { return purchasedQty == null ? 0 : purchasedQty; }
    public void setPurchasedQty(Integer purchasedQty) { this.purchasedQty = purchasedQty; }

    public Integer getAssignedQty() { return assignedQty == null ? 0 : assignedQty; }
    public void setAssignedQty(Integer assignedQty) { this.assignedQty = assignedQty; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public int getRemaining() { return getPurchasedQty() - getAssignedQty(); }

    /** EXPIRED / EXPIRING (còn <= 30 ngày) / OVER (cấp quá số mua) / OK — để tô màu. */
    public String getStatus() {
        LocalDate today = LocalDate.now();
        if (expiryDate != null && expiryDate.isBefore(today)) return "EXPIRED";
        if (expiryDate != null && !expiryDate.isAfter(today.plusDays(30))) return "EXPIRING";
        if (getRemaining() < 0) return "OVER";
        return "OK";
    }
}
