package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private AppUserRepository appUserRepository;

    // Điều hướng khi người dùng truy cập vào đường dẫn /dashboard
    @GetMapping("/dashboard")
    public String showDashboard(Model model, Authentication authentication) {
        String username = ReporterIdentity.of(authentication);
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = a.getAuthority();
                    return auth.equals("ROLE_ADMIN") || auth.equals("ROLE_MANAGER") || auth.equals("ADMIN") || auth.equals("MANAGER");
                });
        boolean isIt = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = a.getAuthority();
                    return auth.equals("ROLE_IT") || auth.equals("IT");
                });
        
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isIt", isIt);
        model.addAttribute("itWorkload", ticketService.getItWorkloadStatus());

        if (isAdmin) {
            model.addAttribute("stats", ticketService.getStatistics(username));
            model.addAttribute("userTickets", ticketService.getTickets("all", null, 0, username));
            model.addAttribute("categoryStats", ticketService.getCategoryStats());
        } else if (isIt) {
            List<String> groups = appUserRepository.findByEmail(username)
                    .map(u -> u.getItGroups().stream().map(Enum::name).collect(java.util.stream.Collectors.toList()))
                    .orElse(java.util.Collections.emptyList());
            
            model.addAttribute("stats", ticketService.getStatisticsForIt(username, groups));
            model.addAttribute("userTickets", ticketService.getTicketsFiltered("all", null, null, null, username, null, null, null, 0));
            model.addAttribute("categoryStats", ticketService.getCategoryStatsForIt(username, groups));
        } else {
            model.addAttribute("stats", ticketService.getStatisticsForUser(username));
            model.addAttribute("userTickets", ticketService.getTicketsForUser(username, "all", null, 0));
            model.addAttribute("categoryStats", ticketService.getCategoryStatsForUser(username));
        }
        
        return "dashboard";
    }

    @Autowired
    private TicketRepository ticketRepository;

    @GetMapping("/api/dashboard/it-schedule")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getItSchedule(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "email", required = false) String emailFilter) {
        List<Map<String, Object>> scheduleList = ticketService.getItScheduleForDate(dateStr);
        if (emailFilter != null && !emailFilter.isBlank()) {
            scheduleList = scheduleList.stream()
                    .filter(it -> emailFilter.equalsIgnoreCase((String) it.get("email")))
                    .collect(java.util.stream.Collectors.toList());
        }
        return ResponseEntity.ok(scheduleList);
    }

    private boolean isAssigneeMatch(String filter, String assignee) {
        if (filter == null || filter.isBlank()) return true;
        if (assignee == null || assignee.isBlank()) return false;
        return filter.trim().equalsIgnoreCase(assignee.trim());
    }

    @GetMapping("/api/dashboard/busy-dates")
    @ResponseBody
    public ResponseEntity<List<String>> getBusyDates(
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "email", required = false) String emailFilter) {
        if (year == null || month == null) {
            java.time.LocalDate now = java.time.LocalDate.now();
            year = now.getYear();
            month = now.getMonthValue();
        }
        List<String> busyDates = ticketService.getBusyDates(year, month, emailFilter);
        return ResponseEntity.ok(busyDates);
    }
}