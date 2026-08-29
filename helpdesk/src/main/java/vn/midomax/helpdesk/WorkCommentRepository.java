package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkCommentRepository extends JpaRepository<WorkComment, Long> {
    List<WorkComment> findByWorkReportIdOrderByCreatedAtAsc(Long workReportId);
    List<WorkComment> findByWorkReportIdOrderByCreatedAtDesc(Long workReportId);
    void deleteByWorkReportId(Long workReportId);
}
