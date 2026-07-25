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
