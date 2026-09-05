package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tính danh sách phân hệ (AppModule) mà user hiện tại được vào.
 *
 * Quyền = quyền theo role như trước giờ (không lấy đi của ai) CỘNG THÊM các phân hệ
 * mà phòng ban của user được tick trong ma trận "Phân hệ theo phòng ban".
 * Sidebar dùng kết quả này để ẩn/hiện menu, ModuleAccessInterceptor dùng để chặn URL.
 */
@Service
public class ModuleAccessService {

    @Autowired
    private DepartmentModuleAccessRepository accessRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    /** Cache ma trận: mã phòng ban -> tập mã phân hệ. Nạp lại khi admin bấm lưu. */
    private volatile Map<String, Set<String>> matrixCache;

    /** Tập mã phân hệ user được vào, ví dụ ["ASSETS", "FINANCE"]. */
    public Set<String> modulesOf(Authentication auth) {
        Set<String> allowed = new HashSet<>();
        if (auth == null || !auth.isAuthenticated()) {
            return allowed;
        }

        Set<String> authorities = new HashSet<>();
        for (GrantedAuthority ga : auth.getAuthorities()) {
            authorities.add(ga.getAuthority());
        }

        // 1. Quyền theo role — giữ nguyên hành vi cũ của sidebar/SecurityConfig
        if (authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_MANAGER")) {
            for (AppModule m : EnumSet.allOf(AppModule.class)) {
                allowed.add(m.name());
            }
            return allowed;
        }
        if (authorities.contains("ROLE_IT")) {
            allowed.add(AppModule.REPORTS.name());
        }
        if (authorities.contains("GROUP_HELPDESK")) {
            allowed.add(AppModule.ASSETS.name());
        }

        // 2. Cộng quyền theo phòng ban (ma trận) + phân hệ cấp riêng cho user
        AppUser user = userOf(auth.getName());
        if (user != null) {
            if (user.getDepartment() != null) {
                Set<String> granted = matrix().get(user.getDepartment());
                if (granted != null) {
                    allowed.addAll(granted);
                }
            }
            for (AppModule m : user.getPersonalModules()) {
                allowed.add(m.name());
            }
        }
        return allowed;
    }

    public boolean canAccess(Authentication auth, AppModule module) {
        return module != null && modulesOf(auth).contains(module.name());
    }

    /** Toàn bộ ma trận cho trang cấu hình: mã phòng ban -> tập mã phân hệ. */
    public Map<String, Set<String>> fullMatrix() {
        return new HashMap<>(matrix());
    }

    /** Ghi đè toàn bộ ma trận (admin bấm Lưu) rồi nạp lại cache. */
    @Transactional
    public void saveMatrix(Map<String, Set<String>> newMatrix) {
        // deleteAllInBatch xóa NGAY bằng một câu DELETE, không bị Hibernate dời
        // xuống sau INSERT lúc flush (deleteAll thường gây trùng unique khi tick lại ô cũ).
        accessRepository.deleteAllInBatch();
        for (Map.Entry<String, Set<String>> e : newMatrix.entrySet()) {
            Department dept = Department.fromString(e.getKey());
            if (dept == null || e.getValue() == null) continue;
            for (String moduleRaw : e.getValue()) {
                AppModule module = AppModule.fromString(moduleRaw);
                if (module != null) {
                    accessRepository.save(new DepartmentModuleAccess(dept.name(), module.name()));
                }
            }
        }
        matrixCache = null; // nạp lại ở lần đọc kế tiếp
    }

    private Map<String, Set<String>> matrix() {
        Map<String, Set<String>> cached = matrixCache;
        if (cached == null) {
            cached = new HashMap<>();
            for (DepartmentModuleAccess row : accessRepository.findAll()) {
                cached.computeIfAbsent(row.getDepartment(), k -> new HashSet<>()).add(row.getModule());
            }
            matrixCache = cached;
        }
        return cached;
    }

    /**
     * Tìm AppUser theo tên đăng nhập. Khớp cả email đầy đủ lẫn phần trước
     * dấu @, cùng luật với đăng nhập và AdminUserController.findByLoginName.
     */
    private AppUser userOf(String loginName) {
        if (loginName == null || loginName.isBlank()) return null;
        String clean = loginName.trim().toLowerCase();
        String prefix = clean.split("@")[0];
        for (AppUser u : appUserRepository.findAll()) {
            if (u.getEmail() == null) continue;
            String dbEmail = u.getEmail().trim().toLowerCase();
            if (dbEmail.equals(clean) || dbEmail.split("@")[0].equals(prefix)) {
                return u;
            }
        }
        return null;
    }
}
