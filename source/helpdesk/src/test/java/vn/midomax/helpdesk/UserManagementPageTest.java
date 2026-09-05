package vn.midomax.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin page "Quản Lý User": the client-side filters (search, role, source) need their
 * inputs and per-row data attributes, and attendance code + office assignment must no longer
 * be offered in the role modal (HR does that on "Chấm Công Online").
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserManagementPageTest {

    private static final String EMAIL = "usermgmt.regress@midomax.vn";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    private DefaultOidcUser admin() {
        OidcIdToken idToken = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "s", "name", "Nguoi Kiem Thu", "email", "kiemthu@midomax.vn"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), idToken, "name");
    }

    private String loadPage() throws Exception {
        return mockMvc.perform(get("/admin/users").with(oidcLogin().oidcUser(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void pageHasSearchRoleSourceFiltersAndRowFilterAttributes() throws Exception {
        AppUser u = new AppUser(EMAIL, "User Mgmt Regress", "ROLE_IT");
        u.setAuthSource(AppUser.SOURCE_LOCAL);
        u.setEmployeeCode("4242");
        appUserRepository.save(u);

        String html = loadPage();

        assertThat(html)
                .contains("id=\"userSearch\"")
                .contains("id=\"roleFilter\"")
                .contains("id=\"sourceFilter\"");

        int at = html.indexOf(EMAIL);
        assertThat(at).as("row of the test user must be rendered").isPositive();
        String row = html.substring(Math.max(0, at - 1500), Math.min(html.length(), at + 500));
        assertThat(row)
                .contains("data-role=\"ROLE_IT\"")
                .contains("data-source=\"LOCAL\"")
                .contains("4242");
    }

    @Test
    void roleModalNoLongerAssignsAttendanceCodeOrOffice() throws Exception {
        String html = loadPage();

        assertThat(html)
                .doesNotContain("editUserEmpCode")
                .doesNotContain("editUserAttDevice");
    }
}
