package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceDeviceUserRepository extends JpaRepository<AttendanceDeviceUser, Long> {

    Optional<AttendanceDeviceUser> findByEmployeeCode(String employeeCode);

    List<AttendanceDeviceUser> findAllByOrderByFullNameAsc();

    /** Người có trên MỘT máy chấm công — dùng lọc bảng công theo văn phòng. */
    List<AttendanceDeviceUser> findByDeviceIdOrderByFullNameAsc(Long deviceId);
}
