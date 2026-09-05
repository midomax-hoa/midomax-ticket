package vn.midomax.helpdesk;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validation rules of POST /attendance/gps-permissions/assign, without Spring or a database.
 * The MockMvc + database twin is AttendanceGpsPermissionsTest.
 */
class AttendanceGpsPermissionControllerTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AttendanceDeviceRepository devices = mock(AttendanceDeviceRepository.class);
    private final AttendanceGpsPermissionController controller = new AttendanceGpsPermissionController(users, devices);

    private AppUser knownUser() {
        AppUser u = new AppUser("unit.gps@midomax.vn", "Unit GPS", "ROLE_USER");
        u.setId(7L);
        when(users.findById(7L)).thenReturn(Optional.of(u));
        return u;
    }

    private void knownDevice() {
        AttendanceDevice d = new AttendanceDevice();
        d.setId(3L);
        d.setName("188Bis");
        when(devices.findById(3L)).thenReturn(Optional.of(d));
    }

    private static Map<String, Object> payload(Object id, Object code, Object deviceId) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("employeeCode", code);
        p.put("deviceId", deviceId);
        return p;
    }

    @Test
    void codeAndOfficeMustComeTogether() {
        AppUser u = knownUser();
        knownDevice();

        assertThat(controller.assign(payload(7, "777", "")).get("ok")).isEqualTo(false);
        assertThat(controller.assign(payload(7, "", "3")).get("ok")).isEqualTo(false);
        assertThat(controller.assign(payload(7, null, 3)).get("ok")).isEqualTo(false);

        verify(users, never()).save(any());
        assertThat(u.getEmployeeCode()).isNull();
        assertThat(u.getAttendanceDeviceId()).isNull();
    }

    @Test
    void unknownUserOrOfficeIsRejected() {
        knownUser();

        assertThat(controller.assign(payload(99, "777", "3")).get("ok")).isEqualTo(false);
        assertThat(controller.assign(payload(7, "777", "42")).get("ok")).isEqualTo(false);
        assertThat(controller.assign(payload(7, "777", "abc")).get("ok")).isEqualTo(false);
        assertThat(controller.assign(payload("x", "777", "3")).get("ok")).isEqualTo(false);

        verify(users, never()).save(any());
    }

    @Test
    void validPairIsSavedTrimmedAndEmptyPairUnassigns() {
        AppUser u = knownUser();
        knownDevice();

        Map<String, Object> ok = controller.assign(payload("7", " 777 ", "3"));
        assertThat(ok.get("ok")).isEqualTo(true);
        assertThat(String.valueOf(ok.get("message"))).contains("777").contains("188Bis");
        assertThat(u.getEmployeeCode()).isEqualTo("777");
        assertThat(u.getAttendanceDeviceId()).isEqualTo(3L);

        assertThat(controller.assign(payload(7, "", "")).get("ok")).isEqualTo(true);
        assertThat(u.getEmployeeCode()).isNull();
        assertThat(u.getAttendanceDeviceId()).isNull();

        verify(users, times(2)).save(u);
    }

    @Test
    void codeLongerThanTheColumnIsRejected() {
        knownUser();
        knownDevice();

        assertThat(controller.assign(payload(7, "123456789012345678901", "3")).get("ok")).isEqualTo(false);
        verify(users, never()).save(any());
    }
}
