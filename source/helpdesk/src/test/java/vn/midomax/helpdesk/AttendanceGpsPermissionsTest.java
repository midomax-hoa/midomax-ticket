package vn.midomax.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HR page "Chấm Công Online": inline assignment of attendance code + office (moved here from
 * the admin role modal) and the client-side search/pagination hooks the template must keep.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AttendanceGpsPermissionsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AttendanceDeviceRepository deviceRepository;

    /** ROLE_ADMIN owns every module, so the HR-only interceptor lets the request through. */
    private DefaultOidcUser admin() {
        OidcIdToken idToken = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "s", "name", "Nguoi Kiem Thu", "email", "kiemthu@midomax.vn"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), idToken, "name");
    }

    private AppUser newUser() {
        return appUserRepository.save(new AppUser("gpsperm.regress@midomax.vn", "GPS Perm Regress", "ROLE_USER"));
    }

    private AttendanceDevice newDevice() {
        AttendanceDevice d = new AttendanceDevice();
        d.setName("Regress Office");
        d.setIpAddress("10.255.255.1");
        return deviceRepository.save(d);
    }

    private ResultActions assign(Long userId, String code, String deviceId) throws Exception {
        String body = "{\"id\":" + userId + ",\"employeeCode\":\"" + code + "\",\"deviceId\":\"" + deviceId + "\"}";
        return mockMvc.perform(post("/attendance/gps-permissions/assign")
                .with(oidcLogin().oidcUser(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void pageRendersInlineAssignControlsAndPaginationHooks() throws Exception {
        AppUser u = newUser();
        newDevice();

        String html = mockMvc.perform(get("/attendance/gps-permissions").with(oidcLogin().oidcUser(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("id=\"qFilter\"")
                .contains("id=\"pager\"")
                .contains("id=\"pageInfo\"")
                .contains("inp-code")
                .contains("inp-device")
                .contains("btn-save-assign")
                .contains("data-uid=\"" + u.getId() + "\"")
                .contains(">Regress Office<");
    }

    @Test
    void assignRejectsCodeWithoutOfficeAndOfficeWithoutCode() throws Exception {
        AppUser u = newUser();
        AttendanceDevice d = newDevice();

        assign(u.getId(), "777", "").andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));
        assign(u.getId(), "", String.valueOf(d.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));

        AppUser reloaded = appUserRepository.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getEmployeeCode()).isNull();
        assertThat(reloaded.getAttendanceDeviceId()).isNull();
    }

    @Test
    void assignRejectsUnknownOfficeAndUnknownUser() throws Exception {
        AppUser u = newUser();

        assign(u.getId(), "777", "999999999").andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));
        assign(999999999L, "777", "1").andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));

        assertThat(appUserRepository.findById(u.getId()).orElseThrow().getEmployeeCode()).isNull();
    }

    @Test
    void assignSavesCodeWithOfficeAndClearsBothWhenEmpty() throws Exception {
        AppUser u = newUser();
        AttendanceDevice d = newDevice();

        assign(u.getId(), " 777 ", String.valueOf(d.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        AppUser reloaded = appUserRepository.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getEmployeeCode()).isEqualTo("777");
        assertThat(reloaded.getAttendanceDeviceId()).isEqualTo(d.getId());

        assign(u.getId(), "", "").andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        reloaded = appUserRepository.findById(u.getId()).orElseThrow();
        assertThat(reloaded.getEmployeeCode()).isNull();
        assertThat(reloaded.getAttendanceDeviceId()).isNull();
    }
}
