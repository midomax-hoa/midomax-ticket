package vn.midomax.helpdesk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Đơn xin nghỉ phép / xin đi trễ, gửi từ lịch chấm công cá nhân.
 *
 * Luồng duyệt 2 cấp:
 *   PENDING (mới gửi) -> HEAD_APPROVED (trưởng phòng xác nhận) -> APPROVED (nhân sự chốt)
 *   Bất kỳ cấp nào cũng có thể REJECTED kèm lý do.
 * Cuối tháng nhân sự tổng kết theo các đơn APPROVED.
 */
@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    public static final String TYPE_LEAVE = "LEAVE";           // Phiếu đăng ký nghỉ (QĐ-NSHT.MDM-03.03)
    public static final String TYPE_SUPPLEMENT = "SUPPLEMENT"; // Giấy bổ sung thông tin chấm công (QĐ-NSHT.MDM-03.01)

    public static final String ST_PENDING = "PENDING";
    public static final String ST_HEAD_APPROVED = "HEAD_APPROVED";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã chấm công của người xin (AppUser.employeeCode). */
    @Column(nullable = false, length = 20)
    private String employeeCode;

    /** Máy chấm công (văn phòng) của người xin — các văn phòng trùng dải mã. */
    private Long deviceId;

    /** Username người xin (để đối chiếu tài khoản). */
    @Column(nullable = false, length = 100)
    private String requesterName;

    /** Tên hiển thị (lấy từ AppUser.fullName lúc gửi, cho trang duyệt dễ đọc). */
    @Column(length = 150)
    private String requesterFullName;

    /** Mã phòng ban của người xin lúc gửi đơn — trưởng phòng lọc theo cột này. */
    @Column(length = 10)
    private String department;

    /** TYPE_LEAVE hoặc TYPE_SUPPLEMENT. */
    @Column(nullable = false, length = 12)
    private String type;

    /** Từ ngày (ngày bắt đầu nghỉ / bổ sung công). */
    @Column(nullable = false)
    private LocalDate requestDate;

    /** Đến hết ngày. Null coi như một ngày (= requestDate). */
    private LocalDate toDate;

    /**
     * Loại phép của Phiếu đăng ký nghỉ: CHE_DO (phép theo chế độ), NAM (phép năm),
     * BHXH (phép chính sách BHXH), KHONG_LUONG (nghỉ không lương). Null với đơn bổ sung công.
     */
    @Column(length = 15)
    private String leaveType;

    /** Số thời gian của Giấy bổ sung công: FULL (cả ngày), MORNING (sáng), AFTERNOON (chiều). */
    @Column(length = 10)
    private String duration;

    /** Tổng số ngày (ví dụ 1, 0.5, 2). */
    private Double totalDays;

    /** Người hỗ trợ công việc trong thời gian nghỉ (Phiếu đăng ký nghỉ). */
    @Column(length = 150)
    private String supporter;

    /** SĐT liên lạc của người đề nghị. */
    @Column(length = 20)
    private String phone;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = ST_PENDING;

    private String headBy;          // trưởng phòng đã xác nhận
    private LocalDateTime headAt;
    private String hrBy;            // nhân sự đã chốt
    private LocalDateTime hrAt;
    private String rejectBy;
    @Column(length = 500)
    private String rejectReason;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public String getRequesterFullName() { return requesterFullName; }
    public void setRequesterFullName(String requesterFullName) { this.requesterFullName = requesterFullName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDate getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDate requestDate) { this.requestDate = requestDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHeadBy() { return headBy; }
    public void setHeadBy(String headBy) { this.headBy = headBy; }
    public LocalDateTime getHeadAt() { return headAt; }
    public void setHeadAt(LocalDateTime headAt) { this.headAt = headAt; }
    public String getHrBy() { return hrBy; }
    public void setHrBy(String hrBy) { this.hrBy = hrBy; }
    public LocalDateTime getHrAt() { return hrAt; }
    public void setHrAt(LocalDateTime hrAt) { this.hrAt = hrAt; }
    public String getRejectBy() { return rejectBy; }
    public void setRejectBy(String rejectBy) { this.rejectBy = rejectBy; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public Double getTotalDays() { return totalDays; }
    public void setTotalDays(Double totalDays) { this.totalDays = totalDays; }
    public String getSupporter() { return supporter; }
    public void setSupporter(String supporter) { this.supporter = supporter; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    /** Ngày kết thúc thực tế (đơn một ngày thì bằng ngày bắt đầu). */
    public LocalDate getEffectiveToDate() {
        return toDate != null ? toDate : requestDate;
    }

    /** Nhãn loại đơn cho template. */
    public String getTypeLabel() {
        return TYPE_SUPPLEMENT.equals(type) ? "Bổ sung công" : "Nghỉ phép";
    }

    /** Nhãn loại phép (Phiếu đăng ký nghỉ). */
    public String getLeaveTypeLabel() {
        if (leaveType == null) return "";
        switch (leaveType) {
            case "CHE_DO": return "Phép theo chế độ";
            case "BHXH": return "Phép chính sách BHXH";
            case "KHONG_LUONG": return "Nghỉ không lương";
            default: return "Phép năm";
        }
    }

    /** Nhãn số thời gian (Giấy bổ sung công). */
    public String getDurationLabel() {
        if (duration == null) return "";
        switch (duration) {
            case "MORNING": return "Sáng";
            case "AFTERNOON": return "Chiều";
            default: return "Cả ngày";
        }
    }

    /** Nhãn trạng thái cho template. */
    public String getStatusLabel() {
        switch (status) {
            case ST_HEAD_APPROVED: return "Trưởng bộ phận đã duyệt — chờ phòng NSHT";
            case ST_APPROVED: return "Đã duyệt";
            case ST_REJECTED: return "Từ chối";
            default: return "Chờ trưởng bộ phận duyệt";
        }
    }
}
