package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalTime;

/**
 * Ca làm việc. Mỗi nhân viên được gán một ca (xem {@link EmployeeShift}); nếu chưa
 * gán thì dùng ca được đánh dấu mặc định.
 */
@Entity
@Table(name = "attendance_shifts")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;

    private LocalTime startTime;
    private LocalTime endTime;

    private Integer breakMinutes = 60;       // Nghỉ trưa, trừ ra khỏi giờ công
    private Integer lateGraceMinutes = 5;    // Trễ trong ngưỡng này không tính đi muộn
    private Integer earlyGraceMinutes = 5;   // Tương tự cho về sớm
    private Integer otStartAfterMinutes = 30; // Ở lại quá ngưỡng này mới tính tăng ca

    /** Ca đêm: giờ ra rơi sang ngày hôm sau. */
    private Boolean crossMidnight = false;

    /** Các thứ đi làm, theo ISO (2=Thứ 2 ... 7=Thứ 7, 1=Chủ nhật). Ví dụ "2,3,4,5,6,7". */
    private String workingWeekdays = "2,3,4,5,6,7";

    private Boolean isDefault = false;
    private Boolean active = true;

    /** Số phút làm việc chuẩn của ca, đã trừ giờ nghỉ. Dùng làm mẫu số khi quy ra công. */
    @Transient
    public int getStandardMinutes() {
        if (startTime == null || endTime == null) return 0;
        int start = startTime.toSecondOfDay() / 60;
        int end = endTime.toSecondOfDay() / 60;
        if (end <= start) end += 24 * 60; // ca đêm
        int total = end - start - (breakMinutes == null ? 0 : breakMinutes);
        return Math.max(total, 0);
    }

    @Transient
    public boolean isWorkingDay(java.time.DayOfWeek dow) {
        if (workingWeekdays == null || workingWeekdays.isBlank()) return true;
        // Chủ nhật là 1, thứ 2..7 là 2..7 — khớp cách gọi thứ trong tiếng Việt.
        int viDay = dow == java.time.DayOfWeek.SUNDAY ? 1 : dow.getValue() + 1;
        for (String part : workingWeekdays.split(",")) {
            if (part.trim().equals(String.valueOf(viDay))) return true;
        }
        return false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(Integer breakMinutes) { this.breakMinutes = breakMinutes; }

    public Integer getLateGraceMinutes() { return lateGraceMinutes; }
    public void setLateGraceMinutes(Integer lateGraceMinutes) { this.lateGraceMinutes = lateGraceMinutes; }

    public Integer getEarlyGraceMinutes() { return earlyGraceMinutes; }
    public void setEarlyGraceMinutes(Integer earlyGraceMinutes) { this.earlyGraceMinutes = earlyGraceMinutes; }

    public Integer getOtStartAfterMinutes() { return otStartAfterMinutes; }
    public void setOtStartAfterMinutes(Integer otStartAfterMinutes) { this.otStartAfterMinutes = otStartAfterMinutes; }

    public Boolean getCrossMidnight() { return crossMidnight; }
    public void setCrossMidnight(Boolean crossMidnight) { this.crossMidnight = crossMidnight; }

    public String getWorkingWeekdays() { return workingWeekdays; }
    public void setWorkingWeekdays(String workingWeekdays) { this.workingWeekdays = workingWeekdays; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
