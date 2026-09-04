package vn.midomax.helpdesk;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Người dùng khai báo trên máy chấm công.
 *
 * Giữ riêng khỏi {@link Employee}: đây là dữ liệu máy tự khai, chưa chắc khớp hồ sơ
 * nhân sự. Nhờ bảng này mà lịch chấm công hiện được tên người ngay cả khi chưa import
 * danh sách nhân viên.
 */
@Entity
@Table(name = "attendance_device_users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_code"}))
public class AttendanceDeviceUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "full_name")
    private String fullName;

    /** Máy gần nhất khai báo người này, để biết họ chấm công ở chi nhánh nào. */
    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
