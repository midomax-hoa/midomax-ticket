package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Count tickets by status
    long countByStatus(String status);

    // Find tickets by status
    List<Ticket> findByStatus(String status);

    // Count tickets where assignee is null (unassigned)
    long countByAssigneeIsNull();

    // Count tickets by assignee
    long countByAssignee(String assignee);

    // Count tickets by assignee and status (tải thực tế của một IT)
    long countByAssigneeAndStatus(String assignee, String status);

    // Find tickets by assignee
    List<Ticket> findByAssignee(String assignee);

    // Count tickets with HIGH priority and status is not RESOLVED (overdue / critical)
    long countByPriorityAndStatusNot(String priority, String statusNot);
    // Find tickets by reporter name (user)
    List<Ticket> findByReporterName(String reporterName);
    // Count tickets by reporter name and status
    long countByReporterNameAndStatus(String reporterName, String status);
    // Count tickets by reporter name
    long countByReporterName(String reporterName);

    // Lọc theo tab (all, mine, unassigned, closed/resolved) và từ khóa tìm kiếm
    @Query("SELECT t FROM Ticket t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:assignee IS NULL OR t.assignee = :assignee) AND " +
           "(:isUnassigned IS NULL OR (:isUnassigned = true AND (t.assignee IS NULL OR TRIM(t.assignee) = ''))) AND " +
           "(:search IS NULL OR " +
           "  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.reporterName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(t.id AS string) = :searchId" +
           ") ORDER BY t.createdAt DESC")
    Page<Ticket> filterAndSearchTickets(
            @Param("status") String status,
            @Param("assignee") String assignee,
            @Param("isUnassigned") Boolean isUnassigned,
            @Param("search") String search,
            @Param("searchId") String searchId,
            Pageable pageable
    );

    // Ticket của MỘT DANH SÁCH người gửi (trưởng phòng xem ticket cả phòng mình).
    // reporterName lưu email (365) hoặc username (local) nên truyền cả hai dạng cho mỗi người.
    @Query("SELECT t FROM Ticket t WHERE t.reporterName IN :reporterNames AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:search IS NULL OR " +
           "  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(t.id AS string) = :searchId" +
           ") ORDER BY t.createdAt DESC")
    Page<Ticket> filterAndSearchTicketsForReporters(
            @Param("reporterNames") java.util.List<String> reporterNames,
            @Param("status") String status,
            @Param("search") String search,
            @Param("searchId") String searchId,
            Pageable pageable
    );

    long countByReporterNameIn(java.util.List<String> reporterNames);

    long countByReporterNameInAndStatus(java.util.List<String> reporterNames, String status);

    // Lọc ticket do chính user tạo
    @Query("SELECT t FROM Ticket t WHERE t.reporterName = :reporterName AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:search IS NULL OR " +
           "  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(t.id AS string) = :searchId" +
           ") ORDER BY t.createdAt DESC")
    Page<Ticket> filterAndSearchTicketsForUser(
            @Param("reporterName") String reporterName,
            @Param("status") String status,
            @Param("search") String search,
            @Param("searchId") String searchId,
            Pageable pageable
    );

    // Lọc ticket trong PHẠM VI của một nhân viên IT: danh mục thuộc nhóm chuyên môn
    // của họ (:groups), HOẶC ticket đang giao cho họ, HOẶC ticket do chính họ gửi.
    // Nhờ vậy IT nhóm HELPDESK không thấy ticket danh mục REPORT/SOFTWARE (trừ khi
    // được giao / tự gửi). :hasGroups = 1 khi user có ít nhất một nhóm — dùng cờ số
    // để tránh sinh mệnh đề IN () rỗng khi user chưa được xếp nhóm nào.
    @Query("SELECT t FROM Ticket t WHERE " +
           "((:hasGroups = 1 AND t.category IN :groups) OR t.assignee = :identity OR t.reporterName = :identity) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:filterAssignee IS NULL OR t.assignee = :filterAssignee) AND " +
           "(:mineAssignee IS NULL OR t.assignee = :mineAssignee) AND " +
           "(:isUnassigned IS NULL OR (t.assignee IS NULL OR TRIM(t.assignee) = '')) AND " +
           "(:dateFrom IS NULL OR t.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR t.createdAt <= :dateTo) AND " +
           "(:search IS NULL OR " +
           "  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.reporterName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(t.id AS string) = :searchId" +
           ") ORDER BY t.createdAt DESC")
    Page<Ticket> filterTicketsForIt(
            @Param("identity") String identity,
            @Param("hasGroups") int hasGroups,
            @Param("groups") List<String> groups,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("category") String category,
            @Param("filterAssignee") String filterAssignee,
            @Param("mineAssignee") String mineAssignee,
            @Param("isUnassigned") Boolean isUnassigned,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("search") String search,
            @Param("searchId") String searchId,
            Pageable pageable
    );

    // Toàn bộ ticket trong phạm vi của IT (không phân trang, không lọc) — dùng để
    // tính bộ thống kê (Tổng/Chưa phân công/Đang xử lý/Quá hạn) đúng theo phạm vi họ thấy.
    @Query("SELECT t FROM Ticket t WHERE " +
           "((:hasGroups = 1 AND t.category IN :groups) OR t.assignee = :identity OR t.reporterName = :identity)")
    List<Ticket> findAllInItScope(
            @Param("identity") String identity,
            @Param("hasGroups") int hasGroups,
            @Param("groups") List<String> groups
    );

    // Bản không phân trang của filterTicketsForIt — dùng cho export Excel để IT chỉ
    // xuất được ticket trong phạm vi họ thấy (không rò rỉ ticket nhóm khác).
    @Query("SELECT t FROM Ticket t WHERE " +
           "((:hasGroups = 1 AND t.category IN :groups) OR t.assignee = :identity OR t.reporterName = :identity) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:filterAssignee IS NULL OR t.assignee = :filterAssignee) AND " +
           "(:dateFrom IS NULL OR t.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR t.createdAt <= :dateTo) AND " +
           "(:search IS NULL OR " +
           "  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.reporterName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(t.id AS string) = :searchId" +
           ") ORDER BY t.createdAt DESC")
    List<Ticket> filterTicketsForItAll(
            @Param("identity") String identity,
            @Param("hasGroups") int hasGroups,
            @Param("groups") List<String> groups,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("category") String category,
            @Param("filterAssignee") String filterAssignee,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("search") String search,
            @Param("searchId") String searchId
    );

    // Lọc đa điều kiện nâng cao (cho bộ lọc + export) - ADMIN
    @Query("SELECT t FROM Ticket t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:assignee IS NULL OR t.assignee = :assignee) AND " +
           "(:dateFrom IS NULL OR t.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR t.createdAt <= :dateTo) AND " +
           "(:search IS NULL OR " +
           "  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.reporterName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(t.id AS string) = :searchId" +
           ") ORDER BY t.createdAt DESC")
    Page<Ticket> filterTickets(
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("category") String category,
            @Param("assignee") String assignee,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("search") String search,
            @Param("searchId") String searchId,
            Pageable pageable
    );

    // Lọc đa điều kiện nâng cao - không phân trang (dùng export Excel)
    @Query("SELECT t FROM Ticket t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:assignee IS NULL OR t.assignee = :assignee) AND " +
           "(:dateFrom IS NULL OR t.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR t.createdAt <= :dateTo) AND " +
           "(:search IS NULL OR " +
           "  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.reporterName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.category) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(t.id AS string) = :searchId" +
           ") ORDER BY t.createdAt DESC")
    List<Ticket> filterTicketsAll(
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("category") String category,
            @Param("assignee") String assignee,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("search") String search,
            @Param("searchId") String searchId
    );

    // Find first active/in-progress ticket assigned to someone
    Ticket findFirstByStatusAndAssigneeIsNotNullOrderByIdDesc(String status);
}

