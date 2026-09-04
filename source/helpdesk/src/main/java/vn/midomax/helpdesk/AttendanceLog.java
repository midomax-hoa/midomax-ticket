package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Một lần quét thô lấy về từ máy chấm công. Giữ nguyên như máy trả về, không sửa;
 * mọi tính toán công đều dựng lại được từ bảng này.
 *
 * Ràng buộc duy nhất (mã NV + thời điểm + máy) để tải lại nhiều lần không bị trùng.
 */
@Entity
@Table(name = "attendance_logs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employeeCode", "punchTime", "deviceId"}),
       indexes = @Index(name = "idx_att_log_time", columnList = "punchTime"))
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeCode;   // Mã trên máy chấm công, khớp Employee.employeeCode
    private LocalDateTime punchTime;

    private Long deviceId;
    private String deviceName;

    private Integer verifyMode;    // 1=vân tay, 4=thẻ, 15=khuôn mặt...
    private Integer punchState;    // 0=vào, 1=ra... tuỳ cấu hình máy, chỉ để tham khảo

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public LocalDateTime getPunchTime() { return punchTime; }
    public void setPunchTime(LocalDateTime punchTime) { this.punchTime = punchTime; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public Integer getVerifyMode() { return verifyMode; }
    public void setVerifyMode(Integer verifyMode) { this.verifyMode = verifyMode; }

    public Integer getPunchState() { return punchState; }
    public void setPunchState(Integer punchState) { this.punchState = punchState; }
}
