package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkReportRepository extends JpaRepository<WorkReport, Long> {
    List<WorkReport> findAllByOrderByUpdatedAtDesc();
    List<WorkReport> findByAssigneeOrderByUpdatedAtDesc(String assignee);
    List<WorkReport> findByProjectNameOrderByUpdatedAtDesc(String projectName);
    List<WorkReport> findByStatusOrderByUpdatedAtDesc(String status);
    List<WorkReport> findByAssigneeAndProjectNameOrderByUpdatedAtDesc(String assignee, String projectName);
    List<WorkReport> findByParentIdOrderByCreatedAtAsc(Long parentId);
}
