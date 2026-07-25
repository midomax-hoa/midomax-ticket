package vn.midomax.helpdesk;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Danh tính dùng để đối chiếu với Ticket.reporterName.
 *
 * Ticket lưu người gửi bằng EMAIL. Nhưng với tài khoản Microsoft 365,
 * authentication.getName() trả về TÊN HIỂN THỊ (application.properties đặt
 * user-name-attribute=name) chứ không phải email — lấy thẳng getName() để lọc sẽ
 * khiến user không thấy ticket của chính mình.
 *
 * Mọi nơi cần danh tính này (tạo ticket, danh sách ticket, dashboard, export...)
 * phải gọi of() thay vì tự lấy getName(), nếu không hai chỗ sẽ lệch nhau.
 */
public final class ReporterIdentity {

    private ReporterIdentity() {
    }

    public static String of(Authentication authentication) {
        String email = emailOf(authentication);
        if (email != null && !email.isEmpty()) {
            return email;
        }
        return authentication != null ? authentication.getName() : "Guest";
    }

    /** Email lấy từ các claim của 365; trả null nếu là tài khoản local (đăng nhập bằng username). */
    public static String emailOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidc) {
            String value = firstNonBlank(
                    oidc.getEmail(),
                    attribute(oidc, "mail"),
                    attribute(oidc, "userPrincipalName"),
                    attribute(oidc, "preferred_username"));
            if (value != null) {
                return value;
            }
        } else if (principal instanceof OAuth2User oauth) {
            String value = firstNonBlank(
                    attribute(oauth, "mail"),
                    attribute(oauth, "userPrincipalName"),
                    attribute(oauth, "email"),
                    attribute(oauth, "preferred_username"));
            if (value != null) {
                return value;
            }
        }
        String name = authentication.getName();
        return (name != null && name.contains("@")) ? name : null;
    }

    private static String attribute(OAuth2User user, String key) {
        Object value = user.getAttributes().get(key);
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
