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
        // Phải khớp với danh tính lưu ở Ticket.reporterName, xem ReporterIdentity.
        String username = ReporterIdentity.of(authentication);
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isIt = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_IT"));
        
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isIt", isIt);
        model.addAttribute("itWorkload", ticketService.getItWorkloadStatus());
        if (isAdmin) {
            model.addAttribute("stats", ticketService.getStatistics(username));
            model.addAttribute("userTickets", ticketService.getTickets("all", null, 0, username));
        } else if (isIt) {
            model.addAttribute("stats", ticketService.getStatisticsForUser(username));
            model.addAttribute("userTickets", ticketService.getTicketsFiltered("all", null, null, null, username, null, null, null, 0));
        } else {
            model.addAttribute("stats", ticketService.getStatisticsForUser(username));
            model.addAttribute("userTickets", ticketService.getTicketsForUser(username, "all", null, 0));
        }
        
        return "dashboard";
    }
}