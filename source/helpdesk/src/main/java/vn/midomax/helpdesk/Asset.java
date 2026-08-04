package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Một công cụ / dụng cụ (tài sản) doanh nghiệp đang quản lý.
 * Cấu trúc cột bám theo file Excel kiểm kê hiện hành:
 * nhóm "DỮ LIỆU TÀI SẢN" + nhóm "KIỂM KÊ TÀI SẢN".
 */
@Entity
@Table(name = "assets")
public class Asset {

    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== DỮ LIỆU TÀI SẢN =====

    /** Mã kiểm kê — để trống được với phụ kiện lẻ không dán mã. */
    @Column(unique = true)
    private String inventoryCode;

    private Long categoryId; // Danh mục (AssetCategory)

    private String officeLocation; // Địa điểm văn phòng

    private Integer quantity = 1; // Số lượng

    // --- Giao cho ---
    private String assignedToName; // Họ và tên
    private String assignedToPosition; // Chức vụ (dùng cho biên bản bàn giao)
    private String assignedToDepartment; // Bộ phận / vị trí
    private String assignedToLocation; // Địa điểm

    private String assetType; // Loại tài sản
    private String manufacturer; // Nhà sản xuất
    private String model; // Model

    @Column(columnDefinition = "TEXT")
    private String details; // Thông tin chi tiết (cấu hình, mô tả...)

    private String serialNumber; // Serial Number
    private LocalDate purchaseDate; // Ngày mua

    /** Trạng thái sử dụng: Đang sử dụng / Trong kho / Đang sửa / Hỏng / Đã thanh lý. */
    private String status;

    // ===== KIỂM KÊ TÀI SẢN =====

    private LocalDate lastInventoryDate; // Ngày kiểm kê gần nhất
    private String inventoryBy; // Người kiểm kê
    private String inventoryCondition; // Tình trạng TS khi kiểm kê

    @Column(columnDefinition = "TEXT")
    private String note; // Ghi chú

    // ===== AUDIT =====

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Asset() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInventoryCode() { return inventoryCode; }
    public void setInventoryCode(String inventoryCode) { this.inventoryCode = inventoryCode; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getOfficeLocation() { return officeLocation; }
    public void setOfficeLocation(String officeLocation) { this.officeLocation = officeLocation; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }

    public String getAssignedToPosition() { return assignedToPosition; }
    public void setAssignedToPosition(String assignedToPosition) { this.assignedToPosition = assignedToPosition; }

    public String getAssignedToDepartment() { return assignedToDepartment; }
    public void setAssignedToDepartment(String assignedToDepartment) { this.assignedToDepartment = assignedToDepartment; }

    public String getAssignedToLocation() { return assignedToLocation; }
    public void setAssignedToLocation(String assignedToLocation) { this.assignedToLocation = assignedToLocation; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getLastInventoryDate() { return lastInventoryDate; }
    public void setLastInventoryDate(LocalDate lastInventoryDate) { this.lastInventoryDate = lastInventoryDate; }

    public String getInventoryBy() { return inventoryBy; }
    public void setInventoryBy(String inventoryBy) { this.inventoryBy = inventoryBy; }

    public String getInventoryCondition() { return inventoryCondition; }
    public void setInventoryCondition(String inventoryCondition) { this.inventoryCondition = inventoryCondition; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    /** Người giao thiết bị (bên A) khi bàn giao. */
    @Column(name = "handover_by")
    private String handoverBy;

    /** Thời gian bàn giao cho người sử dụng. */
    @Column(name = "handover_date")
    private LocalDateTime handoverDate;

    /** Chức vụ của người giao. */
    @Column(name = "handover_by_position")
    private String handoverByPosition;

    /** Phòng ban của người giao. */
    @Column(name = "handover_by_department")
    private String handoverByDepartment;


    public String getHandoverBy() { return handoverBy; }
    public void setHandoverBy(String handoverBy) { this.handoverBy = handoverBy; }
    public String getHandoverByPosition() { return handoverByPosition; }
    public void setHandoverByPosition(String handoverByPosition) { this.handoverByPosition = handoverByPosition; }
    public String getHandoverByDepartment() { return handoverByDepartment; }
    public void setHandoverByDepartment(String handoverByDepartment) { this.handoverByDepartment = handoverByDepartment; }
    public LocalDateTime getHandoverDate() { return handoverDate; }
    public void setHandoverDate(LocalDateTime handoverDate) { this.handoverDate = handoverDate; }

    /** Thời gian bàn giao dạng dd/MM/yyyy HH:mm để hiển thị. */
    @jakarta.persistence.Transient
    public String getHandoverDateStr() {
        return handoverDate != null ? handoverDate.format(DT_FMT) : "";
    }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ===== Tiện ích hiển thị cho Thymeleaf =====

    public String getPurchaseDateStr() { return fmt(purchaseDate); }

    public String getLastInventoryDateStr() { return fmt(lastInventoryDate); }

    public String getCreatedAtStr() {
        if (createdAt == null) return "";
        try {
            return createdAt.format(DT_FMT);
        } catch (Exception e) {
            return "";
        }
    }

    /** Giá trị cho input type=date ("yyyy-MM-dd"). */
    public String getPurchaseDateInput() { return purchaseDate == null ? "" : purchaseDate.toString(); }

    public String getLastInventoryDateInput() { return lastInventoryDate == null ? "" : lastInventoryDate.toString(); }

    private String fmt(LocalDate d) {
        if (d == null) return "";
        try {
            return d.format(D_FMT);
        } catch (Exception e) {
            return "";
        }
    }
}
