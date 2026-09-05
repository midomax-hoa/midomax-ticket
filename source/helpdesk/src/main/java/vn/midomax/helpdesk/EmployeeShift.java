package vn.midomax.helpdesk;

import jakarta.persistence.*;

/**
 * Gán ca làm việc cho nhân viên.
 *
 * Cố ý tách thành bảng riêng thay vì thêm cột vào {@link Employee}: phần import
 * Excel nhân sự map cột theo thứ tự khai báo field của Employee, nên thêm field
 * mới vào đó sẽ làm lệch toàn bộ cột khi import.
 */
@Entity
@Table(name = "employee_shifts", uniqueConstraints = @UniqueConstraint(columnNames = {"employeeCode", "deviceId"}))
public class EmployeeShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeCode;

    /**
     * Máy chấm công (văn phòng) — ca gán theo NGƯỜI = (máy, mã) vì các văn phòng
     * trùng dải mã và ca kho khác ca văn phòng. Null = dòng cũ chưa phân máy,
     * vẫn được dùng làm phương án dự phòng khi (máy, mã) chưa gán ca riêng.
     */
    private Long deviceId;

    private Long shiftId;

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
}
