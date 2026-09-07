package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Cấp biến ${displayName} cho mọi trang: tên hiển thị đẹp của người đang đăng nhập.
 *
 * Authentication.getName() giờ là email/UPN (để phân quyền tra được app_users),
 * nên tên đẹp lấy riêng: ưu tiên claim "name" của Microsoft, rồi đến fullName
 * trong app_users, cuối cùng mới rơi về tên đăng nhập.
 */
@ControllerAdvice
public class CurrentUserDisplayAdvice {

    @Autowired
    private ModuleAccessService moduleAccessService;

    @ModelAttribute("displayName")
    public String displayName(Authentication auth) {
        if (auth == null) return "";

        if (auth.getPrincipal() instanceof OidcUser oidcUser) {
            String name = oidcUser.getFullName();
            if (StringUtils.hasText(name)) return name;
        }

        AppUser user = moduleAccessService.userOf(auth.getName());
        if (user != null && StringUtils.hasText(user.getFullName())) {
            return user.getFullName();
        }
        return auth.getName();
    }
}
