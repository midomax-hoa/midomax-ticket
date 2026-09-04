package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {
    Optional<EmployeeShift> findByEmployeeCode(String employeeCode);

    /** Ca gán cho đúng NGƯỜI = (máy, mã); deviceId null khớp dòng chưa phân máy. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT e FROM EmployeeShift e WHERE e.employeeCode = :code " +
            "AND ((:deviceId IS NULL AND e.deviceId IS NULL) OR e.deviceId = :deviceId)")
    Optional<EmployeeShift> findByPerson(@org.springframework.data.repository.query.Param("code") String code,
                                         @org.springframework.data.repository.query.Param("deviceId") Long deviceId);
}
