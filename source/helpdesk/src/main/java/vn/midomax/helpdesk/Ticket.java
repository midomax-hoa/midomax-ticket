package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category; // network, hardware, software, server

    @Column(nullable = false)
    private String reporterName;

    private String reporterDepartment;

    @Column(nullable = false)
    private String priority; // HIGH, MED, LOW

    @Column(nullable = false)
    private String status; // OPEN, PROGRESS, RESOLVED

    private String assignee; // tin, trinh, dinh

    private LocalDateTime createdAt;

    private LocalDateTime slaDeadline;

    private LocalDateTime estimatedCompletionTime; // Thời gian dự kiến hoàn thành

    private LocalDateTime completedAt; // Thời gian thực tế hoàn thành (khi chuyển sang RESOLVED)

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String fixNote; // Ghi chú của IT/Admin về những gì đã fix khi hoàn thành

    private LocalDateTime itCompletedAt; // Thời gian IT hoàn thành xử lý
    private LocalDateTime closedAt; // Thời gian đóng ticket chính thức

    @Column(columnDefinition = "TEXT")
    private String userFeedback; // Phản hồi / ghi chú của người dùng khi còn lỗi hoặc khi xác nhận

    @Column(columnDefinition = "TEXT")
    private String closeReason; // Lý do đóng ticket (User xác nhận, hoặc Tự động đóng sau 3 ngày)

    // Path to uploaded image (optional)
    private String imagePath;

    // Path to uploaded completion image (optional)
    private String completionImagePath;

    private String location; // Hà Nội, HCM
    private String managerApproval = "PENDING"; // PENDING, APPROVED, REJECTED
    private String itApproval = "PENDING"; // PENDING, APPROVED, REJECTED

    public Ticket() {
    }

    public LocalDateTime getItCompletedAt() { return itCompletedAt; }
    public void setItCompletedAt(LocalDateTime itCompletedAt) { this.itCompletedAt = itCompletedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public String getUserFeedback() { return userFeedback; }
    public void setUserFeedback(String userFeedback) { this.userFeedback = userFeedback; }

    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }

    public String getItCompletedAtStr() {
        if (itCompletedAt == null) return "";
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return itCompletedAt.format(formatter);
        } catch (Exception e) {
            return "";
        }
    }

    public String getClosedAtStr() {
        if (closedAt == null) return "";
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return closedAt.format(formatter);
        } catch (Exception e) {
            return "";
        }
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getManagerApproval() { return managerApproval; }
    public void setManagerApproval(String managerApproval) { this.managerApproval = managerApproval; }

    public String getItApproval() { return itApproval; }
    public void setItApproval(String itApproval) { this.itApproval = itApproval; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    
    public String getReporterDepartment() { return reporterDepartment; }
    public void setReporterDepartment(String reporterDepartment) { this.reporterDepartment = reporterDepartment; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getSlaDeadline() { return slaDeadline; }
    public void setSlaDeadline(LocalDateTime slaDeadline) { this.slaDeadline = slaDeadline; }
    
    public LocalDateTime getEstimatedCompletionTime() { return estimatedCompletionTime; }
    public void setEstimatedCompletionTime(LocalDateTime estimatedCompletionTime) {
        this.estimatedCompletionTime = estimatedCompletionTime;
        if (estimatedCompletionTime != null) {
            this.slaDeadline = estimatedCompletionTime;
        }
    }

    public LocalDateTime getEffectiveSlaDeadline() {
        if (estimatedCompletionTime != null) {
            return estimatedCompletionTime;
        }
        if (slaDeadline != null) {
            return slaDeadline;
        }
        if (createdAt != null) {
            if ("HIGH".equalsIgnoreCase(priority)) {
                return createdAt.plusHours(3);
            } else if ("MED".equalsIgnoreCase(priority)) {
                return createdAt.plusHours(8);
            }
            return createdAt.plusDays(3);
        }
        return LocalDateTime.now().plusDays(3);
    }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getCompletedAtStr() {
        if (completedAt == null) return "";
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return completedAt.format(formatter);
        } catch (Exception e) {
            return "";
        }
    }

    public String getEstimatedCompletionTimeStr() {
        if (estimatedCompletionTime == null) return "";
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return estimatedCompletionTime.format(formatter);
        } catch (Exception e) {
            return "";
        }
    }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getFixNote() { return fixNote; }
    public void setFixNote(String fixNote) { this.fixNote = fixNote; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getCompletionImagePath() { return completionImagePath; }
    public void setCompletionImagePath(String completionImagePath) { this.completionImagePath = completionImagePath; }

    // Transient getter to get formatted ticket code, e.g. T047
    public String getTicketCode() {
        if (id == null) {
            return "T-NEW";
        }
        return "T" + String.format("%03d", id);
    }
}
