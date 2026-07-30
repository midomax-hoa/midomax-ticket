package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
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
        WorkReport saved = workReportRepository.save(report);
        if (saved.getParentId() != null && saved.getParentId() > 0) {
            recalculateParentProgress(saved.getParentId());
        }
        return saved;
    }

    @Override
    public void deleteReport(Long id) {
        System.out.println(">>> [DELETE SERVICE] Bắt đầu xóa ID: " + id);
        WorkReport reportToDelete = getReportById(id);
        Long parentId = reportToDelete != null ? reportToDelete.getParentId() : null;

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

        if (parentId != null && parentId > 0) {
            recalculateParentProgress(parentId);
        }
    }

    @Override
    public WorkReport updateProgressAndReport(Long id, Integer progress, String status, String dailyReport, String watchers, String delayReason) {
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
                if ("COMPLETED".equalsIgnoreCase(status) && (progress == null || progress < 100)) {
                    existing.setProgressPercentage(100);
                }
            }
            if (dailyReport != null) {
                existing.setDailyReport(dailyReport);
            }
            if (watchers != null) {
                existing.setWatchers(watchers);
            }
            if (delayReason != null && !delayReason.trim().isEmpty()) {
                existing.setDelayReason(delayReason.trim());
            }
            existing.setUpdatedAt(LocalDateTime.now());
            WorkReport saved = workReportRepository.save(existing);
            if (saved.getParentId() != null && saved.getParentId() > 0) {
                recalculateParentProgress(saved.getParentId());
            }
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

    @Override
    public void recalculateParentProgress(Long workReportId) {
        if (workReportId == null || workReportId <= 0) return;

        List<WorkSubTask> subtasks = workSubTaskRepository.findByWorkReportIdOrderByIdAsc(workReportId);
        List<WorkReport> childReports = workReportRepository.findByParentIdOrderByCreatedAtAsc(workReportId);

        int totalCount = subtasks.size() + childReports.size();
        if (totalCount > 0) {
            double totalProgressSum = 0.0;

            for (WorkSubTask st : subtasks) {
                if (Boolean.TRUE.equals(st.getCompleted())) {
                    totalProgressSum += 100.0;
                }
            }

            for (WorkReport cr : childReports) {
                int crProgress = cr.getProgressPercentage() != null ? cr.getProgressPercentage() : 0;
                if ("COMPLETED".equalsIgnoreCase(cr.getStatus())) {
                    crProgress = 100;
                }
                totalProgressSum += crProgress;
            }

            int newProgress = (int) Math.round(totalProgressSum / totalCount);

            WorkReport parent = workReportRepository.findById(workReportId).orElse(null);
            if (parent != null) {
                parent.setProgressPercentage(newProgress);
                if (newProgress == 100) {
                    parent.setStatus("COMPLETED");
                } else if (newProgress > 0) {
                    if ("PLANNING".equalsIgnoreCase(parent.getStatus()) || "COMPLETED".equalsIgnoreCase(parent.getStatus())) {
                        parent.setStatus("PROGRESS");
                    }
                } else if (newProgress == 0) {
                    if ("COMPLETED".equalsIgnoreCase(parent.getStatus())) {
                        parent.setStatus("PLANNING");
                    }
                }
                parent.setUpdatedAt(LocalDateTime.now());
                workReportRepository.save(parent);

                if (parent.getParentId() != null && parent.getParentId() > 0) {
                    recalculateParentProgress(parent.getParentId());
                }
            }
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
    public WorkReport updateInline(Long id, String status, String priority, String assignee, String watchers, String dueDateStr, String delayReason) {
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
        if (delayReason != null && !delayReason.trim().isEmpty()) {
            existing.setDelayReason(delayReason.trim());
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
        if (saved.getParentId() != null && saved.getParentId() > 0) {
            recalculateParentProgress(saved.getParentId());
        }
        return saved;
    }

    @Override
    public List<WorkReport> getChildReports(Long parentId) {
        return workReportRepository.findByParentIdOrderByCreatedAtAsc(parentId);
    }

    @Override
    public WorkReport createSubReport(Long parentId, String taskTitle, String assignee, String watchers, String status, String dueDateStr) {
        WorkReport parent = getReportById(parentId);
        WorkReport child = new WorkReport();
        child.setParentId(parentId);
        child.setTaskTitle(taskTitle != null ? taskTitle.trim() : "Việc con mới");
        child.setAssignee((assignee != null && !assignee.trim().isEmpty()) ? assignee.trim() : (parent != null ? parent.getAssignee() : "tin"));
        // Chọn nhiều người: người đầu là phụ trách chính, các tên còn lại thành watchers.
        if (watchers != null && !watchers.trim().isEmpty()) {
            child.setWatchers(watchers);
        }
        child.setProjectName(parent != null ? parent.getProjectName() : "Dự án chung");
        child.setStatus((status != null && !status.trim().isEmpty()) ? status.trim() : (parent != null && parent.getStatus() != null ? parent.getStatus() : "PLANNING"));
        child.setProgressPercentage(0);
        if ("COMPLETED".equalsIgnoreCase(child.getStatus())) {
            child.setProgressPercentage(100);
        }

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
        if (parentId != null && parentId > 0) {
            recalculateParentProgress(parentId);
        }
        return saved;
    }

    @Override
    public WorkReport updateFullReport(Long id, String taskTitle, String projectName, String assignee, String status, Integer progress, String dailyReport, String watchers, String dueDateStr, String delayReason) {
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
                if ("COMPLETED".equalsIgnoreCase(status) && (progress == null || progress < 100)) {
                    existing.setProgressPercentage(100);
                }
            }
            if (dailyReport != null) {
                existing.setDailyReport(dailyReport.trim());
            }
            if (watchers != null) {
                existing.setWatchers(watchers.trim());
            }
            if (delayReason != null && !delayReason.trim().isEmpty()) {
                existing.setDelayReason(delayReason.trim());
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
            if (saved.getParentId() != null && saved.getParentId() > 0) {
                recalculateParentProgress(saved.getParentId());
            }
            return saved;
        }
        return null;
    }

    @Override
    public List<WorkReport> getVisibleReports(String currentUsername) {
        if (currentUsername == null || currentUsername.isEmpty()) {
            return new ArrayList<>();
        }

        List<WorkReport> allReports = getAllReports();
        String userPattern = ".*\\b" + Pattern.quote(currentUsername) + "\\b.*";

        return allReports.stream()
            .filter(r -> {
                if (r.getCreatedBy() != null && currentUsername.equalsIgnoreCase(r.getCreatedBy())) return true;
                if (currentUsername.equalsIgnoreCase(r.getAssignee())) return true;
                if (r.getWatchers() != null && r.getWatchers().matches(userPattern)) return true;
                List<WorkSubTask> subtasks = workSubTaskRepository.findByWorkReportIdOrderByIdAsc(r.getId());
                return subtasks.stream().anyMatch(st -> currentUsername.equalsIgnoreCase(st.getAssignee()));
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<WorkReport> getVisibleReportsFiltered(String currentUsername, String assignee, String projectName, String status) {
        List<WorkReport> visible = getVisibleReports(currentUsername);

        if (assignee != null && !assignee.isEmpty() && !assignee.equals("ALL")) {
            visible = visible.stream()
                .filter(r -> assignee.equalsIgnoreCase(r.getAssignee()))
                .collect(Collectors.toList());
        }
        if (projectName != null && !projectName.isEmpty() && !projectName.equals("ALL")) {
            visible = visible.stream()
                .filter(r -> projectName.equalsIgnoreCase(r.getProjectName()))
                .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty() && !status.equals("ALL")) {
            visible = visible.stream()
                .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
        }

        return visible;
    }
}
