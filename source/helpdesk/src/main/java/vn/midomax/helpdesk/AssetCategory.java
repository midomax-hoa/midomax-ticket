package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Danh mục công cụ / dụng cụ: Thiết bị CNTT, Bàn ghế, Tivi, Điện thoại,
 * Thiết bị mạng, Camera, Máy nước nóng, Quạt...
 */
@Entity
@Table(name = "asset_categories")
public class AssetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // Tên danh mục

    private String icon; // Class icon FontAwesome, ví dụ "fa-solid fa-laptop"

    /**
     * Đánh dấu danh mục thuộc nhóm công nghệ thông tin.
     * Chỉ tài sản thuộc danh mục này mới xuất được form bàn giao.
     */
    @Column(nullable = false)
    private boolean itEquipment = false;

    private Integer sortOrder; // Thứ tự hiển thị

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime createdAt;

    public AssetCategory() {
    }

    public AssetCategory(String name, String icon, boolean itEquipment, Integer sortOrder) {
        this.name = name;
        this.icon = icon;
        this.itEquipment = itEquipment;
        this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public boolean isItEquipment() { return itEquipment; }
    public void setItEquipment(boolean itEquipment) { this.itEquipment = itEquipment; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
