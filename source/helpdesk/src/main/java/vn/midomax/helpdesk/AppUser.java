package vn.midomax.helpdesk;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "app_users")
public class AppUser {

    /** Tài khoản tạo trực tiếp trong hệ thống, đăng nhập bằng form + mật khẩu. */
    public static final String SOURCE_LOCAL = "LOCAL";
    /** Tài khoản đồng bộ từ Microsoft 365, đăng nhập bằng nút Microsoft. */
    public static final String SOURCE_M365 = "M365";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String fullName;
    private String role; // e.g., "ROLE_USER", "ROLE_ADMIN", "ROLE_IT"
    private String password;

    /** SOURCE_LOCAL hoặc SOURCE_M365. Xem isLocal() trước khi đọc trực tiếp. */
    @Column(name = "auth_source", length = 20)
    private String authSource;

    /**
     * Nhóm chuyên môn IT. Chỉ có ý nghĩa khi role là ROLE_IT — không cấp thêm quyền,
     * chỉ dùng để phân loại và định tuyến ticket.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_it_groups", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "it_group", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<ItGroup> itGroups = EnumSet.noneOf(ItGroup.class);

    public AppUser() {
    }

    public AppUser(String email, String fullName, String role) {
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAuthSource() {
        return authSource;
    }

    public void setAuthSource(String authSource) {
        this.authSource = authSource;
    }

    /**
     * Các bản ghi tạo trước khi có cột auth_source sẽ có giá trị null; khi đó suy ra
     * từ mật khẩu, vì user đồng bộ từ 365 không bao giờ được đặt mật khẩu.
     */
    public boolean isLocal() {
        if (authSource != null) {
            return SOURCE_LOCAL.equals(authSource);
        }
        return password != null && !password.isEmpty();
    }

    public Set<ItGroup> getItGroups() {
        return itGroups;
    }

    public void setItGroups(Set<ItGroup> itGroups) {
        this.itGroups.clear();
        if (itGroups != null) {
            this.itGroups.addAll(itGroups);
        }
    }
}

