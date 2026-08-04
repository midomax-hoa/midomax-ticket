package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
        List<WorkReport> reports = workReportRepository.findAllByOrderByUpdatedAtDesc();
        Map<Long, WorkReport> byId = new HashMap<>();
        for (WorkReport r : reports) {
            byId.put(r.getId(), r);
        }
        for (WorkReport r : reports) {
            r.setParentDueDate(inheritedDueDate(r, byId::get));
        }
        return reports;
    }

    /**
     * Hạn chót do chủ công việc đặt ở nhánh cha gần nhất. Leo lên tới khi gặp việc cha có hạn;
     * việc gốc (không có cha) trả null để dùng hạn của chính nó.
     */
    private LocalDateTime inheritedDueDate(WorkReport report, java.util.function.LongFunction<WorkReport> lookup) {
        WorkReport current = report;
        int guard = 0;
        while (current != null && current.getParentId() != null && current.getParentId() > 0 && guard++ < 50) {
            WorkReport parent = lookup.apply(current.getParentId());
            if (parent == null) return null;
            if (parent.getDueDate() != null) return parent.getDueDate();
            current = parent;
        }
        return null;
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
        WorkReport report = workReportRepository.findById(id).orElse(null);
        if (report != null) {
            report.setParentDueDate(inheritedDueDate(report, pid -> workReportRepository.findById(pid).orElse(null)));
        }
        return report;
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

    /**
     * Chủ báo cáo có toàn quyền tự kết thúc / mở lại việc cha dù việc con chưa xong hết.
     * Đánh dấu lại cờ xác nhận để recalculateParentProgress không ghi đè quyết định đó.
     */
    /** Báo cáo đã được chủ chốt hoàn thành thì khoá lại, không thêm việc con / sub-task nữa. */
    private void assertNotClosed(WorkReport parent) {
        if (parent != null && parent.getOwnerConfirmed()) {
            throw new IllegalStateException("Báo cáo đã được chủ báo cáo chốt hoàn thành nên không thêm việc con được nữa.");
        }
    }

    private void syncOwnerConfirm(WorkReport report) {
        boolean done = "COMPLETED".equalsIgnoreCase(report.getStatus())
                || (report.getProgressPercentage() != null && report.getProgressPercentage() == 100);
        report.setOwnerConfirmed(done);
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
            syncOwnerConfirm(existing);
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
        assertNotClosed(getReportById(workReportId));
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
                // Chủ báo cáo đã chốt kết thúc thì giữ nguyên 100%, không tính lại theo việc con
                // (chủ có quyền đóng sớm; muốn mở lại thì tự đổi trạng thái ở modal cập nhật).
                if (parent.getOwnerConfirmed()) {
                    newProgress = 100;
                } else if (newProgress == 100) {
                    // Việc con xong hết chưa chắc việc cha đã xong: dừng ở 90% chờ chủ xác nhận.
                    newProgress = WorkReport.AWAITING_CONFIRM_PROGRESS;
                }

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
    public WorkReport confirmCompletion(Long id, String username) {
        WorkReport report = getReportById(id);
        if (report == null) {
            throw new IllegalArgumentException("Không tìm thấy báo cáo #" + id);
        }
        String owner = report.getCreatedBy();
        if (owner == null || owner.trim().isEmpty() || username == null || !username.equalsIgnoreCase(owner.trim())) {
            throw new IllegalStateException("Chỉ người tạo báo cáo mới được xác nhận hoàn thành.");
        }
        report.setOwnerConfirmed(true);
        report.setProgressPercentage(100);
        report.setStatus("COMPLETED");
        report.setUpdatedAt(LocalDateTime.now());
        WorkReport saved = workReportRepository.save(report);
        if (saved.getParentId() != null && saved.getParentId() > 0) {
            recalculateParentProgress(saved.getParentId());
        }
        return saved;
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
        syncOwnerConfirm(existing);
        existing.setUpdatedAt(LocalDateTime.now());
        WorkReport saved = workReportRepository.save(existing);
        if (saved.getParentId() != null && saved.getParentId() > 0) {
            recalculateParentProgress(saved.getParentId());
        }
        return saved;
    }

    @Override
    public List<WorkReport> getChildReports(Long parentId) {
        List<WorkReport> children = workReportRepository.findByParentIdOrderByCreatedAtAsc(parentId);
        WorkReport parent = getReportById(parentId);
        LocalDateTime inherited = parent != null
                ? (parent.getDueDate() != null ? parent.getDueDate() : parent.getEffectiveDueDate())
                : null;
        children.forEach(c -> c.setParentDueDate(inherited));
        return children;
    }

    @Override
    public WorkReport createSubReport(Long parentId, String taskTitle, String assignee, String watchers, String status, String dueDateStr) {
        WorkReport parent = getReportById(parentId);
        assertNotClosed(parent);
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
            syncOwnerConfirm(existing);
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
        // Watchers là chuỗi "tinnt,admin" nên phải so từng tên, không dùng contains
        // (tránh "user" khớp lẫn vào "user2"). Bỏ qua hoa/thường vì tên tag do người gõ.
        Pattern watcherPattern = Pattern.compile(
                ".*\\b" + Pattern.quote(currentUsername) + "\\b.*", Pattern.CASE_INSENSITIVE);

        Set<Long> directIds = new LinkedHashSet<>();
        Map<Long, WorkReport> byId = new HashMap<>();
        Map<Long, List<WorkReport>> childrenOf = new HashMap<>();
        for (WorkReport r : allReports) {
            byId.put(r.getId(), r);
            if (r.getParentId() != null && r.getParentId() > 0) {
                childrenOf.computeIfAbsent(r.getParentId(), k -> new ArrayList<>()).add(r);
            }
            boolean mine = (r.getCreatedBy() != null && currentUsername.equalsIgnoreCase(r.getCreatedBy()))
                    || currentUsername.equalsIgnoreCase(r.getAssignee())
                    || (r.getWatchers() != null && watcherPattern.matcher(r.getWatchers()).matches());
            if (!mine) {
                List<WorkSubTask> subtasks = workSubTaskRepository.findByWorkReportIdOrderByIdAsc(r.getId());
                mine = subtasks.stream().anyMatch(st -> currentUsername.equalsIgnoreCase(st.getAssignee()));
            }
            if (mine) {
                directIds.add(r.getId());
            }
        }

        // Việc con được giao/tag cho mình chỉ hiển thị khi nhánh cha của nó cũng nằm trong danh
        // sách (giao diện render theo cây, con không có cha sẽ bị rơi mất). Ngược lại, thấy việc
        // cha thì thấy luôn các việc con của nó — giống cách Admin/IT đang xem.
        Set<Long> visibleIds = new LinkedHashSet<>(directIds);
        for (Long id : directIds) {
            WorkReport cur = byId.get(id);
            int guard = 0;
            while (cur != null && cur.getParentId() != null && cur.getParentId() > 0 && guard++ < 50) {
                visibleIds.add(cur.getParentId());
                cur = byId.get(cur.getParentId());
            }
        }
        // Duyệt xuống từ TOÀN BỘ visibleIds (gồm cả các việc cha vừa thêm ở vòng lặp trên),
        // không chỉ từ directIds. Nếu chỉ đi từ directIds thì người được giao một việc con sẽ
        // thấy việc cha nhưng mất hết các việc con khác cùng cha — trái với quy tắc ngay trên.
        Deque<Long> queue = new ArrayDeque<>(visibleIds);
        while (!queue.isEmpty()) {
            for (WorkReport child : childrenOf.getOrDefault(queue.poll(), Collections.emptyList())) {
                if (visibleIds.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }

        return allReports.stream()
            .filter(r -> visibleIds.contains(r.getId()))
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
