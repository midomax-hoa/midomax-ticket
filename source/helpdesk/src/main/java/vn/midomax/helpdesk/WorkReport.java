package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_reports")
public class WorkReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String projectName; // Tên dự án / hạng mục lớn

    @Column(nullable = false)
    private String taskTitle; // Tên công việc / tính năng chi tiết

    @Column(nullable = false)
    private String assignee; // Username của IT/Dev

    private String watchers; // Danh sách username người theo dõi (cách nhau bởi dấu phẩy)

    @Column(nullable = false)
    private String status; // PLANNING, PROGRESS, TESTING, COMPLETED

    @Column(nullable = false)
    private Integer progressPercentage = 0; // 0 to 100

    private LocalDateTime startDate;

    private LocalDateTime dueDate;

    @Column(columnDefinition = "TEXT")
    private String dailyReport; // Ghi chú báo cáo tiến độ gần nhất / nhật ký làm việc

    private String priority; // URGENT, HIGH, NORMAL, LOW

    private String estimatedTime; // VD: "4 giờ", "2 ngày"

    private Double loggedHours = 0.0; // Tổng số giờ đã làm

    private String tags; // Danh sách thẻ nhãn (VD: "Backend,Urgent")

    private Boolean isTimerRunning = false; // Trạng thái bộ đếm giờ

    private LocalDateTime timerStartedAt; // Thời điểm bắt đầu đếm giờ

    @Column(name = "parent_id")
    private Long parentId; // ID của công việc cha (nếu là việc con / sub-task)

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public WorkReport() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (progressPercentage == null) progressPercentage = 0;
        if (status == null || status.isEmpty()) status = "PLANNING";
        if (priority == null || priority.isEmpty()) priority = "NORMAL";
        if (loggedHours == null) loggedHours = 0.0;
        if (isTimerRunning == null) isTimerRunning = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getWatchers() { return watchers; }
    public void setWatchers(String watchers) { this.watchers = watchers; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Integer progressPercentage) { this.progressPercentage = progressPercentage; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public String getDailyReport() { return dailyReport; }
    public void setDailyReport(String dailyReport) { this.dailyReport = dailyReport; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }

    public Double getLoggedHours() { return loggedHours; }
    public void setLoggedHours(Double loggedHours) { this.loggedHours = loggedHours; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Boolean getIsTimerRunning() { return isTimerRunning != null && isTimerRunning; }
    public void setIsTimerRunning(Boolean isTimerRunning) { this.isTimerRunning = isTimerRunning; }

    public LocalDateTime getTimerStartedAt() { return timerStartedAt; }
    public void setTimerStartedAt(LocalDateTime timerStartedAt) { this.timerStartedAt = timerStartedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Column(columnDefinition = "TEXT")
    private String delayReason; // Lý do giải trình khi hoàn thành trễ hạn (overdue)

    public String getDelayReason() { return delayReason; }
    public void setDelayReason(String delayReason) { this.delayReason = delayReason; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    @jakarta.persistence.Transient
    public String getSlaStatus() {
        boolean isDone = "COMPLETED".equals(status) || (progressPercentage != null && progressPercentage == 100);
        if (dueDate == null) {
            return isDone ? "Đúng hạn" : "Chưa kết thúc";
        }
        boolean overdue = java.time.LocalDateTime.now().isAfter(dueDate);
        boolean hasDelayReason = delayReason != null && !delayReason.trim().isEmpty();
        if (isDone) {
            if (hasDelayReason || overdue) {
                return "Trễ hẹn";
            } else {
                return "Đúng hạn";
            }
        } else {
            if (overdue) {
                return "Quá hạn";
            } else {
                return "Chưa kết thúc";
            }
        }
    }

    @jakarta.persistence.Transient
    public String getSlaBadgeClass() {
        String sla = getSlaStatus();
        if ("Đúng hạn".equals(sla)) return "bg-success-subtle text-success border border-success-subtle";
        if ("Trễ hẹn".equals(sla)) return "bg-warning-subtle text-dark border border-warning";
        if ("Quá hạn".equals(sla)) return "bg-danger-subtle text-danger border border-danger-subtle";
        if ("Chưa kết thúc".equals(sla)) return "bg-info-subtle text-info border border-info-subtle";
        return "bg-light text-secondary border";
    }
}
