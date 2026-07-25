package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Hạng mục ngân sách được phân bổ cho một quỹ (kết xuất từ file Excel ngân sách).
 * Ví dụ: Chi phí nhóm 2 ("Phần cứng"), Chi phí nhóm 3 ("Phần cứng Laptop/PC"), Nội dung ("Desktop for Designer").
 */
@Entity
@Table(name = "budget_items")
public class BudgetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fundId; // Thuộc quỹ nào (ExpenseFund)

    private String groupCategory; // Chi phí nhóm 2 (Phần cứng, Bản quyền ứng dụng, Phí Thuê Máy Photo...)

    private String subCategory; // Chi phí nhóm 3 (Phần cứng Laptop/PC, Thiết bị khác, License...)

    @Column(nullable = false)
    private String itemName; // Nội dung (Giám đốc, Quản lý, Màn hình, Visio Standard...)

    @Column(columnDefinition = "TEXT")
    private String description; // Mô tả chi tiết

    private Long unitPrice = 0L; // Đơn giá

    private String unit; // Đơn vị (Cái, User, Tháng...)

    private Integer quantity = 1; // Số lượng

    private Long allocatedAmount = 0L; // Thành tiền (Ngân sách cấp)

    private Long spentAmount = 0L; // Tổng số tiền đã chi (Khấu trừ)

    @Column(columnDefinition = "TEXT")
    private String notes; // Ghi chú

    private LocalDateTime createdAt;

    public BudgetItem() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFundId() { return fundId; }
    public void setFundId(Long fundId) { this.fundId = fundId; }

    public String getGroupCategory() { return groupCategory; }
    public void setGroupCategory(String groupCategory) { this.groupCategory = groupCategory; }

    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getUnitPrice() { return unitPrice != null ? unitPrice : 0L; }
    public void setUnitPrice(Long unitPrice) { this.unitPrice = unitPrice; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getQuantity() { return quantity != null ? quantity : 1; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Long getAllocatedAmount() { return allocatedAmount != null ? allocatedAmount : 0L; }
    public void setAllocatedAmount(Long allocatedAmount) { this.allocatedAmount = allocatedAmount; }

    public Long getSpentAmount() { return spentAmount != null ? spentAmount : 0L; }
    public void setSpentAmount(Long spentAmount) { this.spentAmount = spentAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Tính số tiền còn lại của hạng mục (Ngân sách cấp - Đã chi) */
    public long getRemainingAmount() {
        return getAllocatedAmount() - getSpentAmount();
    }

    /** Tính phần trăm ngân sách đã sử dụng (0..100) */
    public int getSpentPercent() {
        if (getAllocatedAmount() <= 0) return 0;
        int pct = (int) Math.round((getSpentAmount() * 100.0) / getAllocatedAmount());
        return Math.max(0, Math.min(100, pct));
    }
}
