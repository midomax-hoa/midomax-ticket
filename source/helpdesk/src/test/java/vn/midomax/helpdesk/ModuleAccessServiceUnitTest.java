package vn.midomax.helpdesk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test (no Spring context, no database) for the module rules of ModuleAccessService.
 * The database-backed twin is ModuleAccessServiceTest.
 */
class ModuleAccessServiceUnitTest {

    private static final String EMAIL = "unit.regress@midomax.vn";

    private final DepartmentModuleAccessRepository accessRepository = mock(DepartmentModuleAccessRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    /** One live list returned on every call, so a test can edit it like a DBA editing the table. */
    private final List<DepartmentModuleAccess> matrixRows = new ArrayList<>();
    private final ModuleAccessService service = new ModuleAccessService();
    private AppUser user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "accessRepository", accessRepository);
        ReflectionTestUtils.setField(service, "appUserRepository", appUserRepository);
        user = new AppUser(EMAIL, "Unit Regress", "ROLE_USER");
        user.setDepartment("B2B");
        when(appUserRepository.findAll()).thenReturn(List.of(user));
        when(accessRepository.findAll()).thenReturn(matrixRows);
    }

    private static Authentication loginAs(String email, String authority) {
        return new UsernamePasswordAuthenticationToken(email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
    }

    @Test
    void personalGrantAddsOnTopOfMatrixAndCanBeRevoked() {
        matrixRows.add(new DepartmentModuleAccess("B2B", "ASSETS"));
        assertThat(service.modulesOf(loginAs(EMAIL, "ROLE_USER"))).containsExactlyInAnyOrder("ASSETS");

        user.setPersonalModules(EnumSet.of(AppModule.REPORTS));
        assertThat(service.modulesOf(loginAs(EMAIL, "ROLE_USER"))).containsExactlyInAnyOrder("ASSETS", "REPORTS");

        user.setPersonalModules(EnumSet.noneOf(AppModule.class));
        assertThat(service.modulesOf(loginAs(EMAIL, "ROLE_USER"))).containsExactlyInAnyOrder("ASSETS");
    }

    @Test
    void matrixGrantCannotBeRevokedPerUser() {
        // The additive rule: while the department row exists, an unticked personal grant changes nothing.
        // That is why "REPORTS for every department" made the per-user checkbox look broken.
        matrixRows.add(new DepartmentModuleAccess("B2B", "REPORTS"));
        assertThat(service.modulesOf(loginAs(EMAIL, "ROLE_USER"))).contains("REPORTS");
    }

    @Test
    void matrixRowsRemovedBetweenCallsStopGrantingWithoutRestart() {
        matrixRows.add(new DepartmentModuleAccess("B2B", "REPORTS"));
        assertThat(service.modulesOf(loginAs(EMAIL, "ROLE_USER"))).contains("REPORTS");

        matrixRows.clear(); // DELETE FROM department_module_access WHERE module='REPORTS'
        assertThat(service.modulesOf(loginAs(EMAIL, "ROLE_USER"))).doesNotContain("REPORTS");
    }

    @Test
    void builtInRoleModulesAreUnchanged() {
        assertThat(service.modulesOf(loginAs("admin@midomax.vn", "ROLE_ADMIN")))
                .containsExactlyInAnyOrder("NETWORK", "ASSETS", "FINANCE", "REPORTS", "HR");
        assertThat(service.modulesOf(loginAs(EMAIL, "ROLE_IT"))).contains("REPORTS");
        assertThat(service.modulesOf(loginAs(EMAIL, "GROUP_HELPDESK"))).contains("ASSETS");
        assertThat(service.modulesOf(null)).isEmpty();
    }
}
