package vn.midomax.helpdesk;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_comments")
public class WorkComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workReportId; // ID của tác vụ / công việc

    @Column(nullable = false)
    private String author; // Username của người bình luận

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // Nội dung bình luận (có thể chứa @mention)

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkComment() {
    }

    public WorkComment(Long workReportId, String author, String content) {
        this.workReportId = workReportId;
        this.author = author;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getWorkReportId() { return workReportId; }
    public void setWorkReportId(Long workReportId) { this.workReportId = workReportId; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getFormattedCreatedAt() {
        if (createdAt == null) return "";
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
            return createdAt.format(formatter);
        } catch (Exception e) {
            return createdAt.toString();
        }
    }
}
