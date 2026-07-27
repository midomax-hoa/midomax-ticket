package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Nhật ký lịch sử người sử dụng / bàn giao tài sản (công cụ dụng cụ).
 */
@Entity
@Table(name = "asset_usage_histories")
public class AssetUsageHistory {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long assetId; // ID tài sản

    private String inventoryCode; // Mã kiểm kê

    private String assignedToName; // Họ và tên người sử dụng
    private String assignedToPosition; // Chức vụ
    private String assignedToDepartment; // Bộ phận / phòng ban
    private String assignedToLocation; // Địa điểm văn phòng

    private LocalDateTime assignedDate; // Thời điểm nhận máy / cấp phát
    private LocalDateTime returnedDate; // Thời điểm trả máy / chuyển giao (null = đang sử dụng)

    @Column(columnDefinition = "TEXT")
    private String note; // Ghi chú (VD: Cấp mới, Người cũ nghỉ việc, Chuyển phòng...)

    private String createdBy; // Tài khoản ghi nhận log

    public AssetUsageHistory() {
    }

    public AssetUsageHistory(Long assetId, String inventoryCode, String assignedToName,
                             String assignedToPosition, String assignedToDepartment,
                             String assignedToLocation, LocalDateTime assignedDate,
                             String note, String createdBy) {
        this.assetId = assetId;
        this.inventoryCode = inventoryCode;
        this.assignedToName = assignedToName;
        this.assignedToPosition = assignedToPosition;
        this.assignedToDepartment = assignedToDepartment;
        this.assignedToLocation = assignedToLocation;
        this.assignedDate = assignedDate != null ? assignedDate : LocalDateTime.now();
        this.note = note;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getInventoryCode() { return inventoryCode; }
    public void setInventoryCode(String inventoryCode) { this.inventoryCode = inventoryCode; }

    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }

    public String getAssignedToPosition() { return assignedToPosition; }
    public void setAssignedToPosition(String assignedToPosition) { this.assignedToPosition = assignedToPosition; }

    public String getAssignedToDepartment() { return assignedToDepartment; }
    public void setAssignedToDepartment(String assignedToDepartment) { this.assignedToDepartment = assignedToDepartment; }

    public String getAssignedToLocation() { return assignedToLocation; }
    public void setAssignedToLocation(String assignedToLocation) { this.assignedToLocation = assignedToLocation; }

    public LocalDateTime getAssignedDate() { return assignedDate; }
    public void setAssignedDate(LocalDateTime assignedDate) { this.assignedDate = assignedDate; }

    public LocalDateTime getReturnedDate() { return returnedDate; }
    public void setReturnedDate(LocalDateTime returnedDate) { this.returnedDate = returnedDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getAssignedDateStr() {
        return assignedDate == null ? "" : assignedDate.format(DT_FMT);
    }

    public String getReturnedDateStr() {
        return returnedDate == null ? "Đang sử dụng" : returnedDate.format(DT_FMT);
    }
}
