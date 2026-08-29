package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    @Query("SELECT e FROM Employee e WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:department IS NULL OR :department = '' OR e.department = :department) AND " +
           "(:status IS NULL OR :status = '' OR e.workStatus = :status)")
    List<Employee> searchAndFilter(@Param("keyword") String keyword, 
                                   @Param("department") String department, 
                                   @Param("status") String status);

    @Query("SELECT DISTINCT e.department FROM Employee e WHERE e.department IS NOT NULL AND e.department != ''")
    List<String> findAllDepartments();

    @Query("SELECT DISTINCT e.workStatus FROM Employee e WHERE e.workStatus IS NOT NULL AND e.workStatus != ''")
    List<String> findAllStatuses();
}
