package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import vn.midomax.helpdesk.security.CustomAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomOidcUserService customOidcUserService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .csrf(csrf -> csrf.disable()) // Tắt chống giả mạo CSRF tạm thời
            .authorizeHttpRequests(auth -> auth
                // /api/notifications KHÔNG được để permitAll: nội dung chuông là tiêu đề
                // ticket, báo cáo, tình trạng đơn nghỉ — phải đăng nhập mới đọc được.
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/uploads/**").permitAll()
                // Quản lý user (trang + API) chỉ dành cho Admin. Phải đứng trước /admin-home
                // vì luật khớp theo thứ tự, luật đầu tiên trúng sẽ thắng.
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                // Trang chủ sau đăng nhập của Admin và IT.
                .requestMatchers("/admin-home").hasAnyRole("ADMIN", "IT")
                // Các phân hệ giới hạn (/assets, /expenses, /employees, /attendance, /work-reports):
                // chỉ cần đăng nhập ở tầng này; quyền thật do ModuleAccessInterceptor quyết định
                // theo role CỘNG ma trận "Phân hệ theo phòng ban" (cấu hình trong Quản lý User).
                .requestMatchers("/assets/**", "/expenses/**", "/employees/**",
                                 "/attendance/**", "/work-reports/**").authenticated()
                // Sửa/phân công/xoá ticket: chỉ ADMIN/IT/MANAGER. ROLE_USER chỉ được
                // tạo (/ticket/create) và xem ticket của mình. Phạm vi chi tiết cho IT
                // (chỉ ticket trong nhóm) do TicketController tự kiểm tra thêm.
                .requestMatchers("/ticket/update", "/ticket/update-inline", "/ticket/assign", "/ticket/delete/**")
                    .hasAnyRole("ADMIN", "IT", "MANAGER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(customAuthenticationSuccessHandler)
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(customAuthenticationSuccessHandler)
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(customOidcUserService)
                )
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );
        
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
