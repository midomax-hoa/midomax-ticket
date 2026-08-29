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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bảo vệ luồng "user đăng nhập bằng Microsoft 365 xem ticket của chính mình".
 *
 * Ticket lưu người gửi bằng EMAIL, nhưng với tài khoản 365 thì
 * authentication.getName() lại trả về TÊN HIỂN THỊ (application.properties đặt
 * user-name-attribute=name). Nếu chỗ đọc danh sách lấy thẳng getName() để lọc thì
 * user không thấy ticket của chính mình — đúng lỗi đã gặp với adobe01@midomax.vn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketVisibilityOidcTest {

    private static final String EMAIL = "oidc.kiemthu@midomax.vn";
    private static final String DISPLAY_NAME = "Nguoi Dung Kiem Thu";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    /** Dựng principal giống hệt production: tên hiển thị là name, email nằm ở claim riêng. */
    private DefaultOidcUser oidcPrincipal(String role, String... groups) {
        OidcIdToken idToken = new OidcIdToken(
                "token-kiem-thu",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("sub", "sub-kiem-thu", "name", DISPLAY_NAME, "email", EMAIL));
        java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));
        for (String g : groups) {
            authorities.add(new SimpleGrantedAuthority("GROUP_" + g));
        }
        return new DefaultOidcUser(authorities, idToken, "name");
    }

    private Ticket saveTicket(String reporterName, String title) {
        return saveTicket(reporterName, title, null);
    }

    private Ticket saveTicket(String reporterName, String title, String assignee) {
        return saveTicket(reporterName, title, assignee, ItGroup.SOFTWARE.name());
    }

    private Ticket saveTicket(String reporterName, String title, String assignee, String category) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setCategory(category);
        ticket.setPriority("LOW");
        ticket.setStatus("OPEN");
        ticket.setReporterName(reporterName);
        ticket.setAssignee(assignee);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setSlaDeadline(LocalDateTime.now().plusDays(1));
        return ticketRepository.save(ticket);
    }

    @Test
    void userDangNhap365PhaiThayTicketCuaChinhMinh() throws Exception {
        DefaultOidcUser principal = oidcPrincipal("ROLE_USER");
        assertThat(principal.getName())
                .as("tiền đề của lỗi: getName() trả về tên hiển thị chứ không phải email")
                .isEqualTo(DISPLAY_NAME)
                .isNotEqualTo(EMAIL);

        saveTicket(EMAIL, "Ticket cua toi");

        var result = mockMvc.perform(get("/ticket-management").with(oidcLogin().oidcUser(principal)))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Ticket> tickets = (List<Ticket>) result.getModelAndView().getModel().get("tickets");
        @SuppressWarnings("unchecked")
        Map<String, Long> stats = (Map<String, Long>) result.getModelAndView().getModel().get("stats");

        assertThat(tickets).as("user 365 phải thấy ticket của chính mình").isNotEmpty();
        assertThat(tickets).allMatch(t -> EMAIL.equals(t.getReporterName()));
        assertThat(stats.get("total")).as("ô thống kê không được ra 0").isGreaterThan(0);
    }

    @Test
    void dashboardCuaUser365PhaiDemDuocTicketCuaHo() throws Exception {
        saveTicket(EMAIL, "Ticket tren dashboard");

        var result = mockMvc.perform(get("/dashboard")
                        .with(oidcLogin().oidcUser(oidcPrincipal("ROLE_USER"))))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Long> stats = (Map<String, Long>) result.getModelAndView().getModel().get("stats");
        Object userTickets = result.getModelAndView().getModel().get("userTickets");

        assertThat(stats.get("total")).as("ô 'Ticket Của Bạn' không được ra 0").isGreaterThan(0);
        assertThat(((org.springframework.data.domain.Page<?>) userTickets).getContent())
                .as("danh sách ticket gần đây trên dashboard không được rỗng")
                .isNotEmpty();
    }

    /**
     * Ticket ĐÃ có người phụ trách phải render được trọn trang.
     *
     * Ô "Người xử lý" chỉ liệt kê IT đúng nhóm, nên phải kiểm tra xem người đang được
     * giao có nằm ngoài nhóm không — nhánh đó chỉ chạy khi assignee != null. Ticket
     * chưa phân công sẽ đi vòng qua nó và che mất lỗi.
     *
     * Lỗi từng gặp: template vỡ giữa chừng nên phần modal ở cuối trang biến mất →
     * nút "Tạo Ticket" và modal chi tiết chết, mà HTTP vẫn trả 200.
     */
    @Test
    void ticketDaPhanCongVanRenderDuTrangKemModal() throws Exception {
        saveTicket(EMAIL, "Ticket da phan cong", "ai.do@midomax.vn");

        var result = mockMvc.perform(get("/ticket-management")
                        .with(oidcLogin().oidcUser(oidcPrincipal("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html)
                .as("modal tạo ticket nằm cuối trang — mất nghĩa là template đã vỡ giữa chừng")
                .contains("id=\"createTicketModal\"")
                .contains("function openDetailModal");
    }

    @SuppressWarnings("unchecked")
    private List<Ticket> loadTicketsAs(DefaultOidcUser principal) throws Exception {
        var result = mockMvc.perform(get("/ticket-management")
                        .with(oidcLogin().oidcUser(principal)))
                .andExpect(status().isOk())
                .andReturn();
        return (List<Ticket>) result.getModelAndView().getModel().get("tickets");
    }

    /** IT thấy ticket do người khác gửi NẾU danh mục thuộc nhóm của mình. */
    @Test
    void itThayTicketCungNhomDuDoNguoiKhacGui() throws Exception {
        saveTicket("nguoi.khac@midomax.vn", "Ticket phan mem", null, ItGroup.SOFTWARE.name());

        List<Ticket> tickets = loadTicketsAs(oidcPrincipal("ROLE_IT", "SOFTWARE"));

        assertThat(tickets).as("IT nhóm SOFTWARE phải thấy ticket danh mục SOFTWARE của người khác").isNotEmpty();
        assertThat(tickets).anyMatch(t -> "nguoi.khac@midomax.vn".equals(t.getReporterName()));
    }

    /**
     * Đây là cốt lõi user yêu cầu: IT nhóm HELPDESK KHÔNG được thấy ticket danh mục
     * REPORT/SOFTWARE mà mình không được giao và không tự gửi. "Đổi qua báo cáo thì
     * theo nguyên tắc sẽ không thấy" — test khoá đúng hành vi đó.
     */
    @Test
    void itKhongThayTicketKhacNhomVaKhongLienQuan() throws Exception {
        saveTicket("nguoi.khac@midomax.vn", "Ticket bao cao", null, ItGroup.REPORT.name());

        List<Ticket> tickets = loadTicketsAs(oidcPrincipal("ROLE_IT", "HELPDESK"));

        assertThat(tickets)
                .as("IT nhóm HELPDESK không được thấy ticket danh mục REPORT của người khác")
                .noneMatch(t -> "nguoi.khac@midomax.vn".equals(t.getReporterName()));
    }

    /** Ticket ngoài nhóm nhưng ĐƯỢC GIAO cho IT thì vẫn phải thấy (không mất việc). */
    @Test
    void itVanThayTicketNgoaiNhomNeuDuocGiaoChoMinh() throws Exception {
        saveTicket("nguoi.khac@midomax.vn", "Ticket bao cao giao cho IT", EMAIL, ItGroup.REPORT.name());

        List<Ticket> tickets = loadTicketsAs(oidcPrincipal("ROLE_IT", "HELPDESK"));

        assertThat(tickets)
                .as("IT phải thấy ticket được giao cho mình dù danh mục ngoài nhóm")
                .anyMatch(t -> EMAIL.equals(t.getAssignee()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateRequest(Long id) {
        return post("/ticket/update")
                .param("id", String.valueOf(id))
                .param("status", "progress")
                .param("priority", "low")
                .param("assignee", "")
                .param("category", ItGroup.SOFTWARE.name())
                .param("notes", "");
    }

    /** ROLE_USER tuyệt đối không được sửa ticket (kể cả ticket của chính mình). */
    @Test
    void userThuongKhongSuaDuocTicket() throws Exception {
        Ticket t = saveTicket(EMAIL, "Ticket cua toi", null, ItGroup.SOFTWARE.name());

        mockMvc.perform(updateRequest(t.getId()).with(oidcLogin().oidcUser(oidcPrincipal("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** IT không được sửa ticket ngoài phạm vi (khác nhóm, không được giao, không tự gửi). */
    @Test
    void itKhongSuaDuocTicketNgoaiPhamVi() throws Exception {
        Ticket t = saveTicket("nguoi.khac@midomax.vn", "Ticket bao cao", null, ItGroup.REPORT.name());

        mockMvc.perform(updateRequest(t.getId()).with(oidcLogin().oidcUser(oidcPrincipal("ROLE_IT", "HELPDESK"))))
                .andExpect(status().isForbidden());
    }

    /** IT được sửa ticket trong nhóm mình (không bị 403). */
    @Test
    void itSuaDuocTicketTrongNhom() throws Exception {
        Ticket t = saveTicket("nguoi.khac@midomax.vn", "Ticket phan mem", null, ItGroup.SOFTWARE.name());

        mockMvc.perform(updateRequest(t.getId()).with(oidcLogin().oidcUser(oidcPrincipal("ROLE_IT", "SOFTWARE"))))
                .andExpect(status().is3xxRedirection());
    }
}
