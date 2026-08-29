package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Kết quả chấm công đã tổng hợp cho một nhân viên trong một ngày. Được dựng lại từ
 * {@link AttendanceLog}; nếu người dùng sửa tay thì bật {@code manualOverride} và
 * lần tổng hợp sau sẽ không ghi đè dòng đó.
 */
@Entity
@Table(name = "attendance_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employeeCode", "workDate"}),
       indexes = @Index(name = "idx_att_rec_date", columnList = "workDate"))
public class AttendanceRecord {

    /** Đủ công. */ public static final String ST_OK = "OK";
    /** Đi muộn hoặc về sớm. */ public static final String ST_LATE = "LATE";
    /** Chỉ chấm công một buổi (đi công tác nửa ngày) — chờ Giấy bổ sung công, không tính đi trễ. */
    public static final String ST_HALF = "HALF";
    /** Chỉ quét được 1 lần trong ngày. */ public static final String ST_MISSING = "MISSING";
    /** Ngày làm việc nhưng không có lần quét nào. */ public static final String ST_ABSENT = "ABSENT";
    /** Nghỉ phép. */ public static final String ST_LEAVE = "LEAVE";
    /** Nghỉ lễ. */ public static final String ST_HOLIDAY = "HOLIDAY";
    /** Ngày nghỉ theo ca (cuối tuần). */ public static final String ST_OFF = "OFF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeCode;
    private String employeeName;
    private String department;

    /** Máy chấm công (văn phòng). Cùng mã nhưng khác máy là HAI người khác nhau. */
    private Long deviceId;
    private String deviceName;

    private LocalDate workDate;

    private Long shiftId;
    private String shiftName;

    private LocalDateTime firstIn;
    private LocalDateTime lastOut;

    private Integer lateMinutes = 0;
    private Integer earlyMinutes = 0;
    private Integer workedMinutes = 0;
    private Integer otMinutes = 0;

    /** Số công quy đổi: 1.0 / 0.5 / 0.0 */
    private Double workDays = 0.0;

    private String status = ST_ABSENT;
    private Boolean manualOverride = false;
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    /** Khóa định danh người: máy + mã (khớp Person.personKey). */
    public String personKey() {
        return (deviceId == null ? "-" : deviceId) + "|" + employeeCode;
    }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }

    public LocalDateTime getFirstIn() { return firstIn; }
    public void setFirstIn(LocalDateTime firstIn) { this.firstIn = firstIn; }

    public LocalDateTime getLastOut() { return lastOut; }
    public void setLastOut(LocalDateTime lastOut) { this.lastOut = lastOut; }

    public Integer getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(Integer lateMinutes) { this.lateMinutes = lateMinutes; }

    public Integer getEarlyMinutes() { return earlyMinutes; }
    public void setEarlyMinutes(Integer earlyMinutes) { this.earlyMinutes = earlyMinutes; }

    public Integer getWorkedMinutes() { return workedMinutes; }
    public void setWorkedMinutes(Integer workedMinutes) { this.workedMinutes = workedMinutes; }

    public Integer getOtMinutes() { return otMinutes; }
    public void setOtMinutes(Integer otMinutes) { this.otMinutes = otMinutes; }

    public Double getWorkDays() { return workDays; }
    public void setWorkDays(Double workDays) { this.workDays = workDays; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getManualOverride() { return manualOverride; }
    public void setManualOverride(Boolean manualOverride) { this.manualOverride = manualOverride; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
