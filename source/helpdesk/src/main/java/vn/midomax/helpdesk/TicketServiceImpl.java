package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TicketServiceImpl implements TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private String removeDiacritics(String str) {
        if (str == null) return "";
        return java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    public String normalizeAssignee(String rawAssignee) {
        if (rawAssignee == null || rawAssignee.trim().isEmpty()) {
            return null;
        }
        String clean = rawAssignee.trim();
        if (clean.contains("@")) {
            return clean;
        }
        String cleanAscii = removeDiacritics(clean).toLowerCase();

        // 1. Check in AppUser by email, username, or full name
        for (AppUser u : appUserRepository.findAll()) {
            if (u.getEmail() != null && !u.getEmail().trim().isEmpty()) {
                String dbEmail = u.getEmail().trim();
                String prefix = dbEmail.contains("@") ? dbEmail.split("@")[0].toLowerCase() : dbEmail.toLowerCase();
                if (clean.equalsIgnoreCase(dbEmail) || cleanAscii.equalsIgnoreCase(prefix)) {
                    return dbEmail;
                }
                if (u.getFullName() != null && !u.getFullName().trim().isEmpty()) {
                    String fullAscii = removeDiacritics(u.getFullName().trim()).toLowerCase();
                    if (cleanAscii.equalsIgnoreCase(fullAscii) || fullAscii.contains(cleanAscii) || cleanAscii.contains(fullAscii)) {
                        return dbEmail;
                    }
                }
            }
        }

        // 2. Check in Employee by email, code, or full name
        for (Employee e : employeeRepository.findAll()) {
            String compEmail = e.getCompanyEmail() != null && !e.getCompanyEmail().trim().isEmpty() ? e.getCompanyEmail().trim() : e.getPersonalEmail();
            if (compEmail != null && !compEmail.trim().isEmpty()) {
                String prefix = compEmail.contains("@") ? compEmail.split("@")[0].toLowerCase() : compEmail.toLowerCase();
                if (clean.equalsIgnoreCase(compEmail) || cleanAscii.equalsIgnoreCase(prefix)) {
                    return compEmail;
                }
                if (e.getFullName() != null && !e.getFullName().trim().isEmpty()) {
                    String fullAscii = removeDiacritics(e.getFullName().trim()).toLowerCase();
                    if (cleanAscii.equalsIgnoreCase(fullAscii) || fullAscii.contains(cleanAscii) || cleanAscii.contains(fullAscii)) {
                        return compEmail;
                    }
                }
            }
        }

        return clean;
    }

    @jakarta.annotation.PostConstruct
    public void cleanupExistingAssignees() {
        try {
            List<Ticket> tickets = ticketRepository.findAll();
            int updatedCount = 0;
            for (Ticket t : tickets) {
                if (t.getAssignee() != null && !t.getAssignee().trim().isEmpty()) {
                    String norm = normalizeAssignee(t.getAssignee());
                    if (norm != null && !norm.equals(t.getAssignee())) {
                        t.setAssignee(norm);
                        ticketRepository.save(t);
                        updatedCount++;
                    }
                }
            }
            if (updatedCount > 0) {
                System.out.println("[DB CLEANUP] Successfully normalized " + updatedCount + " ticket assignee records to exact email addresses.");
            }
        } catch (Exception e) {
            System.err.println("[DB CLEANUP WARNING] " + e.getMessage());
        }
    }

    @Override
    public Page<Ticket> getTickets(String tab, String search, int page, String currentUser) {
        String status = null;
        String assignee = null;
        Boolean isUnassigned = null;

        // Parse tab to filter attributes
        if (tab != null) {
            switch (tab.toLowerCase()) {
                case "mine":
                    assignee = normalizeAssignee(currentUser); // ticket được giao cho chính người đang đăng nhập
                    if (assignee == null) assignee = currentUser;
                    break;
                case "unassigned":
                    isUnassigned = true;
                    break;
                case "closed":
                case "resolved":
                    status = "RESOLVED";
                    break;
                default:
                    // 'all' or empty tab -> no filter
                    break;
            }
        }

        // Clean search query
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String searchId = null;

        if (cleanSearch != null) {
            // Check if search represents a formatted ticket code, e.g. T047 -> ID = 47
            if (cleanSearch.toLowerCase().startsWith("t")) {
                try {
                    long idVal = Long.parseLong(cleanSearch.substring(1));
                    searchId = String.valueOf(idVal);
                } catch (NumberFormatException e) {
                    // search keyword is just starting with T but not a valid number, keep searchId as null
                }
            } else {
                // If user typed just number e.g. "47"
                try {
                    Long.parseLong(cleanSearch);
                    searchId = cleanSearch;
                } catch (NumberFormatException e) {
                    // keep searchId as null
                }
            }
        }

        Pageable pageable = PageRequest.of(page, 5);
        return ticketRepository.filterAndSearchTickets(status, assignee, isUnassigned, cleanSearch, searchId, pageable);
    }

    @Override
    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Ticket với ID: " + id));
    }

    @Override
    public Ticket createTicket(Ticket ticket) {
        if (ticket.getCreatedAt() == null) {
            ticket.setCreatedAt(LocalDateTime.now());
        }
        if (ticket.getStatus() == null) {
            ticket.setStatus("OPEN");
        }
        if (ticket.getPriority() == null) {
            ticket.setPriority("LOW");
        }
        // Tự động tính toán hạn SLA dựa trên mức độ ưu tiên
        if (ticket.getSlaDeadline() == null) {
            if ("HIGH".equalsIgnoreCase(ticket.getPriority())) {
                ticket.setSlaDeadline(ticket.getCreatedAt().plusHours(3));
            } else if ("MED".equalsIgnoreCase(ticket.getPriority())) {
                ticket.setSlaDeadline(ticket.getCreatedAt().plusHours(8));
            } else {
                ticket.setSlaDeadline(ticket.getCreatedAt().plusDays(3));
            }
        }
        if (ticket.getEstimatedCompletionTime() != null && ticket.getEstimatedCompletionTime().isBefore(LocalDateTime.now().minusMinutes(2))) {
            throw new IllegalArgumentException("Không thể đặt lịch dự kiến hoàn thành trong quá khứ!");
        }
        if (ticket.getAssignee() != null) {
            ticket.setAssignee(normalizeAssignee(ticket.getAssignee()));
        }
        Ticket saved = ticketRepository.save(ticket);
        emailService.sendTicketNotification(saved, "CREATED");
        return saved;
    }

    @Override
    public Ticket updateTicket(Long id, Ticket updatedTicket) {
        Ticket ticket = getTicketById(id);
        String oldStatus = ticket.getStatus();
        String oldAssignee = ticket.getAssignee();

        // Khi vé đã hoàn thành (RESOLVED), khóa tuyệt đối không cho phép chỉnh sửa nữa
        if ("RESOLVED".equalsIgnoreCase(ticket.getStatus())) {
            return ticket;
        }

        ticket.setTitle(updatedTicket.getTitle());
        if (updatedTicket.getDescription() != null) {
            ticket.setDescription(updatedTicket.getDescription());
        }
        if (updatedTicket.getCategory() != null) {
            ticket.setCategory(updatedTicket.getCategory());
        }
        if (updatedTicket.getPriority() != null) {
            ticket.setPriority(updatedTicket.getPriority());
        }
        if (updatedTicket.getStatus() != null) {
            ticket.setStatus(updatedTicket.getStatus());
        }
        if (updatedTicket.getAssignee() != null) {
            // If empty string, set to null (unassign)
            ticket.setAssignee(normalizeAssignee(updatedTicket.getAssignee()));
        }
        if (updatedTicket.getNotes() != null) {
            ticket.setNotes(updatedTicket.getNotes());
        }
        if (updatedTicket.getFixNote() != null) {
            ticket.setFixNote(updatedTicket.getFixNote());
        }
        // Nguyên tắc 1: Khi đã chọn thời gian dự kiến xong thì không được thay đổi nữa
        if (ticket.getEstimatedCompletionTime() == null && updatedTicket.getEstimatedCompletionTime() != null) {
            if (updatedTicket.getEstimatedCompletionTime().isBefore(LocalDateTime.now().minusMinutes(2))) {
                throw new IllegalArgumentException("Không thể đặt lịch dự kiến hoàn thành trong quá khứ!");
            }
            ticket.setEstimatedCompletionTime(updatedTicket.getEstimatedCompletionTime());
        }
        if (updatedTicket.getCompletedAt() != null) {
            ticket.setCompletedAt(updatedTicket.getCompletedAt());
        } else if ("RESOLVED".equalsIgnoreCase(ticket.getStatus()) && ticket.getCompletedAt() == null) {
            ticket.setCompletedAt(LocalDateTime.now());
        }
        if (updatedTicket.getLocation() != null) {
            ticket.setLocation(updatedTicket.getLocation());
        }
        if (updatedTicket.getManagerApproval() != null) {
            ticket.setManagerApproval(updatedTicket.getManagerApproval());
        }
        if (updatedTicket.getItApproval() != null) {
            ticket.setItApproval(updatedTicket.getItApproval());
        }
        Ticket saved = ticketRepository.save(ticket);
        boolean isNewlyAssigned = saved.getAssignee() != null && !saved.getAssignee().trim().isEmpty() &&
                (oldAssignee == null || oldAssignee.trim().isEmpty() || !oldAssignee.equalsIgnoreCase(saved.getAssignee()) || "OPEN".equalsIgnoreCase(oldStatus));

        if (!"RESOLVED".equalsIgnoreCase(oldStatus) && "RESOLVED".equalsIgnoreCase(saved.getStatus())) {
            emailService.sendTicketNotification(saved, "RESOLVED");
        } else if (isNewlyAssigned) {
            emailService.sendTicketNotification(saved, "ASSIGNED");
        }
        return saved;
    }

    @Override
    public Ticket assignTicket(Long id, String assignee) {
        Ticket ticket = getTicketById(id);
        String oldAssignee = ticket.getAssignee();
        ticket.setAssignee(normalizeAssignee(assignee));
        Ticket saved = ticketRepository.save(ticket);
        if (saved.getAssignee() != null && !saved.getAssignee().trim().isEmpty() && (oldAssignee == null || !oldAssignee.equalsIgnoreCase(saved.getAssignee()))) {
            emailService.sendTicketNotification(saved, "ASSIGNED");
        }
        return saved;
    }

    @Override
    public void deleteTicket(Long id) {
        ticketRepository.deleteById(id);
    }

    @Override
    public Map<String, Long> getStatistics(String currentUser) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", ticketRepository.count());
        stats.put("unassigned", ticketRepository.countByAssigneeIsNull());
        stats.put("mine", currentUser == null ? 0L : ticketRepository.countByAssignee(currentUser));
        stats.put("inProgress", ticketRepository.countByStatus("PROGRESS"));
        
        // SLA overdue: HIGH priority that is not RESOLVED
        stats.put("overdue", ticketRepository.countByPriorityAndStatusNot("HIGH", "RESOLVED"));
        
        return stats;
    }

    @Override
    public Map<String, Long> getCategoryStats() {
        Map<String, Long> categoryStats = new HashMap<>();
        List<Ticket> allTickets = ticketRepository.findAll();
        for (Ticket t : allTickets) {
            String cat = t.getCategory() != null ? t.getCategory() : "other";
            categoryStats.merge(cat, 1L, Long::sum);
        }
        return categoryStats;
    }

    @Override
    public List<Ticket> getRecentTickets() {
        Pageable pageable = PageRequest.of(0, 5, org.springframework.data.domain.Sort.by("createdAt").descending());
        return ticketRepository.findAll(pageable).getContent();
    }

    @Override
    public List<Ticket> getTicketsByAssignee(String assignee) {
        return ticketRepository.findByAssignee(assignee);
    }

    private volatile List<Map<String, Object>> cachedItWorkloadStatus = null;
    private volatile long lastItWorkloadFetchTime = 0;

    @Override
    public void invalidateItStaffCache() {
        cachedItWorkloadStatus = null;
        lastItWorkloadFetchTime = 0;
    }

    /**
     * Danh sách nhân sự IT kèm nhóm chuyên môn và tải hiện tại.
     *
     * Đây là nguồn duy nhất cho ô "Người xử lý": mỗi entry mang danh sách nhóm để
     * giao diện lọc theo danh mục ticket (danh mục chính là tên nhóm, xem ItGroup).
     * "email" là giá trị được lưu vào Ticket.assignee — đừng dùng tên hiển thị.
     */
    @Override
    public List<Map<String, Object>> getItWorkloadStatus() {
        long now = System.currentTimeMillis();
        if (cachedItWorkloadStatus != null && (now - lastItWorkloadFetchTime) < 15000) {
            return cachedItWorkloadStatus;
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        List<AppUser> itUsers = appUserRepository.findAll().stream()
                .filter(u -> "ROLE_IT".equalsIgnoreCase(u.getRole()))
                .filter(u -> u.getEmail() != null && !u.getEmail().trim().isEmpty())
                .sorted(java.util.Comparator.comparing(u -> displayNameOf(u).toLowerCase()))
                .collect(java.util.stream.Collectors.toList());

        for (AppUser u : itUsers) {
            String email = u.getEmail().trim();
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", displayNameOf(u));
            entry.put("email", email);
            entry.put("groups", u.getItGroups().stream().map(Enum::name).collect(java.util.stream.Collectors.toList()));
            entry.put("groupLabels", u.getItGroups().stream().map(ItGroup::getLabel).collect(java.util.stream.Collectors.joining(", ")));

            long assigned = ticketRepository.countByAssignee(email);
            long inProgress = ticketRepository.countByAssigneeAndStatus(email, "PROGRESS");
            entry.put("assigned", assigned);
            entry.put("inProgress", inProgress);
            entry.put("status", inProgress > 3 ? "Busy" : "Available");
            result.add(entry);
        }

        cachedItWorkloadStatus = result;
        lastItWorkloadFetchTime = now;
        return result;
    }

    private String displayNameOf(AppUser u) {
        if (u.getFullName() != null && !u.getFullName().trim().isEmpty()) {
            return u.getFullName().trim();
        }
        return u.getEmail() != null ? u.getEmail().split("@")[0] : "IT Staff";
    }

    @Override
    public long countTicketsByReporterAndStatus(String reporterName, String status) {
        return ticketRepository.countByReporterNameAndStatus(reporterName, status);
    }

    @Override
    public long countTicketsByReporter(String reporterName) {
        return ticketRepository.countByReporterName(reporterName);
    }

    @Override
    public Page<Ticket> getTicketsForUser(String username, String tab, String search, int page) {
        String status = null;
        if (tab != null) {
            switch (tab.toLowerCase()) {
                case "closed":
                case "resolved":
                    status = "RESOLVED";
                    break;
                default:
                    break;
            }
        }

        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String searchId = null;

        if (cleanSearch != null) {
            if (cleanSearch.toLowerCase().startsWith("t")) {
                try {
                    long idVal = Long.parseLong(cleanSearch.substring(1));
                    searchId = String.valueOf(idVal);
                } catch (NumberFormatException e) {}
            } else {
                try {
                    Long.parseLong(cleanSearch);
                    searchId = cleanSearch;
                } catch (NumberFormatException e) {}
            }
        }

        Pageable pageable = PageRequest.of(page, 5);
        return ticketRepository.filterAndSearchTicketsForUser(username, status, cleanSearch, searchId, pageable);
    }

    @Override
    public Map<String, Long> getStatisticsForUser(String username) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", ticketRepository.countByReporterName(username));
        stats.put("open", ticketRepository.countByReporterNameAndStatus(username, "OPEN"));
        stats.put("inProgress", ticketRepository.countByReporterNameAndStatus(username, "PROGRESS"));
        stats.put("resolved", ticketRepository.countByReporterNameAndStatus(username, "RESOLVED"));
        stats.put("mine", ticketRepository.countByReporterName(username)); // mine với user cũng chính là total của họ
        return stats;
    }

    @Override
    public Page<Ticket> getTicketsForIt(String identity, List<String> groups, String tab,
                                        String filterStatus, String filterPriority, String filterCategory,
                                        String filterAssignee, LocalDateTime dateFrom, LocalDateTime dateTo,
                                        String search, int page) {
        // Trạng thái: ưu tiên bộ lọc nâng cao, nếu không có thì suy từ tab.
        String status = (filterStatus != null && !filterStatus.isBlank()) ? filterStatus.trim().toUpperCase() : null;
        String mineAssignee = null;
        Boolean isUnassigned = null;
        if (tab != null) {
            switch (tab.toLowerCase()) {
                case "mine":
                    mineAssignee = normalizeAssignee(identity); // ticket giao cho chính IT đang đăng nhập
                    if (mineAssignee == null) mineAssignee = identity;
                    break;
                case "unassigned":
                    isUnassigned = true;
                    break;
                case "closed":
                case "resolved":
                    if (status == null) status = "RESOLVED";
                    break;
                default:
                    break;
            }
        }

        String priority = (filterPriority != null && !filterPriority.isBlank()) ? filterPriority.trim().toUpperCase() : null;
        // Danh mục lưu bằng TÊN HẰNG ItGroup (viết hoa), dropdown lọc cũng gửi g.name() -> so khớp nguyên trạng.
        String category = (filterCategory != null && !filterCategory.isBlank()) ? filterCategory.trim().toUpperCase() : null;
        String assigneeFilter = (filterAssignee != null && !filterAssignee.isBlank()) ? filterAssignee.trim() : null;
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String searchId = resolveSearchId(cleanSearch);

        int hasGroups = (groups != null && !groups.isEmpty()) ? 1 : 0;
        List<String> effGroups = hasGroups == 1 ? groups : java.util.List.of("__NONE__");

        Pageable pageable = PageRequest.of(page, 5);
        return ticketRepository.filterTicketsForIt(identity, hasGroups, effGroups, status, priority,
                category, assigneeFilter, mineAssignee, isUnassigned, dateFrom, dateTo, cleanSearch, searchId, pageable);
    }

    @Override
    public Map<String, Long> getStatisticsForIt(String identity, List<String> groups) {
        int hasGroups = (groups != null && !groups.isEmpty()) ? 1 : 0;
        List<String> effGroups = hasGroups == 1 ? groups : java.util.List.of("__NONE__");
        List<Ticket> scope = ticketRepository.findAllInItScope(identity, hasGroups, effGroups);

        long total = scope.size();
        long unassigned = scope.stream()
                .filter(t -> t.getAssignee() == null || t.getAssignee().isBlank()).count();
        long mine = scope.stream()
                .filter(t -> identity != null && identity.equals(t.getAssignee())).count();
        long inProgress = scope.stream()
                .filter(t -> "PROGRESS".equalsIgnoreCase(t.getStatus())).count();
        long overdue = scope.stream()
                .filter(t -> "HIGH".equalsIgnoreCase(t.getPriority()) && !"RESOLVED".equalsIgnoreCase(t.getStatus())).count();

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("unassigned", unassigned);
        stats.put("mine", mine);
        stats.put("inProgress", inProgress);
        stats.put("overdue", overdue);
        return stats;
    }

    @Override
    public List<Ticket> getAllTicketsForIt(String identity, List<String> groups, String status, String priority,
                                           String category, String assignee, LocalDateTime dateFrom,
                                           LocalDateTime dateTo, String search) {
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanPriority = (priority != null && !priority.isBlank()) ? priority.trim().toUpperCase() : null;
        String cleanCategory = (category != null && !category.isBlank()) ? category.trim().toUpperCase() : null;
        String cleanAssignee = (assignee != null && !assignee.isBlank()) ? assignee.trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String searchId = resolveSearchId(cleanSearch);

        int hasGroups = (groups != null && !groups.isEmpty()) ? 1 : 0;
        List<String> effGroups = hasGroups == 1 ? groups : java.util.List.of("__NONE__");

        return ticketRepository.filterTicketsForItAll(identity, hasGroups, effGroups, cleanStatus, cleanPriority,
                cleanCategory, cleanAssignee, dateFrom, dateTo, cleanSearch, searchId);
    }

    @Override
    public Page<Ticket> getTicketsFiltered(String tab, String status, String priority,
                                           String category, String assignee,
                                           java.time.LocalDateTime dateFrom, java.time.LocalDateTime dateTo,
                                           String search, int page) {
        // status từ tab (nếu không có filter status riêng)
        String resolvedStatus = (status != null && !status.isBlank()) ? status : null;
        if (resolvedStatus == null && tab != null) {
            switch (tab.toLowerCase()) {
                case "closed":
                case "resolved":
                    resolvedStatus = "RESOLVED";
                    break;
                default:
                    break;
            }
        }

        String cleanPriority = (priority != null && !priority.isBlank()) ? priority.trim().toUpperCase() : null;
        String cleanCategory = (category != null && !category.isBlank()) ? category.trim().toLowerCase() : null;
        String cleanAssignee = (assignee != null && !assignee.isBlank()) ? assignee.trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String searchId = resolveSearchId(cleanSearch);

        Pageable pageable = PageRequest.of(page, 10);
        return ticketRepository.filterTickets(resolvedStatus, cleanPriority, cleanCategory,
                cleanAssignee, dateFrom, dateTo, cleanSearch, searchId, pageable);
    }

    @Override
    public List<Ticket> getAllTicketsFiltered(String status, String priority, String category,
                                              String assignee, java.time.LocalDateTime dateFrom,
                                              java.time.LocalDateTime dateTo, String search) {
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanPriority = (priority != null && !priority.isBlank()) ? priority.trim().toUpperCase() : null;
        String cleanCategory = (category != null && !category.isBlank()) ? category.trim().toLowerCase() : null;
        String cleanAssignee = (assignee != null && !assignee.isBlank()) ? assignee.trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String searchId = resolveSearchId(cleanSearch);

        return ticketRepository.filterTicketsAll(cleanStatus, cleanPriority, cleanCategory,
                cleanAssignee, dateFrom, dateTo, cleanSearch, searchId);
    }

    /** Helper: Giải mã searchId từ chuỗi tìm kiếm (ví dụ: "T47" → "47") */
    private String resolveSearchId(String cleanSearch) {
        if (cleanSearch == null) return null;
        if (cleanSearch.toLowerCase().startsWith("t")) {
            try {
                return String.valueOf(Long.parseLong(cleanSearch.substring(1)));
            } catch (NumberFormatException e) {}
        }
        try {
            Long.parseLong(cleanSearch);
            return cleanSearch;
        } catch (NumberFormatException e) {}
        return null;
    }
}
