package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentModuleAccessRepository extends JpaRepository<DepartmentModuleAccess, Long> {

    List<DepartmentModuleAccess> findByDepartment(String department);
}
