package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "marketplace_message_report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "reporter_user_id"}))
public class MarketplaceMessageReport {
    public enum Status { PENDING, RESOLVED, DISMISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = Status.PENDING;
    }

    public Long getId() { return id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long value) { messageId = value; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long value) { requestId = value; }
    public Long getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(Long value) { reporterUserId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String value) { reviewNote = value; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long value) { reviewedBy = value; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant value) { reviewedAt = value; }
    public Instant getCreatedAt() { return createdAt; }
}
