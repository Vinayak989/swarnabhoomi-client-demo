package in.swarnabhoomi.farmlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "support_message", indexes = {
        @Index(name = "ix_support_message_thread", columnList = "thread_id,id"),
        @Index(name = "ix_support_message_unread", columnList = "sender_role,read_at")
})
public class SupportMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long threadId;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false, length = 16)
    private String senderRole;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;

    protected SupportMessage() {}

    public SupportMessage(Long threadId, Long senderUserId, String senderRole, String body) {
        this.threadId = threadId;
        this.senderUserId = senderUserId;
        this.senderRole = senderRole;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getThreadId() { return threadId; }
    public Long getSenderUserId() { return senderUserId; }
    public String getSenderRole() { return senderRole; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
