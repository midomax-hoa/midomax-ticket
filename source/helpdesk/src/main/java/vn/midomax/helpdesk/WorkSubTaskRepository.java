package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkSubTaskRepository extends JpaRepository<WorkSubTask, Long> {
    List<WorkSubTask> findByWorkReportIdOrderByIdAsc(Long workReportId);
    void deleteByWorkReportId(Long workReportId);
}
