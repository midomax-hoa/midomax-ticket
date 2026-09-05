package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    boolean existsByEmployeeCodeAndPunchTimeAndDeviceId(String employeeCode, LocalDateTime punchTime, Long deviceId);

    @Query("SELECT MAX(l.punchTime) FROM AttendanceLog l WHERE l.deviceId = :deviceId")
    LocalDateTime findLastPunchTimeByDevice(@Param("deviceId") Long deviceId);

    /**
     * Nạp sẵn mã NV + giờ quét đã có của một máy để lọc trùng trong bộ nhớ.
     * Máy luôn trả về toàn bộ bộ nhớ (hàng chục nghìn dòng) nên hỏi từng dòng một
     * là quá chậm; lấy một lần rồi so trong Set.
     */
    @Query("SELECT l.employeeCode, l.punchTime FROM AttendanceLog l WHERE l.deviceId = :deviceId")
    List<Object[]> findKeysByDevice(@Param("deviceId") Long deviceId);

    @Query("SELECT l FROM AttendanceLog l WHERE l.punchTime >= :from AND l.punchTime < :to ORDER BY l.employeeCode, l.punchTime")
    List<AttendanceLog> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT l FROM AttendanceLog l WHERE l.employeeCode = :code AND l.punchTime >= :from AND l.punchTime < :to ORDER BY l.punchTime")
    List<AttendanceLog> findByEmployeeInRange(@Param("code") String code,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * Lần quét của MỘT người = (máy, mã): các văn phòng dùng chung dải mã nên phải
     * lọc theo máy, không thì lần quét của người trùng mã ở văn phòng khác lẫn vào.
     * deviceId null = mọi máy (người chưa gắn máy, giữ hành vi cũ).
     */
    @Query("SELECT l FROM AttendanceLog l WHERE l.employeeCode = :code " +
           "AND (:deviceId IS NULL OR l.deviceId = :deviceId) " +
           "AND l.punchTime >= :from AND l.punchTime < :to ORDER BY l.punchTime")
    List<AttendanceLog> findByPersonInRange(@Param("code") String code,
                                            @Param("deviceId") Long deviceId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);
}
