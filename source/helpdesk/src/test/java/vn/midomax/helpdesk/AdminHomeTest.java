package vn.midomax.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bảo vệ trang chủ Admin sau khi thay lưới lối tắt bằng khối máy tính ticket.
 *
 * Điểm mong manh nhất là THỜI TIẾT: /js/time-theme.js ghi thẳng lời chào theo giờ
 * vào ".welcome-banner h1" và ".welcome-banner p". Đổi tên class hay bỏ hai thẻ này
 * thì lời chào đứng im mãi ở "Chào buổi sáng" mà KHÔNG có lỗi nào báo ra — nên phải
 * có test khoá lại.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminHomeTest {

    @Autowired
    private MockMvc mockMvc;

    private DefaultOidcUser principal(String role) {
        OidcIdToken idToken = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "s", "name", "Nguoi Kiem Thu", "email", "kiemthu@midomax.vn"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority(role)), idToken, "name");
    }

    private String loadHome() throws Exception {
        return mockMvc.perform(get("/admin-home").with(oidcLogin().oidcUser(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * time-theme.js tìm đúng hai selector này để ghi lời chào. Mất chúng là mất thời tiết.
     */
    @Test
    void giuNguyenChoNeoCuaThoiTietVaLoiChao() throws Exception {
        String html = loadHome();

        assertThat(html)
                .as("phải còn khối .welcome-banner cho time-theme.js ghi lời chào vào")
                .contains("class=\"welcome-banner\"");

        int banner = html.indexOf("class=\"welcome-banner\"");
        String afterBanner = html.substring(banner, Math.min(banner + 900, html.length()));

        assertThat(afterBanner)
                .as("time-theme.js ghi vào '.welcome-banner h1' — thiếu thẻ h1 là lời chào đứng im")
                .contains("<h1");
        assertThat(afterBanner)
                .as("time-theme.js ghi vào '.welcome-banner p' — thiếu thẻ p là mất câu mô tả theo giờ")
                .contains("<p");

        assertThat(html)
                .as("script thời tiết phải còn được nạp")
                .contains("time-theme.js");
    }

    /**
     * Lỗi Thymeleaf giữa trang vẫn trả HTTP 200 với trang cắt cụt, nên phải kiểm thứ
     * nằm CUỐI trang chứ không chỉ nhìn status code.
     */
    @Test
    void trangChuRenderTronVenKemMayTinhVaLoiTat() throws Exception {
        String html = loadHome();

        assertThat(html).as("khối máy tính").contains("id=\"rig-scene\"");
        assertThat(html).as("khủng long trên màn hình").contains("dino-jump");
        assertThat(html).as("mặt bàn chứa cả máy tính lẫn lối tắt").contains("class=\"rig-deck\"");
        assertThat(html).as("lối tắt vẫn còn theo yêu cầu").contains("Lối tắt truy cập nhanh");
        assertThat(html).as("lối tắt Quản lý Ticket vẫn dẫn đúng trang").contains("/ticket-management");

        // Lối tắt phải nằm BÊN TRONG mặt bàn, không rớt xuống dưới thành khối riêng
        int deck = html.indexOf("class=\"rig-deck\"");
        int shortcuts = html.indexOf("Lối tắt truy cập nhanh");
        int deckEnd = html.indexOf("id=\"createTicketModal\"");
        assertThat(shortcuts).as("lối tắt phải nằm sau khi mở mặt bàn").isGreaterThan(deck);
        assertThat(shortcuts).as("lối tắt phải nằm trước phần cuối trang").isLessThan(deckEnd);

        // Nằm ở cuối file: mất là template đã vỡ giữa chừng
        assertThat(html)
                .as("modal tạo ticket nằm cuối trang — mất nghĩa là template vỡ giữa chừng")
                .contains("id=\"createTicketModal\"");
    }

    /**
     * Trang User dùng CHUNG fragment máy tính với trang Admin nhưng bộ số khác.
     * Phải render trọn vẹn và giữ được chỗ neo của thời tiết y như trang Admin.
     */
    @Test
    void trangUserCungCoMayTinhVaGiuDuocThoiTiet() throws Exception {
        String html = mockMvc.perform(get("/user-home")
                        .with(oidcLogin().oidcUser(principal("ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).as("khối máy tính dùng chung fragment").contains("id=\"rig-scene\"");
        assertThat(html).as("khủng long chạy trên màn hình").contains("dino-jump");
        assertThat(html).as("mặt giấy chứa lối tắt").contains("class=\"rig-deck\"");
        assertThat(html).as("lối tắt của user").contains("Lối tắt truy cập nhanh");
        assertThat(html).as("phải link CSS dùng chung, thiếu là mất sạch tạo hình")
                .contains("ticket-rig.css");

        // Chỗ neo của time-theme.js — mất là lời chào theo giờ đứng im
        assertThat(html).contains("class=\"welcome-banner\"");
        assertThat(html).contains("time-theme.js");
    }

    /** Bốn con số trên thẻ dán phải là dữ liệu thật lấy từ ticketService. */
    @Test
    void truyenDuBoSoLieuTicketRaTrangChu() throws Exception {
        var result = mockMvc.perform(get("/admin-home").with(oidcLogin().oidcUser(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Long> stats = (Map<String, Long>) result.getModelAndView().getModel().get("stats");

        assertThat(stats).as("thiếu 'stats' là các thẻ dán ném lỗi lúc render").isNotNull();
        assertThat(stats).containsKeys("total", "unassigned", "mine", "inProgress", "overdue");
    }
}
