package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một khoản chi mua công cụ / dụng cụ, trừ vào một {@link ExpenseFund}.
 */
@Entity
@Table(name = "tool_expenses")
public class ToolExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fundId; // Thuộc quỹ nào

    @Column(nullable = false)
    private String itemName; // Tên công cụ / dụng cụ đã mua

    @Column(nullable = false)
    private long amount; // Số tiền đã chi (VND)

    private String category; // Loại: Công cụ, Dụng cụ, Vật tư... (tùy chọn)

    private Long budgetItemId; // Hạng mục ngân sách khấu trừ (BudgetItem.id)

    private String invoiceNumber; // Số hóa đơn

    private LocalDate invoiceDate; // Ngày trên hóa đơn

    private String invoicePath; // Đường dẫn ảnh hóa đơn (/uploads/...)

    @Column(columnDefinition = "TEXT")
    private String note;

    private String createdBy; // Người chi

    private LocalDateTime createdAt;

    public ToolExpense() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFundId() { return fundId; }
    public void setFundId(Long fundId) { this.fundId = fundId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Long getBudgetItemId() { return budgetItemId; }
    public void setBudgetItemId(Long budgetItemId) { this.budgetItemId = budgetItemId; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public String getInvoiceDateStr() {
        if (invoiceDate == null) return "";
        try {
            return invoiceDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return "";
        }
    }

    public String getInvoicePath() { return invoicePath; }
    public void setInvoicePath(String invoicePath) { this.invoicePath = invoicePath; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedAtStr() {
        if (createdAt == null) return "";
        try {
            return createdAt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return "";
        }
    }
}
