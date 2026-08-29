package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import vn.midomax.helpdesk.TicketService;

@Controller
public class DashboardController {

    @Autowired
    private TicketService ticketService;

    // Điều hướng khi người dùng truy cập vào đường dẫn /dashboard
    @GetMapping("/dashboard")
    public String showDashboard(Model model, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "Guest";
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (isAdmin) {
            model.addAttribute("stats", ticketService.getStatistics());
        } else {
            model.addAttribute("stats", ticketService.getStatisticsForUser(username));
            model.addAttribute("userTickets", ticketService.getTicketsForUser(username, "all", null, 0));
        }
        
        return "dashboard";
    }
}