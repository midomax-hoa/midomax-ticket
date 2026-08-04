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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkReportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkReportRepository workReportRepository;

    @Autowired
    private WorkReportService workReportService;

    private DefaultOidcUser principal(String role) {
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "sub", "name", "Nguoi Tao", "email", "nguoitao@midomax.vn"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority(role)), idToken, "name");
    }

    @Test
    void testCreateWorkReportSuccess() throws Exception {
        mockMvc.perform(post("/work-reports/create")
                        .with(oidcLogin().oidcUser(principal("ROLE_ADMIN")))
                        .param("projectName", "Dự án Helpdesk")
                        .param("taskTitle", "Thử nghiệm tạo báo cáo công việc")
                        .param("assignee", "nguoitao")
                        .param("watchers", "admin, user2")
                        .param("status", "PLANNING")
                        .param("progressPercentage", "10")
                        .param("dailyReport", "Đã khởi tạo nhiệm vụ thành công"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-reports"));

        List<WorkReport> reports = workReportRepository.findAll();
        assertThat(reports).isNotEmpty();
        WorkReport created = reports.stream()
                .filter(r -> "Thử nghiệm tạo báo cáo công việc".equals(r.getTaskTitle()))
                .findFirst()
                .orElse(null);

        assertThat(created).isNotNull();
        assertThat(created.getProjectName()).isEqualTo("Dự án Helpdesk");
        assertThat(created.getAssignee()).isEqualTo("nguoitao");
        assertThat(created.getWatchers()).contains("admin");
    }

    @Test
    void testDeleteWorkReportSuccess() throws Exception {
        WorkReport report = new WorkReport();
        report.setTaskTitle("Nhiệm vụ sắp xóa");
        report.setProjectName("Dự án Xóa");
        report.setAssignee("nguoitao");
        report.setCreatedBy("nguoitao");
        WorkReport saved = workReportRepository.save(report);

        mockMvc.perform(post("/work-reports/delete/" + saved.getId())
                        .with(oidcLogin().oidcUser(principal("ROLE_ADMIN"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-reports"));

        assertThat(workReportRepository.findById(saved.getId())).isEmpty();
    }

    /**
     * Được giao một việc con thì phải thấy cả nhánh của việc cha đó, gồm các việc con
     * khác cùng cha do người khác phụ trách — không chỉ mỗi việc của mình.
     */
    @Test
    void testNguoiDuocGiaoViecConThayCaCacViecConCungCha() {
        WorkReport parent = new WorkReport();
        parent.setTaskTitle("Nhánh cha dùng thử");
        parent.setProjectName("Dự án chung");
        parent.setAssignee("user2");
        parent.setCreatedBy("user2");
        parent = workReportRepository.save(parent);

        WorkReport childOfOther = new WorkReport();
        childOfOther.setTaskTitle("Việc con của user2");
        childOfOther.setProjectName("Dự án chung");
        childOfOther.setAssignee("user2");
        childOfOther.setCreatedBy("user2");
        childOfOther.setParentId(parent.getId());
        childOfOther = workReportRepository.save(childOfOther);

        WorkReport childOfMine = new WorkReport();
        childOfMine.setTaskTitle("Việc con giao cho user");
        childOfMine.setProjectName("Dự án chung");
        childOfMine.setAssignee("user");
        childOfMine.setCreatedBy("user2");
        childOfMine.setParentId(parent.getId());
        childOfMine = workReportRepository.save(childOfMine);

        List<Long> visibleIds = workReportService.getVisibleReports("user").stream()
                .map(WorkReport::getId)
                .toList();

        assertThat(visibleIds).contains(childOfMine.getId(), parent.getId(), childOfOther.getId());
    }
}
