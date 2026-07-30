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

    @Column(nullable = false)
    private String createdBy; // Username của người tạo báo cáo

    private String watchers; // Danh sách username người theo dõi (cách nhau bởi dấu phẩy)

    @Column(nullable = false)
    private String status; // PLANNING, PROGRESS, COMPLETED

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

    @Column(name = "completed_at")
    private LocalDateTime completedAt; // Thời điểm thực sự đạt 100% / COMPLETED, dùng để chấm SLA

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Việc được xem là xong khi trạng thái COMPLETED hoặc tiến độ đạt 100%. */
    private boolean isDone() {
        return "COMPLETED".equals(status) || (progressPercentage != null && progressPercentage == 100);
    }

    /**
     * Đóng dấu mốc hoàn thành ngay khi việc đạt 100% và xoá mốc nếu bị mở lại.
     * Đặt ở lifecycle callback để mọi đường cập nhật (inline, modal, tự tính từ việc con)
     * đều được chấm mốc giống nhau.
     */
    private void stampCompletion() {
        if (isDone()) {
            if (completedAt == null) completedAt = LocalDateTime.now();
        } else {
            completedAt = null;
        }
    }

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
        if (createdBy == null || createdBy.isEmpty()) createdBy = "system";
        stampCompletion();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        stampCompletion();
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

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getWatchers() { return watchers; }

    /**
     * Giao diện cho gõ "@ten" để tag người theo dõi nên chuỗi gửi lên có thể lẫn ký tự @
     * và khoảng trắng thừa. Chuẩn hoá tại một chỗ để dữ liệu lưu luôn dạng "tin,trung".
     */
    public void setWatchers(String watchers) {
        if (watchers == null || watchers.isBlank()) {
            this.watchers = watchers;
            return;
        }
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (String name : watchers.split(",")) {
            String clean = name.trim().replace("@", "");
            if (!clean.isEmpty()) names.add(clean);
        }
        this.watchers = String.join(",", names);
    }

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

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

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
        boolean done = isDone();
        if (dueDate == null) {
            return done ? "Đúng hạn" : "Chưa kết thúc";
        }
        if (!done) {
            return LocalDateTime.now().isAfter(dueDate) ? "Quá hạn" : "Chưa kết thúc";
        }
        // Đã xong: chấm theo mốc hoàn thành thực tế, xong trước hạn thì luôn là đúng hạn
        // dù sau đó thời gian có trôi qua hạn chót.
        if (completedAt != null) {
            return completedAt.isAfter(dueDate) ? "Trễ hẹn" : "Đúng hạn";
        }
        // Dữ liệu cũ chưa có mốc hoàn thành: suy đoán theo giải trình trễ / thời điểm hiện tại.
        boolean hasDelayReason = delayReason != null && !delayReason.trim().isEmpty();
        return (hasDelayReason || LocalDateTime.now().isAfter(dueDate)) ? "Trễ hẹn" : "Đúng hạn";
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
