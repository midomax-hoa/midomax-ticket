package vn.midomax.helpdesk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.trim().isEmpty()) {
            throw new UsernameNotFoundException("Tên đăng nhập không được để trống");
        }

        String cleanInput = username.trim().toLowerCase();
        logger.info("[LOGIN ATTEMPT] Local Form Login attempting for username: '{}'", username);

        // 1. Kiểm tra xem tài khoản có trong bảng app_users hay không
        AppUser foundUser = null;
        try {
            for (AppUser u : appUserRepository.findAll()) {
                if (u.getEmail() != null) {
                    String dbEmail = u.getEmail().trim().toLowerCase();
                    String dbPrefix = dbEmail.split("@")[0];
                    if (dbEmail.equals(cleanInput) || dbPrefix.equals(cleanInput)) {
                        foundUser = u;
                        break;
                    }
                }
                if (u.getFullName() != null && u.getFullName().trim().equalsIgnoreCase(cleanInput)) {
                    foundUser = u;
                    break;
                }
            }
        } catch (Exception e) {
            logger.warn("[LOGIN DB CHECK FAILED] Could not query app_users table: {}", e.getMessage());
        }

        if (foundUser == null) {
            // Mặc định cho 2 tài khoản local demo nếu chưa có trong DB
            if ("admin".equals(cleanInput) || "user".equals(cleanInput)) {
                String role = "admin".equals(cleanInput) ? "ROLE_ADMIN" : "ROLE_USER";
                logger.info("[LOGIN SUCCESS] Local account '{}' using default role: '{}'", cleanInput, role);
                return User.builder()
                        .username(cleanInput)
                        .password("{noop}123") // Mật khẩu mặc định là 123 nếu không có trong DB
                        .authorities(new SimpleGrantedAuthority(role))
                        .build();
            } else {
                logger.warn("[LOGIN REJECTED] '{}' is not a local account.", username);
                throw new UsernameNotFoundException("Tài khoản không tồn tại. Nếu bạn dùng Microsoft 365, vui lòng nhấn nút đăng nhập bên dưới.");
            }
        }

        java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
                UserAuthorityMapper.authoritiesOf(foundUser);
        logger.info("[LOGIN SUCCESS] Local account '{}' found in MySQL app_users, assigning authorities: '{}'", cleanInput, authorities);

        String password = foundUser.getPassword();
        if (password == null || password.isEmpty()) {
            password = "{noop}123";
        }

        return User.builder()
                .username(foundUser.getEmail() != null ? foundUser.getEmail() : cleanInput)
                .password(password)
                .authorities(authorities)
                .build();
    }
}
