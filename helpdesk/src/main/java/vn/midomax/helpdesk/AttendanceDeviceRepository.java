package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceDeviceRepository extends JpaRepository<AttendanceDevice, Long> {
    List<AttendanceDevice> findByActiveTrue();
}
