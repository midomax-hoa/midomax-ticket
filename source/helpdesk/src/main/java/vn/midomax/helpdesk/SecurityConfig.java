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
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/uploads/**", "/api/notifications/**").permitAll()
                // Quản lý user (trang + API) chỉ dành cho Admin. Phải đứng trước /admin-home
                // vì luật khớp theo thứ tự, luật đầu tiên trúng sẽ thắng.
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                // Trang chủ sau đăng nhập của Admin và IT.
                .requestMatchers("/admin-home").hasAnyRole("ADMIN", "IT")
                // Công Cụ Dụng Cụ: Admin, Manager, và trong IT chỉ nhóm helpdesk.
                .requestMatchers("/assets/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "GROUP_HELPDESK")
                .requestMatchers("/expenses/**", "/employees/**").hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers("/work-reports/**").hasAnyRole("ADMIN", "IT", "MANAGER")
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
