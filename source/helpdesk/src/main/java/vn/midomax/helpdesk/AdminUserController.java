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

    // View cho trang Quản lý User
    @GetMapping("/admin/users")
    public String userManagementPage(Model model) {
        List<AppUser> users = appUserRepository.findAll();
        model.addAttribute("users", users);
        model.addAttribute("itGroups", ItGroup.values());
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

        if (existingUser != null) {
            // Update role and email if user already exists
            existingUser.setRole(role);
            if (fullName != null && !fullName.trim().isEmpty()) {
                existingUser.setFullName(fullName);
            }
            existingUser.setEmail(email.trim());
            existingUser.setItGroups(groups);
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
}
