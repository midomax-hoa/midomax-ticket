package vn.midomax.helpdesk;

import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TicketService {

    /**
     * @param currentUser email của người đang đăng nhập — cần cho tab "mine"
     *                    (ticket được giao cho chính họ). Xem ReporterIdentity.
     */
    Page<Ticket> getTickets(String tab, String search, int page, String currentUser);

    Ticket getTicketById(Long id);

    Ticket createTicket(Ticket ticket);

    Ticket updateTicket(Long id, Ticket updatedTicket);

    Ticket assignTicket(Long id, String assignee);

    void deleteTicket(Long id);

    // Get statistics for the dashboard/ticket widgets
    // currentUser: email người đang đăng nhập, dùng cho ô "Của tôi".
    Map<String, Long> getStatistics(String currentUser);
    
    // Get category statistics for the dashboard donut chart
    Map<String, Long> getCategoryStats();
    Map<String, Long> getCategoryStatsForUser(String username);
    Map<String, Long> getCategoryStatsForIt(String identity, List<String> groups);
    List<Map<String, Object>> getItScheduleForDate(String dateStr);
    List<String> getBusyDates(int year, int month, String emailFilter);
    
    // Get top 5 recent tickets for the dashboard
    List<Ticket> getRecentTickets();

    // Get tickets by assignee
    List<Ticket> getTicketsByAssignee(String assignee);

    // Get IT staff workload status
    List<Map<String, Object>> getItWorkloadStatus();

    /**
     * Xoá cache danh sách IT. Gọi ngay sau khi thêm/sửa/xoá user, để IT vừa được
     * xếp nhóm hiện ra ô "Người xử lý" luôn thay vì phải chờ cache hết hạn.
     */
    void invalidateItStaffCache();

    // Count tickets for a specific reporter filtered by status
    long countTicketsByReporterAndStatus(String reporterName, String status);

    // Count all tickets for a specific reporter
    long countTicketsByReporter(String reporterName);

    Page<Ticket> getTicketsForUser(String username, String tab, String search, int page);

    /** Trưởng phòng: ticket của toàn bộ người gửi trong danh sách (cả phòng ban). */
    Page<Ticket> getTicketsForDeptHead(List<String> reporterNames, String tab, String search, int page);

    Map<String, Long> getStatisticsForDeptHead(List<String> reporterNames, String ownIdentity);

    Map<String, Long> getStatisticsForUser(String username);

    /**
     * Danh sách ticket một nhân viên IT được phép thấy: danh mục thuộc nhóm của họ,
     * ticket giao cho họ, hoặc ticket họ tự gửi. Hỗ trợ cả tab và bộ lọc nâng cao.
     * @param identity email/danh tính IT (xem ReporterIdentity)
     * @param groups   tên các nhóm chuyên môn của IT (GROUP_* đã bỏ tiền tố)
     */
    Page<Ticket> getTicketsForIt(String identity, List<String> groups, String tab,
                                 String filterStatus, String filterPriority, String filterCategory,
                                 String filterAssignee, LocalDateTime dateFrom, LocalDateTime dateTo,
                                 String search, int page);

    /** Thống kê (total/unassigned/mine/inProgress/overdue) tính đúng theo phạm vi IT thấy. */
    Map<String, Long> getStatisticsForIt(String identity, List<String> groups);

    /** Danh sách ticket trong phạm vi IT theo bộ lọc, không phân trang (dùng export Excel). */
    List<Ticket> getAllTicketsForIt(String identity, List<String> groups, String status, String priority,
                                    String category, String assignee, LocalDateTime dateFrom,
                                    LocalDateTime dateTo, String search);

    // Lọc đa điều kiện nâng cao (Admin) - phân trang
    Page<Ticket> getTicketsFiltered(String tab, String status, String priority,
                                    String category, String assignee,
                                    LocalDateTime dateFrom, LocalDateTime dateTo,
                                    String search, int page);

    // Lấy tất cả ticket theo bộ lọc - không phân trang (dùng cho export Excel)
    List<Ticket> getAllTicketsFiltered(String status, String priority, String category,
                                      String assignee, LocalDateTime dateFrom,
                                      LocalDateTime dateTo, String search);
}
