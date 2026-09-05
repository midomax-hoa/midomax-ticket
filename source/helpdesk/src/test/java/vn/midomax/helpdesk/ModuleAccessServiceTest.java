package vn.midomax.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module access is the SUM of role + department matrix + personal grants, never subtractive.
 * These tests pin two things: a personal grant only decides anything when the department
 * matrix does not already grant the module, and matrix rows changed directly in the database
 * take effect immediately (the old in-memory cache needed an app restart or a re-save from
 * the admin UI before a "DELETE FROM department_module_access" was noticed).
 */
@SpringBootTest
@Transactional
class ModuleAccessServiceTest {

    private static final String EMAIL = "modaccess.regress@midomax.vn";
    private static final String DEPT = "B2B";

    @Autowired
    private ModuleAccessService service;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DepartmentModuleAccessRepository accessRepository;

    private AppUser plainUserInDept() {
        AppUser u = new AppUser(EMAIL, "Module Access Regress", "ROLE_USER");
        u.setDepartment(DEPT);
        return appUserRepository.save(u);
    }

    private Authentication login() {
        return new UsernamePasswordAuthenticationToken(EMAIL, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void personalGrantDecidesOnlyWhenMatrixDoesNotGrantTheModule() {
        service.saveMatrix(Map.of(DEPT, Set.of("ASSETS")));
        AppUser u = plainUserInDept();

        assertThat(service.modulesOf(login())).contains("ASSETS").doesNotContain("REPORTS");

        u.setPersonalModules(EnumSet.of(AppModule.REPORTS));
        appUserRepository.save(u);
        assertThat(service.modulesOf(login())).contains("REPORTS");

        u.setPersonalModules(EnumSet.noneOf(AppModule.class));
        appUserRepository.save(u);
        assertThat(service.modulesOf(login())).doesNotContain("REPORTS");
    }

    @Test
    void matrixRowsDeletedDirectlyInDatabaseStopGrantingImmediately() {
        service.saveMatrix(Map.of(DEPT, Set.of("REPORTS")));
        plainUserInDept();
        assertThat(service.modulesOf(login())).contains("REPORTS");

        // Same effect as a DBA running "DELETE FROM department_module_access WHERE module='REPORTS'"
        accessRepository.deleteAll(accessRepository.findByDepartment(DEPT));
        accessRepository.flush();

        assertThat(service.modulesOf(login())).doesNotContain("REPORTS");
    }
}
