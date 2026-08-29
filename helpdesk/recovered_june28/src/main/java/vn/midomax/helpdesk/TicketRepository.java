package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Count tickets by status
    long countByStatus(String status);

    // Count tickets where assignee is null (unassigned)
    long countByAssigneeIsNull();

    // Count tickets by assignee
    long countByAssignee(String assignee);

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
           "(:isUnassigned IS NULL OR (:isUnassigned = true AND t.assignee IS NULL)) AND " +
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
}
