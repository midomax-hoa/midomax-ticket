package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkReportServiceImpl implements WorkReportService {

    @Autowired
    private WorkReportRepository workReportRepository;

    @Autowired
    private WorkSubTaskRepository workSubTaskRepository;

    @Autowired
    private WorkCommentRepository workCommentRepository;

    @Override
    public List<WorkReport> getAllReports() {
        return workReportRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Override
    public List<WorkReport> getReportsByAssignee(String assignee) {
        if (assignee == null || assignee.isEmpty()) {
            return getAllReports();
        }
        return workReportRepository.findByAssigneeOrderByUpdatedAtDesc(assignee);
    }

    @Override
    public List<WorkReport> getReportsByProject(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return getAllReports();
        }
        return workReportRepository.findByProjectNameOrderByUpdatedAtDesc(projectName);
    }

    @Override
    public List<WorkReport> getReportsFiltered(String assignee, String projectName, String status) {
        List<WorkReport> list = getAllReports();
        if (assignee != null && !assignee.isEmpty() && !assignee.equals("ALL")) {
            list = list.stream().filter(r -> assignee.equalsIgnoreCase(r.getAssignee())).collect(Collectors.toList());
        }
        if (projectName != null && !projectName.isEmpty() && !projectName.equals("ALL")) {
            list = list.stream().filter(r -> projectName.equalsIgnoreCase(r.getProjectName())).collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty() && !status.equals("ALL")) {
            list = list.stream().filter(r -> status.equalsIgnoreCase(r.getStatus())).collect(Collectors.toList());
        }
        return list;
    }

    @Override
    public WorkReport getReportById(Long id) {
        return workReportRepository.findById(id).orElse(null);
    }

    @Override
    public WorkReport saveReport(WorkReport report) {
        if (report.getCreatedAt() == null) {
            report.setCreatedAt(LocalDateTime.now());
        }
        report.setUpdatedAt(LocalDateTime.now());
        if (report.getProgressPercentage() == null) {
            report.setProgressPercentage(0);
        }
        if (report.getStatus() == null || report.getStatus().isEmpty()) {
            report.setStatus("PLANNING");
        }
        if (report.getParentId() != null && report.getParentId() <= 0) {
            report.setParentId(null);
        }
        return workReportRepository.save(report);
    }

    @Override
    public void deleteReport(Long id) {
        System.out.println(">>> [DELETE SERVICE] Bắt đầu xóa ID: " + id);
        // Nhớ cha trước khi xóa để tính lại tỷ lệ cho cha sau khi con biến mất
        WorkReport toDelete = getReportById(id);
        Long grandParentId = (toDelete != null) ? toDelete.getParentId() : null;
        List<WorkReport> children = workReportRepository.findByParentIdOrderByCreatedAtAsc(id);
        System.out.println(">>> [DELETE SERVICE] Tìm thấy số lượng con: " + children.size());
        for (WorkReport child : children) {
            System.out.println(">>> [DELETE SERVICE] Xóa việc con ID: " + child.getId());
            workCommentRepository.deleteByWorkReportId(child.getId());
            workSubTaskRepository.deleteByWorkReportId(child.getId());
            workReportRepository.deleteById(child.getId());
        }
        System.out.println(">>> [DELETE SERVICE] Xóa các comment/subtask/report của ID: " + id);
        workCommentRepository.deleteByWorkReportId(id);
        workSubTaskRepository.deleteByWorkReportId(id);
        workReportRepository.deleteById(id);
        System.out.println(">>> [DELETE SERVICE] Hoàn tất xóa ID: " + id);
        if (grandParentId != null) {
            syncHierarchy(getReportById(grandParentId));
        }
    }

    @Override
    public WorkReport updateProgressAndReport(Long id, Integer progress, String status, String dailyReport, String watchers) {
        WorkReport existing = getReportById(id);
        if (existing != null) {
            if (progress != null) {
                existing.setProgressPercentage(progress);
                if (progress == 100 && (status == null || !status.equals("COMPLETED"))) {
                    status = "COMPLETED";
                }
            }
            if (status != null && !status.isEmpty()) {
                existing.setStatus(status);
            }
            if (dailyReport != null) {
                existing.setDailyReport(dailyReport);
            }
            if (watchers != null) {
                existing.setWatchers(watchers);
            }
            existing.setUpdatedAt(LocalDateTime.now());
            WorkReport saved = workReportRepository.save(existing);
            syncHierarchy(saved);   // sửa con → cha tự tính lại; sửa cha có con → khoá theo con
            return saved;
        }
        return null;
    }

    @Override
    public List<WorkSubTask> getSubTasks(Long workReportId) {
        return workSubTaskRepository.findByWorkReportIdOrderByIdAsc(workReportId);
    }

    @Override
    public WorkSubTask addSubTask(Long workReportId, String title, String assignee) {
        WorkSubTask st = new WorkSubTask();
        st.setWorkReportId(workReportId);
        st.setTitle(title);
        st.setAssignee(assignee);
        st.setCompleted(false);
        st.setCreatedAt(LocalDateTime.now());
        st.setUpdatedAt(LocalDateTime.now());
        WorkSubTask saved = workSubTaskRepository.save(st);
        recalculateParentProgress(workReportId);
        return saved;
    }

    @Override
    public WorkSubTask toggleSubTask(Long subTaskId, Boolean completed) {
        WorkSubTask st = workSubTaskRepository.findById(subTaskId).orElse(null);
        if (st != null) {
            st.setCompleted(completed);
            st.setUpdatedAt(LocalDateTime.now());
            workSubTaskRepository.save(st);
            recalculateParentProgress(st.getWorkReportId());
        }
        return st;
    }

    @Override
    public void deleteSubTask(Long subTaskId) {
        WorkSubTask st = workSubTaskRepository.findById(subTaskId).orElse(null);
        if (st != null) {
            Long parentId = st.getWorkReportId();
            workSubTaskRepository.deleteById(subTaskId);
            recalculateParentProgress(parentId);
        }
    }

    private void recalculateParentProgress(Long workReportId) {
        List<WorkSubTask> subtasks = workSubTaskRepository.findByWorkReportIdOrderByIdAsc(workReportId);
        if (!subtasks.isEmpty()) {
            long completedCount = subtasks.stream().filter(s -> Boolean.TRUE.equals(s.getCompleted())).count();
            int newProgress = (int) Math.round(((double) completedCount / subtasks.size()) * 100);
            WorkReport parent = workReportRepository.findById(workReportId).orElse(null);
            if (parent != null) {
                parent.setProgressPercentage(newProgress);
                if (newProgress == 100) {
                    parent.setStatus("COMPLETED");
                } else if (newProgress > 0 && "PLANNING".equals(parent.getStatus())) {
                    parent.setStatus("PROGRESS");
                }
                parent.setUpdatedAt(LocalDateTime.now());
                workReportRepository.save(parent);
            }
        }
    }

    /**
     * Tính lại tiến độ + trạng thái của một việc CHA từ các việc CON thật
     * (WorkReport có parentId = id này). Không có con thì giữ nguyên (việc lá
     * nhập tay bình thường).
     *   - Tiến độ cha = TRUNG BÌNH % của tất cả con (làm tròn). Tất cả con 100%
     *     thì cha = 100%; có con 90% thì cha = tổng/tỷ lệ tương ứng.
     *   - Trạng thái tự suy ra: con xong hết → COMPLETED; có con đang chạy →
     *     PROGRESS; con chưa động gì → PLANNING.
     */
    private void recalcFromChildren(Long id) {
        List<WorkReport> children = workReportRepository.findByParentIdOrderByCreatedAtAsc(id);
        if (children.isEmpty()) return;   // việc lá: không đụng tới

        int sum = 0, allDone = 0, anyStarted = 0;
        for (WorkReport c : children) {
            int p = (c.getProgressPercentage() == null) ? 0 : c.getProgressPercentage();
            if (p < 0) p = 0; else if (p > 100) p = 100;
            sum += p;
            if (p >= 100) allDone++;
            if (p > 0 || (c.getStatus() != null && !"PLANNING".equalsIgnoreCase(c.getStatus()))) anyStarted++;
        }
        int avg = (int) Math.round((double) sum / children.size());

        WorkReport parent = workReportRepository.findById(id).orElse(null);
        if (parent == null) return;
        if (allDone == children.size()) {
            parent.setProgressPercentage(100);
            parent.setStatus("COMPLETED");
        } else {
            parent.setProgressPercentage(avg);
            parent.setStatus(anyStarted > 0 ? "PROGRESS" : "PLANNING");
        }
        parent.setUpdatedAt(LocalDateTime.now());
        workReportRepository.save(parent);
    }

    /**
     * Đồng bộ cả cây: tính lại chính việc này từ con (nếu có con → khoá giá trị
     * theo con), rồi lan LÊN cha, ông... để mọi tầng đều đúng tỷ lệ.
     */
    private void syncHierarchy(WorkReport report) {
        if (report == null) return;
        recalcFromChildren(report.getId());   // nếu nó là cha → cập nhật theo con
        Long parentId = report.getParentId();
        if (parentId != null) {
            syncHierarchy(workReportRepository.findById(parentId).orElse(null));
        }
    }

    @Override
    public List<WorkComment> getComments(Long workReportId) {
        return workCommentRepository.findByWorkReportIdOrderByCreatedAtAsc(workReportId);
    }

    @Override
    public WorkComment addComment(Long workReportId, String author, String content) {
        WorkComment c = new WorkComment(workReportId, author, content);
        return workCommentRepository.save(c);
    }

    @Override
    public WorkReport updateInline(Long id, String status, String priority, String assignee, String watchers, String dueDateStr) {
        WorkReport existing = getReportById(id);
        if (existing == null) return null;

        if (status != null && !status.isEmpty()) {
            existing.setStatus(status.toUpperCase());
            if ("COMPLETED".equalsIgnoreCase(status)) {
                existing.setProgressPercentage(100);
            }
        }
        if (priority != null && !priority.isEmpty()) {
            existing.setPriority(priority.toUpperCase());
        }
        if (assignee != null) {
            existing.setAssignee(assignee.trim());
        }
        if (watchers != null) {
            existing.setWatchers(watchers.trim());
        }
        if (dueDateStr != null) {
            if (dueDateStr.trim().isEmpty()) {
                existing.setDueDate(null);
            } else {
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    existing.setDueDate(java.time.LocalDateTime.parse(dueDateStr.trim(), formatter));
                } catch (Exception e) {
                    // Ignore format errors
                }
            }
        }
        existing.setUpdatedAt(LocalDateTime.now());
        WorkReport saved = workReportRepository.save(existing);
        syncHierarchy(saved);
        return saved;
    }

    @Override
    public List<WorkReport> getChildReports(Long parentId) {
        return workReportRepository.findByParentIdOrderByCreatedAtAsc(parentId);
    }

    @Override
    public WorkReport createSubReport(Long parentId, String taskTitle, String assignee, String status, String dueDateStr) {
        WorkReport parent = getReportById(parentId);
        WorkReport child = new WorkReport();
        child.setParentId(parentId);
        child.setTaskTitle(taskTitle != null ? taskTitle.trim() : "Việc con mới");
        child.setAssignee((assignee != null && !assignee.trim().isEmpty()) ? assignee.trim() : (parent != null ? parent.getAssignee() : "tin"));
        child.setProjectName(parent != null ? parent.getProjectName() : "Dự án chung");
        child.setStatus((status != null && !status.trim().isEmpty()) ? status.trim() : (parent != null && parent.getStatus() != null ? parent.getStatus() : "PLANNING"));
        child.setProgressPercentage(0);

        if (dueDateStr != null && !dueDateStr.trim().isEmpty()) {
            try {
                if (dueDateStr.contains(" ")) {
                    child.setDueDate(LocalDateTime.parse(dueDateStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                } else {
                    child.setDueDate(LocalDate.parse(dueDateStr.trim()).atStartOfDay());
                }
            } catch (Exception e) {
                // Ignore or fallback
            }
        }

        child.setCreatedAt(LocalDateTime.now());
        child.setUpdatedAt(LocalDateTime.now());
        WorkReport saved = workReportRepository.save(child);
        // Có thêm con mới (0%) → cha tính lại ngay để tỷ lệ đúng
        syncHierarchy(getReportById(parentId));
        return saved;
    }

    @Override
    public WorkReport updateFullReport(Long id, String taskTitle, String projectName, String assignee, String status, Integer progress, String dailyReport, String watchers, String dueDateStr) {
        WorkReport existing = getReportById(id);
        if (existing != null) {
            if (taskTitle != null && !taskTitle.trim().isEmpty()) {
                existing.setTaskTitle(taskTitle.trim());
            }
            if (projectName != null && !projectName.trim().isEmpty()) {
                existing.setProjectName(projectName.trim());
            }
            if (assignee != null && !assignee.trim().isEmpty()) {
                existing.setAssignee(assignee.trim().toLowerCase());
            }
            if (progress != null) {
                existing.setProgressPercentage(progress);
                if (progress == 100 && (status == null || !status.equals("COMPLETED"))) {
                    status = "COMPLETED";
                }
            }
            if (status != null && !status.trim().isEmpty()) {
                existing.setStatus(status.trim().toUpperCase());
            }
            if (dailyReport != null) {
                existing.setDailyReport(dailyReport.trim());
            }
            if (watchers != null) {
                existing.setWatchers(watchers.trim());
            }
            if (dueDateStr != null) {
                if (dueDateStr.trim().isEmpty()) {
                    existing.setDueDate(null);
                } else {
                    try {
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                        existing.setDueDate(java.time.LocalDateTime.parse(dueDateStr.trim(), formatter));
                    } catch (Exception e) {
                        // ignore parse error
                    }
                }
            }
            existing.setUpdatedAt(LocalDateTime.now());
            WorkReport saved = workReportRepository.save(existing);
            syncHierarchy(saved);
            return saved;
        }
        return null;
    }
}
