package vn.midomax.helpdesk;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Một lần chấm công bằng GPS trên điện thoại.
 *
 * Lần chấm hợp lệ sẽ ĐỒNG THỜI ghi một dòng attendance_log với đúng (máy, mã NV)
 * của người đó — phần tính công dùng dòng log ấy y như lần quét vân tay. Bảng này
 * giữ phần còn lại mà log không chứa: toạ độ, độ lệch, IP, ảnh selfie — để nhân sự
 * soát gian lận (GPS trên web có thể bị giả, xem GpsCheckinService).
 */
@Entity
@Table(name = "gps_checkins")
public class GpsCheckin {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", length = 20)
    private String employeeCode;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    /** Email tài khoản bấm nút — đối chiếu được ai bấm hộ ai. */
    @Column(name = "user_email", length = 150)
    private String userEmail;

    @Column(name = "punch_time")
    private LocalDateTime punchTime;

    private Double latitude;
    private Double longitude;

    /** Độ chính xác trình duyệt báo (mét). Số quá đẹp hoặc quá tệ đều đáng ngờ. */
    @Column(name = "accuracy_m")
    private Double accuracyM;

    /** Khoảng cách tới văn phòng lúc chấm (mét). Null nếu chấm tự do. */
    @Column(name = "distance_m")
    private Double distanceM;

    /** Chấm theo chế độ công tác (không so bán kính). */
    @Column(name = "free_location")
    private Boolean freeLocation = false;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    /** Tên file selfie trong thư mục lưu — xem qua /attendance/gps/selfie/{id}. */
    @Column(name = "selfie_file", length = 200)
    private String selfieFile;

    /** Địa chỉ dịch ngược từ toạ độ (OpenStreetMap) — chủ yếu cho lần chấm công tác. */
    @Column(name = "location_name", length = 300)
    private String locationName;

    /** Loại lần chấm: "IN" (vào) / "OUT" (ra). Null = dữ liệu cũ trước khi tách nút. */
    @Column(name = "punch_type", length = 8)
    private String punchType;

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getPunchType() { return punchType; }
    public void setPunchType(String punchType) { this.punchType = punchType; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public LocalDateTime getPunchTime() { return punchTime; }
    public void setPunchTime(LocalDateTime punchTime) { this.punchTime = punchTime; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getAccuracyM() { return accuracyM; }
    public void setAccuracyM(Double accuracyM) { this.accuracyM = accuracyM; }

    public Double getDistanceM() { return distanceM; }
    public void setDistanceM(Double distanceM) { this.distanceM = distanceM; }

    public boolean isFreeLocation() { return Boolean.TRUE.equals(freeLocation); }
    public void setFreeLocation(Boolean freeLocation) { this.freeLocation = freeLocation; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getSelfieFile() { return selfieFile; }
    public void setSelfieFile(String selfieFile) { this.selfieFile = selfieFile; }

    // ===== Tiện ích cho giao diện =====

    public String getPunchTimeStr() {
        return punchTime == null ? "" : punchTime.format(DT_FMT);
    }

    /** Link mở Google Maps đúng điểm chấm — không cần API key. */
    public String getMapUrl() {
        if (latitude == null || longitude == null) return null;
        return "https://www.google.com/maps?q=" + latitude + "," + longitude;
    }
}
