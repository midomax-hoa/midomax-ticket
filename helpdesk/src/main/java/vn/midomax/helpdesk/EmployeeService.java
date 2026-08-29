package vn.midomax.helpdesk;

import java.util.List;
import java.util.Optional;

public interface EmployeeService {
    List<Employee> getAllEmployees();
    Optional<Employee> getEmployeeById(Long id);
    Employee saveEmployee(Employee employee);
    void deleteEmployee(Long id);
    
    List<Employee> searchAndFilter(String keyword, String department, String status);
    List<String> findAllDepartments();
    List<String> findAllStatuses();
}
