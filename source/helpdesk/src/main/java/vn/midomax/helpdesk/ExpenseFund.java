package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Quỹ chi tiêu công cụ / dụng cụ (CCDC) của team.
 * Ví dụ: công ty cấp 10.000.000đ cho một kỳ, các khoản chi sẽ trừ dần vào quỹ này.
 */
@Entity
@Table(name = "expense_funds")
public class ExpenseFund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // Ví dụ: "Quỹ CCDC Tháng 7/2026"

    @Column(nullable = false)
    private long allocatedAmount; // Số tiền được cấp (VND)

    private boolean active = true; // Quỹ đang sử dụng

    /**
     * Kỳ ngân sách của quỹ: chỉ hóa đơn trong khoảng này mới được tính vào "đã chi".
     * Ví dụ ngân sách 6 tháng cuối năm 2026 -> năm 2026, từ tháng 6 đến tháng 12.
     * Null = chưa đặt, khi đó tính cả năm.
     */
    private Integer budgetYear;
    private Integer fromMonth;
    private Integer toMonth;

    public Integer getBudgetYear() { return budgetYear; }
    public void setBudgetYear(Integer budgetYear) { this.budgetYear = budgetYear; }

    public Integer getFromMonth() { return fromMonth; }
    public void setFromMonth(Integer fromMonth) { this.fromMonth = fromMonth; }

    public Integer getToMonth() { return toMonth; }
    public void setToMonth(Integer toMonth) { this.toMonth = toMonth; }

    /** Tháng bắt đầu thực tế (mặc định 1). */
    public int startMonth() { return fromMonth == null ? 1 : Math.max(1, Math.min(12, fromMonth)); }

    /** Tháng kết thúc thực tế (mặc định 12). */
    public int endMonth() { return toMonth == null ? 12 : Math.max(1, Math.min(12, toMonth)); }

    /** Hóa đơn có nằm trong kỳ ngân sách của quỹ không. */
    public boolean covers(int year, int month) {
        if (budgetYear != null && budgetYear != year) return false;
        return month >= startMonth() && month <= endMonth();
    }

    /** Nhãn kỳ để hiển thị, ví dụ "T6–T12/2026". */
    public String getPeriodLabel() {
        if (budgetYear == null && fromMonth == null && toMonth == null) return "Cả năm";
        String range = "T" + startMonth() + "–T" + endMonth();
        return budgetYear == null ? range : range + "/" + budgetYear;
    }

    private String createdBy;

    private LocalDateTime createdAt;

    public ExpenseFund() {
    }

    public ExpenseFund(String name, long allocatedAmount, String createdBy) {
        this.name = name;
        this.allocatedAmount = allocatedAmount;
        this.createdBy = createdBy;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(long allocatedAmount) { this.allocatedAmount = allocatedAmount; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
