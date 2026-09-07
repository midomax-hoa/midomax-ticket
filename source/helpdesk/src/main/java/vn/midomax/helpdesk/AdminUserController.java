package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Controller
public class AdminUserController {

    /** Các role hợp lệ. Nhóm chuyên môn IT không nằm ở đây vì không cấp quyền. */
    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_USER", "ROLE_IT", "ROLE_ADMIN");

    private static final int MIN_PASSWORD_LENGTH = 6;

    @Autowired
    private AzureUserSyncService azureUserSyncService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ModuleAccessService moduleAccessService;

    // View cho trang Quản lý User
    @GetMapping("/admin/users")
    public String userManagementPage(Model model) {
        List<AppUser> users = appUserRepository.findAll();
        model.addAttribute("users", users);
        model.addAttribute("itGroups", ItGroup.values());
        model.addAttribute("departments", Department.values());

        // Bản rút gọn cho JS của modal đồng bộ 365: đánh dấu user nào đã có trong hệ thống.
        // Không đưa thẳng entity vào inline JS để khỏi lộ hash mật khẩu.
        List<Map<String, String>> existingJs = new ArrayList<>();
        for (AppUser u : users) {
            Map<String, String> m = new java.util.HashMap<>();
            m.put("email", u.getEmail());
            m.put("role", u.getRole());
            m.put("department", u.getDepartment());
            existingJs.add(m);
        }
        model.addAttribute("existingUsersJs", existingJs);

        List<Map<String, String>> deptJs = new ArrayList<>();
        for (Department d : Department.values()) {
            Map<String, String> m = new java.util.HashMap<>();
            m.put("code", d.name());
            m.put("label", d.getLabel());
            m.put("display", d.getDisplay());
            deptJs.add(m);
        }
        model.addAttribute("departmentsJs", deptJs);

        List<Map<String, String>> moduleJs = new ArrayList<>();
        for (AppModule m : AppModule.values()) {
            Map<String, String> mm = new java.util.HashMap<>();
            mm.put("code", m.name());
            mm.put("label", m.getLabel());
            mm.put("icon", m.getIcon());
            moduleJs.add(mm);
        }
        model.addAttribute("modulesJs", moduleJs);
        return "user-management";
    }

    // API lấy danh sách User từ Microsoft 365
    @GetMapping("/api/admin/microsoft-users")
    @ResponseBody
    public ResponseEntity<List<Map<String, String>>> fetchMicrosoftUsers() {
        List<Map<String, String>> users = azureUserSyncService.fetchUsersFromMicrosoft365();
        return ResponseEntity.ok(users);
    }

    // API tạo User local (không qua Microsoft 365)
    @PostMapping("/api/admin/users/create-local")
    @ResponseBody
    public ResponseEntity<String> createLocalUser(@RequestBody Map<String, Object> payload) {
        String email = asString(payload.get("email"));
        String fullName = asString(payload.get("fullName"));
        String password = asString(payload.get("password"));
        String role = normalizeRole(asString(payload.get("role")));

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Tên đăng nhập / email không được để trống");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Họ và tên không được để trống");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest().body("Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
        }
        if (!ALLOWED_ROLES.contains(role)) {
            return ResponseEntity.badRequest().body("Vai trò không hợp lệ");
        }
        if (findByLoginName(email) != null) {
            return ResponseEntity.badRequest().body("Tên đăng nhập / email này đã tồn tại trong hệ thống");
        }

        AppUser user = new AppUser(email.trim(), fullName.trim(), role);
        user.setPassword(passwordEncoder.encode(password));
        user.setAuthSource(AppUser.SOURCE_LOCAL);
        user.setItGroups(parseItGroups(payload.get("itGroups"), role));
        user.setDepartment(parseDepartment(payload.get("department")));
        appUserRepository.save(user);
        ticketService.invalidateItStaffCache();

        return ResponseEntity.ok("Đã tạo user local thành công");
    }

    // API đặt lại mật khẩu cho User local
    @PostMapping("/api/admin/users/reset-password")
    @ResponseBody
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> payload) {
        String idStr = payload.get("id");
        String password = payload.get("password");

        if (idStr == null) {
            return ResponseEntity.badRequest().body("Dữ liệu không hợp lệ");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest().body("Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
        }

        Long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("ID không hợp lệ");
        }

        Optional<AppUser> userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Không tìm thấy user");
        }

        AppUser user = userOpt.get();
        if (!user.isLocal()) {
            return ResponseEntity.badRequest().body("User đồng bộ từ Microsoft 365 không đặt mật khẩu tại đây");
        }

        user.setPassword(passwordEncoder.encode(password));
        appUserRepository.save(user);
        return ResponseEntity.ok("Đã đặt lại mật khẩu thành công");
    }

    // API thêm User vào hệ thống
    @PostMapping("/api/admin/users/add-from-365")
    @ResponseBody
    public ResponseEntity<String> addUserFrom365(@RequestBody Map<String, Object> payload) {
        String email = asString(payload.get("email"));
        String fullName = asString(payload.get("fullName"));
        String role = normalizeRole(asString(payload.get("role")));

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Email không hợp lệ");
        }
        if (!ALLOWED_ROLES.contains(role)) {
            return ResponseEntity.badRequest().body("Vai trò không hợp lệ");
        }

        Set<ItGroup> groups = parseItGroups(payload.get("itGroups"), role);
        AppUser existingUser = findByLoginName(email);

        String department = parseDepartment(payload.get("department"));

        if (existingUser != null) {
            // Update role and email if user already exists
            existingUser.setRole(role);
            if (fullName != null && !fullName.trim().isEmpty()) {
                existingUser.setFullName(fullName);
            }
            existingUser.setEmail(email.trim());
            existingUser.setItGroups(groups);
            if (department != null) {
                existingUser.setDepartment(department);
            }
            if (existingUser.getAuthSource() == null) {
                existingUser.setAuthSource(AppUser.SOURCE_M365);
            }
            appUserRepository.save(existingUser);
            ticketService.invalidateItStaffCache();
            return ResponseEntity.ok("Đã cập nhật quyền thành công");
        } else {
            // Create new user
            AppUser newUser = new AppUser(email.trim(), fullName, role);
            newUser.setAuthSource(AppUser.SOURCE_M365);
            newUser.setItGroups(groups);
            newUser.setDepartment(department);
            appUserRepository.save(newUser);
            ticketService.invalidateItStaffCache();
            return ResponseEntity.ok("Đã thêm user thành công");
        }
    }

    // API cập nhật quyền User đã có
    @PostMapping("/api/admin/users/update-role")
    @ResponseBody
    public ResponseEntity<String> updateUserRole(@RequestBody Map<String, Object> payload) {
        String idStr = asString(payload.get("id"));
        String role = normalizeRole(asString(payload.get("role")));

        if (idStr == null || role == null) {
            return ResponseEntity.badRequest().body("Dữ liệu không hợp lệ");
        }
        if (!ALLOWED_ROLES.contains(role)) {
            return ResponseEntity.badRequest().body("Vai trò không hợp lệ");
        }

        try {
            Long id = Long.parseLong(idStr);
            Optional<AppUser> userOpt = appUserRepository.findById(id);
            if (userOpt.isPresent()) {
                AppUser user = userOpt.get();
                user.setRole(role);
                user.setItGroups(parseItGroups(payload.get("itGroups"), role));
                if (payload.containsKey("department")) {
                    user.setDepartment(parseDepartment(payload.get("department")));
                }
                if (payload.containsKey("modules")) {
                    user.setPersonalModules(parseModules(payload.get("modules")));
                }
                if (payload.containsKey("deptHead")) {
                    user.setDeptHead(Boolean.TRUE.equals(payload.get("deptHead")));
                }
                if (payload.containsKey("employeeCode")) {
                    String code = asString(payload.get("employeeCode"));
                    code = code == null ? null : code.trim();
                    // "0" là quy ước XÓA mã: coi như user chưa có mã chấm công
                    if (code == null || code.isEmpty() || code.equals("0")) code = null;
                    user.setEmployeeCode(code);
                }
                if (payload.containsKey("attendanceDeviceId")) {
                    String devRaw = asString(payload.get("attendanceDeviceId"));
                    Long devId = null;
                    if (devRaw != null && !devRaw.isBlank()) {
                        try { devId = Long.parseLong(devRaw.trim()); } catch (NumberFormatException ignored) { }
                    }
                    user.setAttendanceDeviceId(devId);
                }
                // Chấm công GPS: cấp theo từng người; cờ "công tác" cho phép chấm ngoài bán kính
                if (payload.containsKey("gpsAllowed")) {
                    user.setGpsCheckinAllowed(Boolean.TRUE.equals(payload.get("gpsAllowed")));
                }
                if (payload.containsKey("gpsFree")) {
                    user.setGpsFreeLocation(Boolean.TRUE.equals(payload.get("gpsFree")));
                }
                appUserRepository.save(user);
                ticketService.invalidateItStaffCache();

                Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
                if (currentAuth != null && user.getEmail() != null) {
                    String currentUsername = currentAuth.getName().trim().toLowerCase();
                    String targetEmail = user.getEmail().trim().toLowerCase();
                    if (currentUsername.equals(targetEmail) || currentUsername.equals(targetEmail.split("@")[0])) {
                        List<GrantedAuthority> authorities = UserAuthorityMapper.authoritiesOf(user);
                        Authentication newAuth = new UsernamePasswordAuthenticationToken(currentAuth.getPrincipal(), currentAuth.getCredentials(), authorities);
                        SecurityContextHolder.getContext().setAuthentication(newAuth);
                    }
                }

                return ResponseEntity.ok("Đã cập nhật quyền thành công");
            } else {
                return ResponseEntity.badRequest().body("Không tìm thấy user");
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("ID không hợp lệ");
        }
    }

    // ===== Ma trận "Phân hệ theo phòng ban" =====

    /** Đọc ma trận hiện tại: { "TCKT": ["ASSETS"], "NSHC": ["HR"], ... }. */
    @GetMapping("/api/admin/department-access")
    @ResponseBody
    public ResponseEntity<Map<String, Set<String>>> getDepartmentAccess() {
        return ResponseEntity.ok(moduleAccessService.fullMatrix());
    }

    /** Ghi đè toàn bộ ma trận. Body cùng định dạng với GET. */
    @PostMapping("/api/admin/department-access")
    @ResponseBody
    public ResponseEntity<String> saveDepartmentAccess(@RequestBody Map<String, List<String>> body) {
        Map<String, Set<String>> matrix = new java.util.HashMap<>();
        if (body != null) {
            for (Map.Entry<String, List<String>> e : body.entrySet()) {
                matrix.put(e.getKey(), e.getValue() == null ? Set.of() : new java.util.HashSet<>(e.getValue()));
            }
        }
        moduleAccessService.saveMatrix(matrix);
        return ResponseEntity.ok("Đã lưu phân hệ theo phòng ban");
    }

    // API xóa User
    @DeleteMapping("/api/admin/users/{id}")
    @ResponseBody
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        if (appUserRepository.existsById(id)) {
            appUserRepository.deleteById(id);
            ticketService.invalidateItStaffCache();
            return ResponseEntity.ok("Đã xóa user thành công");
        }
        return ResponseEntity.badRequest().body("Không tìm thấy user");
    }

    /**
     * Khớp theo cả email đầy đủ lẫn phần trước dấu @, giống cách đăng nhập tra cứu user,
     * để không tạo ra hai tài khoản mà form login coi là một.
     */
    private AppUser findByLoginName(String email) {
        String cleanEmail = email.trim().toLowerCase();
        String cleanPrefix = cleanEmail.split("@")[0];

        for (AppUser u : appUserRepository.findAll()) {
            if (u.getEmail() != null) {
                String dbEmail = u.getEmail().trim().toLowerCase();
                if (dbEmail.equals(cleanEmail) || dbEmail.split("@")[0].equals(cleanPrefix)) {
                    return u;
                }
            }
        }
        return null;
    }

    private String normalizeRole(String role) {
        return UserAuthorityMapper.normalizeRole(role);
    }

    /** Nhóm chuyên môn chỉ có ý nghĩa với ROLE_IT; role khác luôn về rỗng. */
    private Set<ItGroup> parseItGroups(Object raw, String role) {
        Set<ItGroup> groups = EnumSet.noneOf(ItGroup.class);
        if (!"ROLE_IT".equals(role) || !(raw instanceof List<?> list)) {
            return groups;
        }
        for (Object item : list) {
            ItGroup group = ItGroup.fromString(asString(item));
            if (group != null) {
                groups.add(group);
            }
        }
        return groups;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Danh sách phân hệ cấp riêng cho user từ payload; mã lạ bị bỏ qua. */
    private Set<AppModule> parseModules(Object raw) {
        Set<AppModule> modules = EnumSet.noneOf(AppModule.class);
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                AppModule m = AppModule.fromString(asString(item));
                if (m != null) {
                    modules.add(m);
                }
            }
        }
        return modules;
    }

    /** Chuẩn hóa mã phòng ban từ payload; giá trị lạ hoặc rỗng thành null (chưa phân). */
    private String parseDepartment(Object raw) {
        Department d = Department.fromString(asString(raw));
        return d == null ? null : d.name();
    }
}
