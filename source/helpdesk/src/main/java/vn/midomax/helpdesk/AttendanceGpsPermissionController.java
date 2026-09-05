package vn.midomax.helpdesk;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * HR page "Chấm Công Online" (Quản Lý Nhân Sự > Chấm Công Online): grant or revoke GPS
 * check-in in bulk, and assign the attendance code + office (device) inline per row.
 *
 * Lives under /attendance, so ModuleAccessInterceptor only lets users with the HR module in.
 * Split out of AttendanceController (calendar, devices, shifts, leave requests) to keep it small.
 */
@Controller
@RequestMapping("/attendance/gps-permissions")
public class AttendanceGpsPermissionController {

    /** Width of column app_users.employee_code. */
    private static final int MAX_EMPLOYEE_CODE_LENGTH = 20;

    private final AppUserRepository appUserRepository;
    private final AttendanceDeviceRepository deviceRepo;

    public AttendanceGpsPermissionController(AppUserRepository appUserRepository,
                                             AttendanceDeviceRepository deviceRepo) {
        this.appUserRepository = appUserRepository;
        this.deviceRepo = deviceRepo;
    }

    @GetMapping
    public String page(Model model) {
        List<AppUser> users = new ArrayList<>(appUserRepository.findAll());
        users.sort(Comparator.comparing(
                u -> u.getFullName() != null ? u.getFullName() : (u.getEmail() != null ? u.getEmail() : ""),
                String.CASE_INSENSITIVE_ORDER));
        model.addAttribute("users", users);
        // Every device (office), including inactive ones, for the per-row office select
        model.addAttribute("devices", deviceRepo.findAll());
        return "attendance-gps-permissions";
    }

    /** Bulk toggle: action = office | free | deny for the given ids. */
    @PostMapping("/bulk")
    @ResponseBody
    public Map<String, Object> bulk(@RequestBody Map<String, Object> payload) {
        Object idsObj = payload.get("ids");
        String action = String.valueOf(payload.get("action"));
        int changed = 0;
        if (idsObj instanceof List<?> ids) {
            for (Object o : ids) {
                AppUser u = findUser(o);
                if (u == null) continue;
                // Three explicit states so HR never has to reason about the two flags:
                // office = check in within the office radius; free = anywhere (business trip,
                // implies check-in allowed); deny = online check-in off.
                switch (action) {
                    case "office" -> { u.setGpsCheckinAllowed(true); u.setGpsFreeLocation(false); }
                    case "free" -> { u.setGpsCheckinAllowed(true); u.setGpsFreeLocation(true); }
                    case "deny" -> { u.setGpsCheckinAllowed(false); u.setGpsFreeLocation(false); }
                    default -> { continue; }
                }
                appUserRepository.save(u);
                changed++;
            }
        }
        return Map.of("ok", true, "changed", changed);
    }

    /**
     * Assign attendance code + office (device) to one user. This used to sit in the admin
     * role modal; it is HR's job now. Code and device must BOTH be given or BOTH be empty,
     * because offices share the same code range (same code on another device is a different
     * person). Both empty = unassign.
     */
    @PostMapping("/assign")
    @ResponseBody
    public Map<String, Object> assign(@RequestBody Map<String, Object> payload) {
        AppUser user = findUser(payload.get("id"));
        if (user == null) return fail("Không tìm thấy user.");

        String code = text(payload.get("employeeCode"));
        if (code != null && code.length() > MAX_EMPLOYEE_CODE_LENGTH) {
            return fail("Mã chấm công tối đa " + MAX_EMPLOYEE_CODE_LENGTH + " ký tự.");
        }

        String deviceRaw = text(payload.get("deviceId"));
        AttendanceDevice device = null;
        if (deviceRaw != null) {
            try {
                device = deviceRepo.findById(Long.valueOf(deviceRaw)).orElse(null);
            } catch (NumberFormatException e) {
                return fail("Văn phòng không hợp lệ.");
            }
            if (device == null) return fail("Văn phòng (máy chấm công) không tồn tại.");
        }
        if ((code == null) != (device == null)) {
            return fail("Mã chấm công và văn phòng phải cùng nhập hoặc cùng để trống.");
        }

        user.setEmployeeCode(code);
        user.setAttendanceDeviceId(device == null ? null : device.getId());
        appUserRepository.save(user);

        String name = user.getFullName() != null ? user.getFullName() : user.getEmail();
        String message = code == null
                ? "Đã gỡ mã chấm công của " + name + "."
                : "Đã gán mã " + code + " (" + officeName(device) + ") cho " + name + ".";
        return Map.of("ok", true, "message", message);
    }

    private AppUser findUser(Object idRaw) {
        String id = text(idRaw);
        if (id == null) return null;
        try {
            return appUserRepository.findById(Long.valueOf(id)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String officeName(AttendanceDevice device) {
        return device.getName() != null ? device.getName() : "máy #" + device.getId();
    }

    /** Trimmed string; null when missing or blank. */
    private static String text(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    private static Map<String, Object> fail(String message) {
        return Map.of("ok", false, "message", message);
    }
}
