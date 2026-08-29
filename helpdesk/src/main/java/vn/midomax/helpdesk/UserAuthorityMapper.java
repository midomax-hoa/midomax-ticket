package vn.midomax.helpdesk;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

/**
 * Dựng danh sách quyền từ AppUser. Dùng chung cho cả đăng nhập form local và
 * Microsoft 365 để hai đường đăng nhập không bao giờ lệch nhau.
 */
public final class UserAuthorityMapper {

    /** Tiền tố cho nhóm chuyên môn IT, tách khỏi ROLE_ để Spring không coi là vai trò. */
    public static final String GROUP_PREFIX = "GROUP_";

    private UserAuthorityMapper() {
    }

    public static String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "ROLE_USER";
        }
        String clean = role.trim().toUpperCase();
        return clean.startsWith("ROLE_") ? clean : "ROLE_" + clean;
    }

    /**
     * Trả về vai trò (ROLE_*) kèm nhóm chuyên môn (GROUP_*). Nhóm chỉ được cấp cho
     * ROLE_IT — role khác luôn có itGroups rỗng nên vòng lặp tự bỏ qua.
     */
    public static List<GrantedAuthority> authoritiesOf(AppUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(normalizeRole(user.getRole())));
        for (ItGroup group : user.getItGroups()) {
            authorities.add(new SimpleGrantedAuthority(GROUP_PREFIX + group.name()));
        }
        return authorities;
    }
}
