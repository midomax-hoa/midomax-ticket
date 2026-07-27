package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
@org.springframework.web.bind.annotation.RequestMapping(produces = "text/html;charset=UTF-8")
public class HomeController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    /** Lấy fullName từ DB theo email; fallback về tên đăng nhập nếu không tìm thấy. */
    private String resolveDisplayName(Authentication authentication) {
        if (authentication == null) return "Bạn";
        String principal = authentication.getName();
        return appUserRepository.findByEmail(principal)
                .map(AppUser::getFullName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(principal);
    }

    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    public String root(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = a.getAuthority();
                    return auth.equals("ROLE_ADMIN") || auth.equals("ROLE_IT") || auth.equals("ADMIN") || auth.equals("IT");
                })) {
            return "redirect:/admin-home";
        }
        return "redirect:/user-home";
    }

    @GetMapping(value = "/user-home", produces = "text/html;charset=UTF-8")
    public String userHome(Model model, Authentication authentication) {
        // Tên để hiển thị và danh tính để đối chiếu ticket là HAI thứ khác nhau: với tài
        // khoản 365, getName() trả về tên hiển thị còn ticket lại lưu email.
        String username = resolveDisplayName(authentication);
        String identity = ReporterIdentity.of(authentication);
        model.addAttribute("username", username);
        model.addAttribute("currentDate", java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")));
        long open = ticketService.countTicketsByReporterAndStatus(identity, "OPEN");
        long progress = ticketService.countTicketsByReporterAndStatus(identity, "PROGRESS");
        long resolved = ticketService.countTicketsByReporterAndStatus(identity, "RESOLVED");
        model.addAttribute("openCount", open);
        model.addAttribute("progressCount", progress);
        model.addAttribute("resolvedCount", resolved);
        
        // Auto-fill form data
        model.addAttribute("department", "NhA?n s?"); // Gi? l?-p phA?ng ban

        // Banner active ticket
        Ticket activeTicket = ticketRepository.findFirstByStatusAndAssigneeIsNotNullOrderByIdDesc("PROGRESS");
        if (activeTicket != null) {
            model.addAttribute("activeTicket", activeTicket);
        }

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_IT") || a.getAuthority().equals("ROLE_MANAGER"));
        model.addAttribute("isAdmin", isAdmin);
        return "user-home";
    }

    @GetMapping(value = "/login", produces = "text/html;charset=UTF-8")
    public String login() {
        return "login";
    }

    @GetMapping(value = "/admin-home", produces = "text/html;charset=UTF-8")
    public String adminHome(Model model, Authentication authentication) {
        String displayName = resolveDisplayName(authentication);
        model.addAttribute("adminName", displayName);
        model.addAttribute("isAdmin", true);
        // Số liệu cho các thẻ dán quanh máy tính ở trang chủ. Dùng chung getStatistics()
        // với trang Quản lý Ticket nên hai nơi luôn khớp số. Khóa: total, unassigned,
        // mine, inProgress, overdue.
        model.addAttribute("stats", ticketService.getStatistics(ReporterIdentity.of(authentication)));
        return "admin-home";
    }

    @GetMapping(value = "/schedule", produces = "text/html;charset=UTF-8")
    public String schedule(Model model, Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_IT") || a.getAuthority().equals("ROLE_MANAGER"));
        model.addAttribute("isAdmin", isAdmin);
        List<Map<String, Object>> workload = ticketService.getItWorkloadStatus();
        model.addAttribute("itWorkload", workload);
        model.addAttribute("itStatusList", workload);
        return "schedule";
    }

    @GetMapping(value = "/profile", produces = "text/html;charset=UTF-8")
    public String profile(Model model, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "User";
        model.addAttribute("username", username);
        return "profile";
    }
}

