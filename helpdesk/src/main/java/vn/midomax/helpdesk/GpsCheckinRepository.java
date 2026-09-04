package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GpsCheckinRepository extends JpaRepository<GpsCheckin, Long> {

    List<GpsCheckin> findTop200ByOrderByPunchTimeDesc();

    /** Chống bấm liên tục: lần chấm gần nhất của một người. */
    List<GpsCheckin> findTop1ByEmployeeCodeAndDeviceIdOrderByPunchTimeDesc(String employeeCode, Long deviceId);

    /** Vai lan cham gan nhat cua mot nguoi — hien tren trang cham cong ca nhan. */
    List<GpsCheckin> findTop5ByEmployeeCodeAndDeviceIdOrderByPunchTimeDesc(String employeeCode, Long deviceId);

    List<GpsCheckin> findByPunchTimeBetweenOrderByPunchTimeDesc(LocalDateTime from, LocalDateTime to);

    /** Các lần chấm HÔM NAY của một người — nhân viên tự xem lại hình + vị trí ngay trên trang chấm. */
    List<GpsCheckin> findByEmployeeCodeAndDeviceIdAndPunchTimeBetweenOrderByPunchTimeDesc(
            String employeeCode, Long deviceId, LocalDateTime from, LocalDateTime to);

    /** Lịch sử chấm của MỘT người — trang soát mở theo từng người, không tải cả công ty. */
    List<GpsCheckin> findTop100ByEmployeeCodeAndDeviceIdOrderByPunchTimeDesc(String employeeCode, Long deviceId);

    /**
     * Tóm tắt theo người: (mã, máy, tên máy, số lần chấm, lần gần nhất).
     * Trang soát vẽ danh sách này trước — nhẹ vì không kèm ảnh nào.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT c.employeeCode, c.deviceId, c.deviceName, COUNT(c), MAX(c.punchTime) " +
        "FROM GpsCheckin c GROUP BY c.employeeCode, c.deviceId, c.deviceName " +
        "ORDER BY MAX(c.punchTime) DESC")
    List<Object[]> summarizeByPerson();
}
