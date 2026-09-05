package vn.midomax.helpdesk;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Một loại license Microsoft 365 mà công ty đang dùng (mỗi dòng ứng với một skuId
 * bên Microsoft).
 *
 * Số ĐÃ CẤP không lưu ở đây — nó được đếm trực tiếp từ Microsoft 365 mỗi lần mở
 * trang, nên không bao giờ lệch với thực tế. Bảng này chỉ giữ những thứ Microsoft
 * không biết: số công ty đã mua theo hợp đồng, tên gọi cho dễ đọc, và loại nào có
 * mất tiền để kế toán khỏi phải nhìn mấy license miễn phí.
 */
@Entity
@Table(name = "license_365", uniqueConstraints = @UniqueConstraint(columnNames = "sku_id"))
public class License365 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GUID định danh loại license bên Microsoft. Đây mới là khoá thật, không phải tên. */
    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    /** Tên hiển thị cho người dùng, admin tự đặt (Microsoft trả về GUID chứ không có tên). */
    @Column(name = "display_name", length = 200)
    private String displayName;

    /** Số license công ty đã mua theo hợp đồng. Admin nhập tay. */
    @Column(name = "purchased_qty")
    private Integer purchasedQty = 0;

    /** Có mất tiền không. License miễn phí (Power Automate Free...) để false cho gọn danh sách. */
    @Column(name = "paid")
    private Boolean paid = true;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    public License365() {
    }

    public License365(String skuId, String displayName, Boolean paid) {
        this.skuId = skuId;
        this.displayName = displayName;
        this.paid = paid;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Integer getPurchasedQty() { return purchasedQty == null ? 0 : purchasedQty; }
    public void setPurchasedQty(Integer purchasedQty) { this.purchasedQty = purchasedQty; }

    public Boolean getPaid() { return paid == null || paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
