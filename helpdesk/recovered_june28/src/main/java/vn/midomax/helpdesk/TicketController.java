package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class TicketController {

    @Autowired
    private TicketService ticketService;

    // Trách nhiệm: Chỉ điều hướng và xử lý giao diện Quản lý Ticket
    @GetMapping("/ticket-management")
    public String showTicketManagement(
            @RequestParam(name = "tab", defaultValue = "all") String tab,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model,
            Authentication authentication) {
        
        String username = authentication != null ? authentication.getName() : "Guest";
        boolean isAdmin = authentication != null

        return "redirect:/ticket-management";
    }

    // API lấy thông tin chi tiết ticket dưới dạng JSON (dùng cho AJAX Modal edit)
    @GetMapping("/ticket/detail/{id}")
    @ResponseBody
    public Ticket getTicketDetail(@PathVariable("id") Long id) {
        return ticketService.getTicketById(id);
    }

    // API phân công nhanh (gọi qua AJAX Fetch)
    @PostMapping("/ticket/assign")
    @ResponseBody
    public String assignTicket(@RequestParam("id") Long id, @RequestParam("assignee") String assignee) {
        ticketService.assignTicket(id, assignee);
        return "success";
    }

    // API cập nhật trạng thái nhanh trên từng dòng (gọi qua AJAX Fetch)
    @PostMapping("/ticket/update-inline")
    @ResponseBody
    public String updateInline(
            @RequestParam("id") Long id,
            @RequestParam("priority") String priority,
            @RequestParam("status") String status,
            @RequestParam("assignee") String assignee) {

        Ticket ticket = ticketService.getTicketById(id);
        ticket.setPriority(priority.toUpperCase());
        ticket.setStatus(status.toUpperCase());
        ticket.setAssignee(assignee.trim().isEmpty() ? null : assignee.trim());
        
        ticketService.updateTicket(id, ticket);
        return "success";
    }

    // API Xóa ticket
    @GetMapping("/ticket/delete/{id}")
    public String deleteTicket(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        ticketService.deleteTicket(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa Ticket #" + id + " thành công!");
        return "redirect:/ticket-management";
    }
}