package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeCodeAndRequestDateBetween(String employeeCode, LocalDate from, LocalDate to);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<LeaveRequest> findByDepartmentAndStatusOrderByCreatedAtDesc(String department, String status);

    List<LeaveRequest> findByRequestDateBetweenOrderByRequestDateAsc(LocalDate from, LocalDate to);

    List<LeaveRequest> findTop30ByOrderByCreatedAtDesc();

    /** Đơn có khoảng ngày giao với [from, to] — dùng khi tính công và khi hiện trên lịch. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT r FROM LeaveRequest r WHERE r.requestDate <= :to " +
            "AND COALESCE(r.toDate, r.requestDate) >= :from ORDER BY r.requestDate")
    List<LeaveRequest> findOverlapping(@org.springframework.data.repository.query.Param("from") LocalDate from,
                                       @org.springframework.data.repository.query.Param("to") LocalDate to);
}
