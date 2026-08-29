package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Một máy chấm công ZK Device. Công ty có nhiều chi nhánh nên mỗi máy là một dòng
 * riêng: máy trong LAN dùng IP nội bộ, máy chi nhánh xa dùng IP public đã NAT về
 * cổng 4370.
 */
@Entity
@Table(name = "attendance_devices")
public class AttendanceDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;        // Tên gợi nhớ, ví dụ "Kho HCM"
    private String location;    // Địa điểm đặt máy
    private String ipAddress;
    private Integer port = 4370;
    private Integer commKey = 0; // Mật khẩu kết nối trên máy, mặc định 0
    private Boolean active = true;

    private LocalDateTime lastSyncAt;
    private String lastSyncStatus; // Kết quả lần tải gần nhất, hiển thị ở bảng thiết bị

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public Integer getCommKey() { return commKey; }
    public void setCommKey(Integer commKey) { this.commKey = commKey; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public String getLastSyncStatus() { return lastSyncStatus; }
    public void setLastSyncStatus(String lastSyncStatus) { this.lastSyncStatus = lastSyncStatus; }
}
