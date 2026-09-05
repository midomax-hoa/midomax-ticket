package vn.midomax.helpdesk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Một dòng = một ô được tick trong ma trận "Phân hệ theo phòng ban":
 * phòng ban `department` được quyền vào phân hệ `module`.
 */
@Entity
@Table(name = "department_module_access",
       uniqueConstraints = @UniqueConstraint(columnNames = {"department", "module"}))
public class DepartmentModuleAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã phòng ban (Department.name()): B2B, ITD, TCKT... */
    @Column(nullable = false, length = 10)
    private String department;

    /** Mã phân hệ (AppModule.name()): ASSETS, FINANCE, HR... */
    @Column(nullable = false, length = 20)
    private String module;

    public DepartmentModuleAccess() {
    }

    public DepartmentModuleAccess(String department, String module) {
        this.department = department;
        this.module = module;
    }

    public Long getId() {
        return id;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }
}
