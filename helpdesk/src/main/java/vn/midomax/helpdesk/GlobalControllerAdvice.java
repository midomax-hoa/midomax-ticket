package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private TicketService ticketService;

    /**
     * Dữ liệu cho ô "Danh mục" và "Người xử lý" của ticket.
     *
     * Đặt ở đây vì hai ô này nằm trong fragment dùng chung cho nhiều trang — nếu để
     * từng controller tự thêm thì sẽ có trang thiếu và dropdown rỗng.
     *
     * - itStaff        : nhân sự IT (ROLE_IT) kèm nhóm chuyên môn và tải hiện tại.
     * - itGroups       : 3 nhóm IT, đồng thời là danh mục ticket (xem ItGroup).
     * - categoryLabels : mã danh mục -> nhãn hiển thị, vd SOFTWARE -> "Phần mềm".
     * - categoryIcons  : mã danh mục -> icon.
     * - itNameByEmail  : email -> tên hiển thị (Ticket.assignee lưu email).
     */
    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        List<Map<String, Object>> itStaff = ticketService.getItWorkloadStatus();
        model.addAttribute("itStaff", itStaff);
        model.addAttribute("itGroups", ItGroup.values());

        Map<String, String> categoryLabels = new LinkedHashMap<>();
        Map<String, String> categoryIcons = new LinkedHashMap<>();
        for (ItGroup group : ItGroup.values()) {
            categoryLabels.put(group.name(), group.getCategoryLabel());
            categoryIcons.put(group.name(), group.getIcon());
        }
        model.addAttribute("categoryLabels", categoryLabels);
        model.addAttribute("categoryIcons", categoryIcons);

        Map<String, String> itNameByEmail = new LinkedHashMap<>();
        for (Map<String, Object> staff : itStaff) {
            itNameByEmail.put(String.valueOf(staff.get("email")), String.valueOf(staff.get("name")));
        }
        model.addAttribute("itNameByEmail", itNameByEmail);

        // Tính sẵn ở đây thay vì lọc bằng biểu thức trong template: bên trong phép lọc
        // .?[...] của SpEL, ngữ cảnh đổi sang từng phần tử nên không đọc được biến ngoài
        // như ticket.category — viết kiểu đó sẽ ném EL1008E lúc render.
        Map<String, List<String>> itEmailsByCategory = new LinkedHashMap<>();
        for (ItGroup group : ItGroup.values()) {
            List<String> emails = new ArrayList<>();
            for (Map<String, Object> staff : itStaff) {
                Object groups = staff.get("groups");
                if (groups instanceof List<?> list && list.contains(group.name())) {
                    emails.add(String.valueOf(staff.get("email")));
                }
            }
            itEmailsByCategory.put(group.name(), emails);
        }
        model.addAttribute("itEmailsByCategory", itEmailsByCategory);
    }
}
