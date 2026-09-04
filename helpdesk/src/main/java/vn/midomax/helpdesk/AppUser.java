package vn.midomax.helpdesk;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "app_users")
public class AppUser {

    /** Tài khoản tạo trực tiếp trong hệ thống, đăng nhập bằng form + mật khẩu. */
    public static final String SOURCE_LOCAL = "LOCAL";
    /** Tài khoản đồng bộ từ Microsoft 365, đăng nhập bằng nút Microsoft. */
    public static final String SOURCE_M365 = "M365";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String fullName;
    private String role; // e.g., "ROLE_USER", "ROLE_ADMIN", "ROLE_IT"
    private String password;

    /** SOURCE_LOCAL hoặc SOURCE_M365. Xem isLocal() trước khi đọc trực tiếp. */
    @Column(name = "auth_source", length = 20)
    private String authSource;

    /** Mã phòng ban (Department.name(): B2B, ITD, MKT...). Null = chưa phân. */
    @Column(name = "department", length = 10)
    private String department;

    /**
     * Mã chấm công trên máy ZK (trùng Employee.employeeCode). Admin nhập tay ở
     * trang Phân quyền User; có mã thì nhân viên tự xem được lịch chấm công của mình.
     */
    @Column(name = "employee_code", length = 20)
    private String employeeCode;

    /**
     * Máy chấm công (văn phòng) của mã trên. BẮT BUỘC chọn kèm mã vì các văn phòng
     * dùng chung dải mã — mã 101 ở 2 máy là 2 người khác nhau.
     */
    @Column(name = "attendance_device_id")
    private Long attendanceDeviceId;

    /**
     * Được chấm công bằng GPS trên điện thoại (kèm selfie). Cấp theo từng người —
     * mặc định tắt, ai vẫn quẹt máy vân tay thì không bật.
     */
    @Column(name = "gps_checkin_allowed")
    private Boolean gpsCheckinAllowed = false;

    /**
     * Đi công tác: được chấm GPS ở BẤT KỲ đâu (không so bán kính văn phòng),
     * hệ thống vẫn lưu toạ độ để nhân sự xem lại. Chỉ có nghĩa khi cờ trên bật.
     */
    @Column(name = "gps_free_location")
    private Boolean gpsFreeLocation = false;

    public boolean isGpsCheckinAllowed() { return Boolean.TRUE.equals(gpsCheckinAllowed); }
    public void setGpsCheckinAllowed(Boolean v) { this.gpsCheckinAllowed = v; }

    public boolean isGpsFreeLocation() { return Boolean.TRUE.equals(gpsFreeLocation); }
    public void setGpsFreeLocation(Boolean v) { this.gpsFreeLocation = v; }

    /**
     * Trưởng phòng: thấy được ticket của mọi nhân viên CÙNG phòng ban
     * (chỉ xem — quyền sửa/phân công vẫn theo role). Null coi như false.
     */
    @Column(name = "dept_head")
    private Boolean deptHead;

    /**
     * Phân hệ cấp RIÊNG cho user này, cộng thêm vào quyền từ vai trò và ma trận
     * phòng ban. Dùng khi một người cần vào phân hệ mà cả phòng họ không cần.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_modules", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "module", length = 20)
    @Enumerated(EnumType.STRING)
    private Set<AppModule> personalModules = java.util.EnumSet.noneOf(AppModule.class);

    /**
     * Nhóm chuyên môn IT. Chỉ có ý nghĩa khi role là ROLE_IT — không cấp thêm quyền,
     * chỉ dùng để phân loại và định tuyến ticket.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_it_groups", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "it_group", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<ItGroup> itGroups = EnumSet.noneOf(ItGroup.class);

    public AppUser() {
    }

    public AppUser(String email, String fullName, String role) {
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAuthSource() {
        return authSource;
    }

    public void setAuthSource(String authSource) {
        this.authSource = authSource;
    }

    /**
     * Các bản ghi tạo trước khi có cột auth_source sẽ có giá trị null; khi đó suy ra
     * từ mật khẩu, vì user đồng bộ từ 365 không bao giờ được đặt mật khẩu.
     */
    public boolean isLocal() {
        if (authSource != null) {
            return SOURCE_LOCAL.equals(authSource);
        }
        return password != null && !password.isEmpty();
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    /** Enum phòng ban, null nếu chưa phân hoặc mã không hợp lệ. Template dùng để lấy label. */
    public Department getDepartmentEnum() {
        return Department.fromString(department);
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public Long getAttendanceDeviceId() {
        return attendanceDeviceId;
    }

    public void setAttendanceDeviceId(Long attendanceDeviceId) {
        this.attendanceDeviceId = attendanceDeviceId;
    }

    public boolean isDeptHead() {
        return Boolean.TRUE.equals(deptHead);
    }

    public void setDeptHead(Boolean deptHead) {
        this.deptHead = deptHead;
    }

    public Set<AppModule> getPersonalModules() {
        return personalModules;
    }

    public void setPersonalModules(Set<AppModule> personalModules) {
        this.personalModules.clear();
        if (personalModules != null) {
            this.personalModules.addAll(personalModules);
        }
    }

    public Set<ItGroup> getItGroups() {
        return itGroups;
    }

    public void setItGroups(Set<ItGroup> itGroups) {
        this.itGroups.clear();
        if (itGroups != null) {
            this.itGroups.addAll(itGroups);
        }
    }
}

