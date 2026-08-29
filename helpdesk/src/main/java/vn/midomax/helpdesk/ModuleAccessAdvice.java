package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;

/**
 * Gắn biến `allowedModules` (tập mã phân hệ user được vào) cho MỌI trang,
 * để fragment sidebar ẩn/hiện menu theo phòng ban mà không cần từng controller tự nạp.
 */
@ControllerAdvice
public class ModuleAccessAdvice {

    @Autowired
    private ModuleAccessService moduleAccessService;

    @Autowired
    private AppUserRepository appUserRepository;

    @ModelAttribute("allowedModules")
    public Set<String> allowedModules() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return moduleAccessService.modulesOf(auth);
    }

    /**
     * Cờ cho sidebar: user có quyền duyệt đơn nghỉ phép / bổ sung công không
     * (Admin, trưởng phòng, hoặc thuộc phòng NSHC). Hiện menu "Duyệt Đơn Từ".
     */
    @ModelAttribute("canApproveLeave")
    public boolean canApproveLeave() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return false;
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return true;
        }
        String login = auth.getName().trim().toLowerCase();
        String prefix = login.split("@")[0];
        String email365 = ReporterIdentity.emailOf(auth);
        String email365Lower = email365 == null ? null : email365.trim().toLowerCase();
        for (AppUser u : appUserRepository.findAll()) {
            if (u.getEmail() == null) continue;
            String dbEmail = u.getEmail().trim().toLowerCase();
            String dbPrefix = dbEmail.split("@")[0];
            boolean match = dbEmail.equals(login) || dbPrefix.equals(prefix)
                    || (email365Lower != null && (dbEmail.equals(email365Lower) || dbPrefix.equals(email365Lower.split("@")[0])));
            if (match) {
                return u.isDeptHead() || "NSHC".equals(u.getDepartment());
            }
        }
        return false;
    }
}
