package vn.midomax.helpdesk;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phân hệ Mạng (/network). Trang khung — ai được cấp qua ma trận
 * "Phân hệ theo phòng ban" (hoặc Admin/Manager) là thấy và vào được;
 * quyền do ModuleAccessInterceptor chặn, không cần kiểm tra lại ở đây.
 */
@Controller
public class NetworkController {

    @GetMapping("/network")
    public String networkPage(Model model) {
        model.addAttribute("activePage", "network");
        return "network";
    }
}
