package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @GetMapping("/")
    public String root(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/admin-home";
        }
        return "redirect:/user-home";
    }

    @GetMapping("/user-home")
    public String userHome(Model model, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "Guest";
        model.addAttribute("username", username);
        model.addAttribute("currentDate", java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")));
        long open = ticketService.countTicketsByReporterAndStatus(username, "OPEN");
        long progress = ticketService.countTicketsByReporterAndStatus(username, "PROGRESS");
        long resolved = ticketService.countTicketsByReporterAndStatus(username, "RESOLVED");
        model.addAttribute("openCount", open);
        model.addAttribute("progressCount", progress);
        model.addAttribute("resolvedCount", resolved);
        
        // Auto-fill form data
        model.addAttribute("department", "Nhân sự"); // Giả lập phòng ban

        // Banner active ticket
        Ticket activeTicket = ticketRepository.findFirstByStatusAndAssigneeIsNotNullOrderByIdDesc("PROGRESS");
        if (activeTicket != null) {
            model.addAttribute("activeTicket", activeTicket);
        }

        return "user-home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/admin-home")
    public String adminHome(Model model) {
        model.addAttribute("adminName", "IT Admin");
        return "admin-home";
    }

    @GetMapping("/schedule")
    public String schedule(Model model) {
        model.addAttribute("itStatusList", ticketService.getItWorkloadStatus());
        return "schedule";
    }

    @GetMapping("/profile")
    public String profile(Model model, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "User";
        model.addAttribute("username", username);
        return "profile";
    }
}
