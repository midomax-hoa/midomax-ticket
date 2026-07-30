package vn.midomax.helpdesk;

import java.util.List;

public interface WorkReportService {
    List<WorkReport> getAllReports();
    List<WorkReport> getReportsByAssignee(String assignee);
    List<WorkReport> getReportsByProject(String projectName);
    List<WorkReport> getReportsFiltered(String assignee, String projectName, String status);
    List<WorkReport> getVisibleReports(String currentUsername);
    List<WorkReport> getVisibleReportsFiltered(String currentUsername, String assignee, String projectName, String status);
    WorkReport getReportById(Long id);
    WorkReport saveReport(WorkReport report);
    void deleteReport(Long id);
    WorkReport updateProgressAndReport(Long id, Integer progress, String status, String dailyReport, String watchers, String delayReason);
    List<WorkSubTask> getSubTasks(Long workReportId);
    WorkSubTask addSubTask(Long workReportId, String title, String assignee);
    WorkSubTask toggleSubTask(Long subTaskId, Boolean completed);
    void deleteSubTask(Long subTaskId);
    List<WorkComment> getComments(Long workReportId);
    WorkComment addComment(Long workReportId, String author, String content);
    WorkReport updateInline(Long id, String status, String priority, String assignee, String watchers, String dueDateStr, String delayReason);
    List<WorkReport> getChildReports(Long parentId);
    WorkReport createSubReport(Long parentId, String taskTitle, String assignee, String watchers, String status, String dueDateStr);
    WorkReport updateFullReport(Long id, String taskTitle, String projectName, String assignee, String status, Integer progress, String dailyReport, String watchers, String dueDateStr, String delayReason);
    void recalculateParentProgress(Long workReportId);

    /** Người tạo báo cáo chốt hoàn thành: đưa việc cha từ 90% (chờ xác nhận) lên 100%. */
    WorkReport confirmCompletion(Long id, String username);
}
