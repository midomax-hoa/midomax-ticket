package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.core.io.InputStreamResource;
import vn.midomax.helpdesk.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ExcelService excelService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private StorageService storageService;

    // Trách nhiệm: Chỉ điều hướng và xử lý giao diện Quản lý Ticket
    @GetMapping("/ticket-management")
    public String showTicketManagement(
            @RequestParam(name = "tab", defaultValue = "all") String tab,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "filterStatus", required = false) String filterStatus,
            @RequestParam(name = "filterPriority", required = false) String filterPriority,
            @RequestParam(name = "filterCategory", required = false) String filterCategory,
            @RequestParam(name = "filterAssignee", required = false) String filterAssignee,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo,
            Model model,
            Authentication authentication) {
        
        String username = resolveReporterIdentity(authentication);
        boolean fullAccess = isAdminOrManager(authentication); // ADMIN/MANAGER: thấy & sửa mọi ticket
        boolean it = isIt(authentication);                     // IT: chỉ trong phạm vi nhóm mình

        // Parse date filters
        LocalDateTime from = parseDate(dateFrom, false);
        LocalDateTime to = parseDate(dateTo, true);

        // Check if any advanced filter is active
        boolean hasAdvancedFilter = (filterStatus != null && !filterStatus.isBlank())
                || (filterPriority != null && !filterPriority.isBlank())
                || (filterCategory != null && !filterCategory.isBlank())
                || (filterAssignee != null && !filterAssignee.isBlank())
                || from != null || to != null;

        Page<Ticket> ticketPage;
        Map<String, Long> stats;

        if (fullAccess) {
            if (hasAdvancedFilter) {
                ticketPage = ticketService.getTicketsFiltered(tab, filterStatus, filterPriority,
                        filterCategory, filterAssignee, from, to, search, page);
            } else {
                ticketPage = ticketService.getTickets(tab, search, page, username);
            }
            stats = ticketService.getStatistics(username);
        } else if (it) {
            // IT chỉ thấy ticket thuộc nhóm chuyên môn của mình, ticket được giao, hoặc mình tự gửi.
            List<String> groups = itGroupsOf(authentication);
            ticketPage = ticketService.getTicketsForIt(username, groups, tab, filterStatus, filterPriority,
                    filterCategory, filterAssignee, from, to, search, page);
            stats = ticketService.getStatisticsForIt(username, groups);
        } else {
            ticketPage = ticketService.getTicketsForUser(username, tab, search, page);
            stats = ticketService.getStatisticsForUser(username);
        }

        model.addAttribute("tickets", ticketPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ticketPage.getTotalPages());
        model.addAttribute("stats", stats);
        model.addAttribute("currentTab", tab);
        model.addAttribute("searchQuery", search);
        // isAdmin = có "giao diện quản lý" (bộ thống kê A + tab + nút sửa): ADMIN, MANAGER hoặc IT.
        model.addAttribute("isAdmin", fullAccess || it);
        // Pass filter values back to template
        model.addAttribute("filterStatus", filterStatus);
        model.addAttribute("filterPriority", filterPriority);
        model.addAttribute("filterCategory", filterCategory);
        model.addAttribute("filterAssignee", filterAssignee);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("hasAdvancedFilter", hasAdvancedFilter);
        
        return "ticket-management"; // Trả về file ticket-management.html
    }

    // Xuất danh sách ticket ra file Excel
    @GetMapping("/ticket/export-excel")
    public ResponseEntity<InputStreamResource> exportTicketsExcel(
            @RequestParam(name = "filterStatus", required = false) String filterStatus,
            @RequestParam(name = "filterPriority", required = false) String filterPriority,
            @RequestParam(name = "filterCategory", required = false) String filterCategory,
            @RequestParam(name = "filterAssignee", required = false) String filterAssignee,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo,
            @RequestParam(name = "search", required = false) String search,
            Authentication authentication) throws IOException {

        List<Ticket> tickets;
        if (isAdminOrManager(authentication)) {
            LocalDateTime from = parseDate(dateFrom, false);
            LocalDateTime to = parseDate(dateTo, true);
            tickets = ticketService.getAllTicketsFiltered(filterStatus, filterPriority,
                    filterCategory, filterAssignee, from, to, search);
        } else if (isIt(authentication)) {
            // IT chỉ xuất được ticket trong phạm vi nhóm mình (không rò rỉ nhóm khác).
            LocalDateTime from = parseDate(dateFrom, false);
            LocalDateTime to = parseDate(dateTo, true);
            String username = resolveReporterIdentity(authentication);
            tickets = ticketService.getAllTicketsForIt(username, itGroupsOf(authentication), filterStatus,
                    filterPriority, filterCategory, filterAssignee, from, to, search);
        } else {
            String username = resolveReporterIdentity(authentication);
            tickets = ticketService.getTicketsForUser(username, "all", search, 0).getContent();
        }

        ByteArrayInputStream excelStream = excelService.exportTicketsToExcel(tickets);
        String filename = "tickets_" + LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyyyy")) + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(excelStream));
    }

    /** Parse date string (yyyy-MM-dd) thành LocalDateTime */
    private LocalDateTime parseDate(String dateStr, boolean endOfDay) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(dateStr);
            return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    // API tạo ticket mới qua form HTML POST chuẩn
    @PostMapping("/ticket/create")
    public String createTicket(
            @RequestParam("title") String title,
            @RequestParam(value = "reporterSelect", required = false) String reporterSelect,
            @RequestParam(value = "customRequester", required = false) String customRequester,
            @RequestParam("category") String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "bookedStartTime", required = false) String bookedStartTime,
            @RequestParam(value = "estimatedCompletionTime", required = false) String estimatedCompletionTime,
            @RequestParam("description") String description,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request) {

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        String defaultReporter = resolveReporterIdentity(authentication);

        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setCategory(category);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setStatus("OPEN");
        ticket.setManagerApproval("PENDING");
        ticket.setItApproval("PENDING");

        // Xử lý thông tin người gửi & phân quyền
        if (isAdmin) {
            String reporterName = defaultReporter;
            String reporterDept = "IT";

            if ("user1".equals(reporterSelect)) {
                reporterName = "A";
                reporterDept = "Kế toán";
            } else if ("user2".equals(reporterSelect)) {
                reporterName = "B";
                reporterDept = "Nhân sự";
            } else if ("other".equals(reporterSelect) && customRequester != null && !customRequester.trim().isEmpty()) {
                reporterName = customRequester.trim();
                reporterDept = "Yêu cầu bên ngoài";
            } else if ("self".equals(reporterSelect)) {
                reporterName = defaultReporter;
                reporterDept = "IT Admin";
            }
            
            ticket.setReporterName(reporterName);
            ticket.setReporterDepartment(reporterDept);
            ticket.setPriority(priority != null ? priority.toUpperCase() : "LOW");
            ticket.setAssignee(assignee == null || assignee.trim().isEmpty() ? null : assignee.trim());
            ticket.setLocation(location != null ? location : "Hà Nội");
        } else {
            // USER thường
            ticket.setReporterName(defaultReporter); // tự động lấy mail user đăng nhập
            ticket.setReporterDepartment(department != null ? department : "Nhân viên");
            ticket.setPriority("LOW"); // mặc định LOW
            ticket.setAssignee(assignee == null || assignee.trim().isEmpty() ? null : assignee.trim());
            ticket.setLocation(location != null ? location : "Hà Nội");
        }

        // Parse estimatedCompletionTime if provided
        if (estimatedCompletionTime != null && !estimatedCompletionTime.trim().isEmpty()) {
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                ticket.setEstimatedCompletionTime(LocalDateTime.parse(estimatedCompletionTime.trim(), formatter));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        // Xử lý upload file hình ảnh
        String imagePath = storageService.store(imageFile);
        if (imagePath != null) {
            ticket.setImagePath(imagePath);
        }

        ticketService.createTicket(ticket);

        redirectAttributes.addFlashAttribute("successTicket", ticket);

        String referer = request.getHeader("Referer");
        if (referer != null) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }

    // API cập nhật thông tin chi tiết từ Modal chi tiết
    @PostMapping("/ticket/update")
    public String updateTicket(
            @RequestParam("id") Long id,
            @RequestParam("status") String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "fixNote", required = false) String fixNote,
            @RequestParam(value = "estimatedCompletionTime", required = false) String estimatedCompletionTime,
            @RequestParam(value = "completedAt", required = false) String completedAtStr,
            @RequestParam(value = "completionImageFile", required = false) MultipartFile completionImageFile,
            Authentication authentication) {

        Ticket existing = ticketService.getTicketById(id);
        assertCanAccess(authentication, existing, true); // chỉ người trong phạm vi mới được sửa
        
        if (status != null && !status.isBlank()) {
            existing.setStatus(status.toUpperCase());
        }
        if (priority != null && !priority.isBlank()) {
            existing.setPriority(priority.toUpperCase());
        }
        if (assignee != null) {
            existing.setAssignee(assignee.trim().isEmpty() ? null : assignee.trim());
        }
        if (category != null && !category.isBlank()) {
            existing.setCategory(category);
        }
        if (notes != null) {
            existing.setNotes(notes);
        }
        if (fixNote != null) {
            existing.setFixNote(fixNote);
        }
        if (existing.getEstimatedCompletionTime() == null && estimatedCompletionTime != null && !estimatedCompletionTime.trim().isEmpty()) {
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                existing.setEstimatedCompletionTime(LocalDateTime.parse(estimatedCompletionTime.trim(), formatter));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        if ("RESOLVED".equalsIgnoreCase(existing.getStatus())) {
            if (existing.getItCompletedAt() == null) existing.setItCompletedAt(LocalDateTime.now());
            if (completedAtStr != null && !completedAtStr.trim().isEmpty()) {
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    existing.setCompletedAt(LocalDateTime.parse(completedAtStr.trim(), formatter));
                } catch (Exception e) {
                    if (existing.getCompletedAt() == null) existing.setCompletedAt(LocalDateTime.now());
                }
            } else if (existing.getCompletedAt() == null) {
                existing.setCompletedAt(LocalDateTime.now());
            }
            emailService.sendTicketNotification(existing, "RESOLVED");
        } else if ("CLOSED".equalsIgnoreCase(existing.getStatus())) {
            if (existing.getClosedAt() == null) existing.setClosedAt(LocalDateTime.now());
            if (existing.getCloseReason() == null || existing.getCloseReason().isEmpty()) {
                existing.setCloseReason("Đã đóng công việc bởi chuyên viên IT/Admin");
            }
            emailService.sendTicketNotification(existing, "CLOSED");
        }

        // Xử lý upload file hình ảnh xác nhận hoàn thành
        String completionImagePath = storageService.store(completionImageFile);
        if (completionImagePath != null) {
            existing.setCompletionImagePath(completionImagePath);
        }

        if ("PROGRESS".equals(existing.getStatus()) && existing.getTitle() != null && !existing.getTitle().startsWith("❗ Hỗ trợ ")) {
            existing.setTitle("❗ Hỗ trợ " + existing.getTitle());
        }

        ticketService.updateTicket(id, existing);

        return "redirect:/ticket-management";
    }

    // API lấy thông tin chi tiết ticket dưới dạng JSON (dùng cho AJAX Modal edit)
    @GetMapping("/ticket/detail/{id}")
    @ResponseBody
    public Ticket getTicketDetail(@PathVariable("id") Long id, Authentication authentication) {
        Ticket ticket = ticketService.getTicketById(id);
        assertCanAccess(authentication, ticket, false); // chặn xem chéo ticket ngoài phạm vi
        return ticket;
    }

    // API phân công nhanh (gọi qua AJAX Fetch)
    @PostMapping("/ticket/assign")
    @ResponseBody
    public String assignTicket(@RequestParam("id") Long id, @RequestParam("assignee") String assignee,
                               Authentication authentication) {
        Ticket existing = ticketService.getTicketById(id);
        assertCanAccess(authentication, existing, true);
        ticketService.assignTicket(id, assignee);
        return "success";
    }

    // API cập nhật trạng thái nhanh trên từng dòng (gọi qua AJAX Fetch)
    @PostMapping("/ticket/update-inline")
    @ResponseBody
    public String updateInline(
            @RequestParam("id") Long id,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam("status") String status,
            @RequestParam(value = "assignee", required = false) String assignee,
            @RequestParam(value = "fixNote", required = false) String fixNote,
            @RequestParam(value = "estimatedCompletionTime", required = false) String estimatedCompletionTime,
            @RequestParam(value = "completedAt", required = false) String completedAtStr,
            Authentication authentication) {

        Ticket existing = ticketService.getTicketById(id);
        assertCanAccess(authentication, existing, true);

        if (priority != null && !priority.isBlank()) {
            existing.setPriority(priority.toUpperCase());
        }
        existing.setStatus(status.toUpperCase());
        if (assignee != null) {
            existing.setAssignee(assignee.trim().isEmpty() ? null : assignee.trim());
        }
        if (fixNote != null) {
            existing.setFixNote(fixNote);
        }
        if (existing.getEstimatedCompletionTime() == null && estimatedCompletionTime != null && !estimatedCompletionTime.trim().isEmpty()) {
            try {
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                existing.setEstimatedCompletionTime(LocalDateTime.parse(estimatedCompletionTime.trim(), formatter));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        if ("RESOLVED".equalsIgnoreCase(existing.getStatus())) {
            if (existing.getItCompletedAt() == null) existing.setItCompletedAt(LocalDateTime.now());
            if (completedAtStr != null && !completedAtStr.trim().isEmpty()) {
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    existing.setCompletedAt(LocalDateTime.parse(completedAtStr.trim(), formatter));
                } catch (Exception e) {
                    if (existing.getCompletedAt() == null) existing.setCompletedAt(LocalDateTime.now());
                }
            } else if (existing.getCompletedAt() == null) {
                existing.setCompletedAt(LocalDateTime.now());
            }
            emailService.sendTicketNotification(existing, "RESOLVED");
        } else if ("CLOSED".equalsIgnoreCase(existing.getStatus())) {
            if (existing.getClosedAt() == null) existing.setClosedAt(LocalDateTime.now());
            if (existing.getCloseReason() == null || existing.getCloseReason().isEmpty()) {
                existing.setCloseReason("Đóng công việc bởi chuyên viên IT/Admin");
            }
            emailService.sendTicketNotification(existing, "CLOSED");
        }
        
        if ("PROGRESS".equals(existing.getStatus()) && existing.getTitle() != null && !existing.getTitle().startsWith("❗ Hỗ trợ ")) {
            existing.setTitle("❗ Hỗ trợ " + existing.getTitle());
        }

        ticketService.updateTicket(id, existing);
        return "success";
    }

    // API người dùng xác nhận đã xử lý xong (hết lỗi) -> Đóng Ticket
    @PostMapping("/ticket/user-confirm-resolve")
    public String userConfirmResolve(
            @RequestParam("id") Long id,
            @RequestParam(value = "userFeedback", required = false) String userFeedback,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Ticket ticket = ticketService.getTicketById(id);
        if (ticket != null) {
            String username = resolveReporterIdentity(authentication);
            ticket.setStatus("CLOSED");
            ticket.setClosedAt(LocalDateTime.now());
            ticket.setCloseReason("Người dùng (" + username + ") xác nhận đã xử lý xong (hết lỗi)");
            if (userFeedback != null && !userFeedback.trim().isEmpty()) {
                ticket.setUserFeedback(userFeedback.trim());
            }
            ticketService.updateTicket(id, ticket);
            emailService.sendTicketNotification(ticket, "CLOSED");
            redirectAttributes.addFlashAttribute("successMsg", "Đã xác nhận hoàn thành Ticket #" + id + " thành công!");
        }
        return "redirect:/ticket-management";
    }

    // API người dùng phản hồi còn lỗi -> Tiếp tục xử lý (REOPEN / PROGRESS)
    @PostMapping("/ticket/user-report-issue")
    public String userReportIssue(
            @RequestParam("id") Long id,
            @RequestParam("userFeedback") String userFeedback,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Ticket ticket = ticketService.getTicketById(id);
        if (ticket != null) {
            String username = resolveReporterIdentity(authentication);
            ticket.setStatus("PROGRESS");
            ticket.setUserFeedback(userFeedback.trim());
            String timeStr = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            String noteLine = "[" + timeStr + "] " + username + " phản hồi còn lỗi: " + userFeedback.trim();
            ticket.setNotes((ticket.getNotes() != null && !ticket.getNotes().isEmpty() ? ticket.getNotes() + "\n" : "") + noteLine);
            ticketService.updateTicket(id, ticket);
            emailService.sendTicketNotification(ticket, "REOPENED");
            redirectAttributes.addFlashAttribute("warningMsg", "Đã gửi phản hồi lỗi Ticket #" + id + " tới bộ phận IT!");
        }
        return "redirect:/ticket-management";
    }

    private boolean isManagerOrIT(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_IT")
                        || a.getAuthority().equals("ROLE_MANAGER"));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    /** ADMIN/MANAGER: toàn quyền xem & sửa mọi ticket. */
    private boolean isAdminOrManager(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_ADMIN") || hasAuthority(authentication, "ROLE_MANAGER");
    }

    private boolean isIt(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_IT");
    }

    /** Tên các nhóm chuyên môn của IT (GROUP_HELPDESK -> "HELPDESK"), khớp Ticket.category. */
    private List<String> itGroupsOf(Authentication authentication) {
        if (authentication == null) {
            return java.util.List.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("GROUP_"))
                .map(a -> a.substring("GROUP_".length()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Kiểm tra một ticket có nằm trong phạm vi thao tác của người đăng nhập không.
     * ADMIN/MANAGER: mọi ticket.
     * IT: ticket thuộc nhóm mình / được giao / mình gửi.
     * USER: ticket do chính mình gửi (xem hoặc cập nhật trạng thái của chính mình).
     */
    private boolean canAccessTicket(Authentication authentication, Ticket ticket, boolean forWrite) {
        if (authentication == null || ticket == null) {
            return false;
        }
        if (isAdminOrManager(authentication)) {
            return true;
        }
        String identity = resolveReporterIdentity(authentication);
        if (isIt(authentication)) {
            List<String> groups = itGroupsOf(authentication);
            boolean inGroup = ticket.getCategory() != null && groups.stream().anyMatch(g -> g.equalsIgnoreCase(ticket.getCategory()) || ticket.getCategory().toUpperCase().contains(g.toUpperCase()));
            boolean assigned = ticket.getAssignee() != null && ticket.getAssignee().equalsIgnoreCase(identity);
            boolean reported = ticket.getReporterName() != null && ticket.getReporterName().equalsIgnoreCase(identity);
            return inGroup || assigned || reported || groups.contains("HELPDESK");
        }
        // ROLE_USER / Người gửi: tạo xong CHỈ ĐƯỢC XEM (forWrite = false), KHÔNG ĐƯỢC CHỈNH SỬA/XÓA (forWrite = true).
        if (forWrite) {
            return false;
        }
        return ticket.getReporterName() != null && ticket.getReporterName().equalsIgnoreCase(identity);
    }

    /** Ném 403 nếu người dùng thao tác ngoài phạm vi cho phép. */
    private void assertCanAccess(Authentication authentication, Ticket ticket, boolean forWrite) {
        if (!canAccessTicket(authentication, ticket, forWrite)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác ticket này.");
        }
    }

    // API Xóa ticket
    @GetMapping("/ticket/delete/{id}")
    public String deleteTicket(@PathVariable("id") Long id, RedirectAttributes redirectAttributes,
                               Authentication authentication) {
        Ticket existing = ticketService.getTicketById(id);
        assertCanAccess(authentication, existing, true);
        ticketService.deleteTicket(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa Ticket #" + id + " thành công!");
        return "redirect:/ticket-management";
    }

    // API Calendar: Lấy danh sách IT
    @GetMapping("/api/calendar/it-list")
    @ResponseBody
    public List<Map<String, Object>> getCalendarItList(Authentication authentication) {
        boolean isManagerOrIT = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_IT") || a.getAuthority().equals("ROLE_MANAGER"));
        
        if (isManagerOrIT) {
            return ticketService.getItWorkloadStatus();
        } else {
            String username = resolveReporterIdentity(authentication);
            // Lấy danh sách Assignee (IT) từ các vé PROGRESS do user này tạo
            List<Ticket> userTickets = ticketService.getTicketsForUser(username, "all", "", 0).getContent();
            List<String> assignees = userTickets.stream()
                    .filter(t -> "PROGRESS".equals(t.getStatus()) && t.getAssignee() != null && !t.getAssignee().trim().isEmpty())
                    .map(Ticket::getAssignee)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (String assignee : assignees) {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("name", assignee);
                result.add(map);
            }
            return result;
        }
    }

    // API Calendar: Lấy sự kiện
    @GetMapping("/api/calendar/events")
    @ResponseBody
    public List<Map<String, Object>> getCalendarEvents(@RequestParam(value = "itName", required = false) String itName, Authentication authentication) {
        boolean isManagerOrIT = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_IT") || a.getAuthority().equals("ROLE_MANAGER"));
        String username = resolveReporterIdentity(authentication);

        List<Ticket> tickets;
        if (itName != null && !itName.trim().isEmpty()) {
            tickets = ticketService.getTickets("all", "", 0, username).getContent().stream()
                    .filter(t -> itName.equals(t.getAssignee()) && "PROGRESS".equals(t.getStatus()))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            tickets = new java.util.ArrayList<>();
        }

        List<Map<String, Object>> events = new java.util.ArrayList<>();
        for (Ticket t : tickets) {
            Map<String, Object> event = new java.util.HashMap<>();
            event.put("id", t.getId());
            
            String displayTitle = t.getTitle();
            if (!isManagerOrIT) {
                // USER: Ẩn danh nếu không phải vé của mình
                if (!username.equals(t.getReporterName())) {
                    displayTitle = "❗ Bận"; // Hoặc "Đang xử lý sự cố khác"
                }
            }
            
            event.put("title", displayTitle);
            event.put("start", t.getCreatedAt().toString());
            // Giả lập end time: start + 2 hours
            event.put("end", t.getCreatedAt().plusHours(2).toString());
            
            events.add(event);
        }
        return events;
    }

    // API lấy lịch bận của nhân viên IT hỗ trợ
    @GetMapping("/api/tickets/schedule/{assignee}")
    @ResponseBody
    public List<Map<String, Object>> getSchedule(@PathVariable("assignee") String assignee) {
        List<Ticket> tickets = ticketService.getTicketsByAssignee(assignee);
        List<Map<String, Object>> schedule = new java.util.ArrayList<>();
        for (Ticket t : tickets) {
            if (t.getSlaDeadline() != null && !"RESOLVED".equals(t.getStatus())) {
                String startTime = t.getSlaDeadline().toString();
                String endTime = t.getEstimatedCompletionTime() != null ? t.getEstimatedCompletionTime().toString() : t.getSlaDeadline().plusHours(1).toString();
                Map<String, Object> slot = new java.util.HashMap<>();
                slot.put("start", startTime);
                slot.put("end", endTime);
                slot.put("bookedStartTime", startTime);
                slot.put("bookedEndTime", endTime);
                slot.put("title", t.getTitle() != null ? t.getTitle() : "Yêu cầu IT");
                slot.put("ticketCode", t.getId() != null ? t.getId().toString() : "0");
                slot.put("id", t.getId());
                schedule.add(slot);
            }
        }
        schedule.sort(java.util.Comparator.comparing(m -> (String) m.get("bookedStartTime")));
        return schedule;
    }

    private String resolveReporterIdentity(Authentication authentication) {
        return ReporterIdentity.of(authentication);
    }
}