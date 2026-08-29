package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class AdminUserController {

    @Autowired
    private AzureUserSyncService azureUserSyncService;

    @Autowired
    private AppUserRepository appUserRepository;

    // View cho trang Quản lý User
    @GetMapping("/admin/users")
    public String userManagementPage(Model model) {
        List<AppUser> users = appUserRepository.findAll();
        model.addAttribute("users", users);
        return "user-management";
    }

    // API lấy danh sách User từ Microsoft 365
    @GetMapping("/api/admin/microsoft-users")
    @ResponseBody
    public ResponseEntity<List<Map<String, String>>> fetchMicrosoftUsers(
            @RegisteredOAuth2AuthorizedClient("microsoft") OAuth2AuthorizedClient authorizedClient) {
        
        if (authorizedClient == null) {
            return Respon
        } else {
            // Create new user
            AppUser newUser = new AppUser(email, fullName, role);
            appUserRepository.save(newUser);
            return ResponseEntity.ok("Đã thêm user thành công");
        }
    }
    // API cập nhật quyền User đã có
    @PostMapping("/api/admin/users/update-role")
    @ResponseBody
    public ResponseEntity<String> updateUserRole(@RequestBody Map<String, String> payload) {
        String idStr = payload.get("id");
        String role = payload.get("role");

        if (idStr == null || role == null) {
            return ResponseEntity.badRequest().body("Dữ liệu không hợp lệ");
        }

        try {
            Long id = Long.parseLong(idStr);
            Optional<AppUser> userOpt = appUserRepository.findById(id);
            if (userOpt.isPresent()) {
                AppUser user = userOpt.get();
                user.setRole(role);
                appUserRepository.save(user);
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
            return ResponseEntity.ok("Đã xóa user thành công");
        }
        return ResponseEntity.badRequest().body("Không tìm thấy user");
    }
}

