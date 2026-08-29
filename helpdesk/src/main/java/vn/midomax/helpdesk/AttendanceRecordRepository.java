package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeCodeAndWorkDate(String employeeCode, LocalDate workDate);

    /** Tra bản ghi theo (máy, mã, ngày) — deviceId null khớp dòng chưa gắn máy (dữ liệu cũ). */
    @Query("SELECT r FROM AttendanceRecord r WHERE r.employeeCode = :code AND r.workDate = :day " +
           "AND ((:deviceId IS NULL AND r.deviceId IS NULL) OR r.deviceId = :deviceId)")
    Optional<AttendanceRecord> findByPersonAndWorkDate(@Param("code") String code,
                                                       @Param("day") LocalDate day,
                                                       @Param("deviceId") Long deviceId);

    /** Bản ghi CŨ chưa gắn máy của một mã — dùng làm phương án dự phòng khi hiển thị. */
    @Query("SELECT r FROM AttendanceRecord r WHERE r.employeeCode = :code AND r.deviceId IS NULL " +
           "AND r.workDate BETWEEN :from AND :to ORDER BY r.workDate")
    List<AttendanceRecord> findLegacyByCodeInRange(@Param("code") String code,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    /** Bản ghi của một người (máy + mã) trong khoảng; deviceId null = mọi máy (tương thích cũ). */
    @Query("SELECT r FROM AttendanceRecord r WHERE r.employeeCode = :code " +
           "AND (:deviceId IS NULL OR r.deviceId = :deviceId) " +
           "AND r.workDate BETWEEN :from AND :to ORDER BY r.workDate")
    List<AttendanceRecord> findByPersonInRange(@Param("code") String code,
                                               @Param("deviceId") Long deviceId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    @Query("SELECT r FROM AttendanceRecord r WHERE r.workDate BETWEEN :from AND :to ORDER BY r.employeeCode, r.workDate")
    List<AttendanceRecord> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT r FROM AttendanceRecord r WHERE r.employeeCode = :code AND r.workDate BETWEEN :from AND :to ORDER BY r.workDate")
    List<AttendanceRecord> findByEmployeeInRange(@Param("code") String code,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);
}
